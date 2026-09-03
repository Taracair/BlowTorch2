package com.resurrection.blowtorch2.lib.chat;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

import android.content.Context;

import com.resurrection.blowtorch2.lib.service.StellarService;
import com.resurrection.blowtorch2.lib.util.AtomicFiles;
import com.resurrection.blowtorch2.lib.util.BlowTorchLogger;

/**
 * Per-world chat inbox JSON beside the profile. {@code static} cache exists in
 * both processes; writers take a shared file lock, reload, mutate, persist.
 * {@link #attach} is service-only so notify stays there.
 */
public final class ChatStore {

	public static final int[] MINE_COLOR_PRESETS = new int[] {
			0xFF1B6B66, 0xFF1A4A7A, 0xFF7A5A14, 0xFF6A1A4A
	};

	static final int MAX_MESSAGES = ChatInbox.MAX_MESSAGES;

	/**
	 * Options {@code chat_max_messages}: 0 means no practical limit
	 * ({@link ChatInbox#HARD_MAX_MESSAGES}). Missing/unparseable is 4000.
	 */
	public static int coerceMaxMessages(Object value) {
		return ChatInbox.coerceMaxMessages(value);
	}

	private static final Pattern NON_WORD = Pattern.compile("\\W");
	private static final Object CACHE_LOCK = new Object();
	private static final HashMap<String, ChatStore> CACHE = new HashMap<String, ChatStore>();

	private static volatile StellarService attached;

	private final Object lock = new Object();
	private final String display;
	private final String fileName;
	private Context app;
	private ChatInbox inbox;
	private long loadedMtime;
	private int maxMessages = ChatInbox.DEFAULT_MAX_MESSAGES;

	/**
	 * Remember the service that can broadcast {@code chatInboxUpdated}.
	 * One line from {@code StellarService.onCreate}.
	 */
	public static void attach(StellarService service) {
		attached = service;
	}

	/**
	 * Inbox for this world, creating or loading {@code display.chat.json}.
	 *
	 * @param ctx any context; the application context is kept
	 * @param display world display name, same string as the profile
	 */
	public static ChatStore forWorld(Context ctx, String display) {
		String name = fileNameForDisplay(display);
		synchronized (CACHE_LOCK) {
			ChatStore existing = CACHE.get(name);
			if (existing == null) {
				existing = new ChatStore(ctx, display == null ? "" : display, name);
				CACHE.put(name, existing);
			} else if (ctx != null) {
				existing.ensureContext(ctx);
			}
			return existing;
		}
	}

	/**
	 * JVM tests: no files, no notify.
	 */
	ChatStore(ChatInbox inbox) {
		this.inbox = inbox == null ? new ChatInbox() : inbox;
		this.app = null;
		this.display = "";
		this.fileName = "";
		this.loadedMtime = 0L;
	}

	private ChatStore(Context ctx, String display, String fileName) {
		this.display = display == null ? "" : display;
		this.fileName = fileName;
		this.app = applicationContext(ctx);
		this.inbox = loadInbox(this.app, fileName);
		this.loadedMtime = fileMtime(this.app, fileName);
	}

	/**
	 * Copy a line into this world's inbox and persist.
	 *
	 * <p>Stamps {@code System.currentTimeMillis()}. Dual-display: this is a
	 * copy; callers must not gag the MUD line.
	 */
	public void append(String threadId, String title, String body) {
		append(threadId, title, body, null);
	}

	/**
	 * {@code seedTemplateIfEmpty} is applied in the same lock as the append,
	 * and only if the thread has no template yet. Trigger {@code $n} should
	 * already be substituted; {@code $text} is left as written.
	 */
	public void append(String threadId, String title, String body,
			String seedTemplateIfEmpty) {
		append(threadId, title, body, seedTemplateIfEmpty, false);
	}

	/**
	 * Trigger copy. {@code mine} paints an own-bubble. A configured name
	 * needle can also mark the line even when {@code mine} is false.
	 */
	public void append(String threadId, String title, String body,
			String seedTemplateIfEmpty, boolean mine) {
		appendAt(threadId, title, body, System.currentTimeMillis(),
				seedTemplateIfEmpty, mine, !mine);
	}

	/** Typed Send: own bubble immediately, not an unread badge. */
	public void appendOutgoing(String threadId, String title, String body) {
		appendAt(threadId, title, body, System.currentTimeMillis(),
				null, true, false);
	}

	void appendAt(String threadId, String title, String body, long whenMs) {
		appendAt(threadId, title, body, whenMs, null, false, true);
	}

	void appendAt(String threadId, String title, String body, long whenMs,
			String seedTemplateIfEmpty) {
		appendAt(threadId, title, body, whenMs, seedTemplateIfEmpty, false, true);
	}

	void appendAt(String threadId, String title, String body, long whenMs,
			String seedTemplateIfEmpty, boolean mine, boolean countUnread) {
		final boolean[] countedUnread = new boolean[] { false };
		final int[] unreadAfter = new int[1];
		final String[] resolvedTitle = new String[] { title };
		boolean wrote;
		synchronized (lock) {
			refreshMaxFromServiceLocked();
			wrote = mutateUnderFileLock(() -> {
				if (attached == null) {
					maxMessages = inbox.maxMessages();
				}
				inbox.setMaxMessages(maxMessages);
				if (seedTemplateIfEmpty != null && seedTemplateIfEmpty.length() > 0) {
					String existing = inbox.replyTemplate(threadId);
					if (existing == null || existing.length() == 0) {
						inbox.setReplyTemplate(threadId, seedTemplateIfEmpty);
					}
				}
				boolean isMine = mine || inbox.bodyLooksMine(threadId, body);
				if (inbox.absorbRecentMine(threadId, body, whenMs, isMine)) {
					return true;
				}
				boolean count = countUnread && !isMine;
				inbox.append(threadId, title, body, whenMs, isMine, count);
				countedUnread[0] = count;
				unreadAfter[0] = inbox.unreadCount(threadId);
				resolvedTitle[0] = inbox.threadTitle(threadId);
				return true;
			});
		}
		if (wrote) {
			notifyInboxUpdated();
			if (countedUnread[0]) {
				notifyUnreadAppended(threadId, resolvedTitle[0], unreadAfter[0]);
			}
		}
	}

	/**
	 * Live cap from Options. {@code 0} is no practical limit. Persist when
	 * the cap (or pruned size) actually changed — raising 4000 to 0 must
	 * write {@code maxMessages} so a UI-process Send does not re-apply 4000.
	 * Unchanged 4000 on world load must not rewrite the whole inbox.
	 */
	public void setMaxMessages(int max) {
		synchronized (lock) {
			maxMessages = coerceMaxMessages(Integer.valueOf(max));
			mutateUnderFileLock(() -> {
				int previousCap = inbox.maxMessages();
				int before = inbox.messageCount();
				inbox.setMaxMessages(maxMessages);
				inbox.capOldest();
				return previousCap != maxMessages
						|| inbox.messageCount() < before;
			});
		}
	}

	/**
	 * Service process: Options is the source of truth. UI process has no
	 * {@link #attach}; the JSON the service last wrote carries the cap
	 * ({@code maxMessages}). A missing key on old files is HARD_MAX so a
	 * Send from the drawer does not re-apply the default 4000.
	 */
	private void refreshMaxFromServiceLocked() {
		StellarService svc = attached;
		if (svc == null || display.length() == 0) {
			return;
		}
		maxMessages = svc.chatMaxMessages(display);
	}

	/**
	 * Leftover: world-level Me is unused. Prefer {@link #mineNeedle(String)}.
	 */
	public String mineNeedle() {
		return "";
	}

	public String mineNeedle(String threadId) {
		synchronized (lock) {
			reloadIfNewerLocked();
			return inbox.mineNeedle(threadId);
		}
	}

	/**
	 * Leftover unused setter: world-level Me is not the live setting.
	 * Prefer {@link #setMineNeedle(String, String)}.
	 */
	public void setMineNeedle(String needle) {
	}

	public void setMineNeedle(String threadId, String needle) {
		synchronized (lock) {
			mutateUnderFileLock(() -> {
				inbox.setMineNeedle(threadId, needle);
				return true;
			});
		}
	}

	/** One form per line in the ⚙ editor; stored as {@code ;} separated. */
	public static String mineNeedleEditorText(String stored) {
		return ChatInbox.mineNeedleEditorText(stored);
	}

	public static String canonicalizeMineNeedle(String typed) {
		return ChatInbox.canonicalizeMineNeedle(typed);
	}

	public int totalUnread() {
		synchronized (lock) {
			reloadIfNewerLocked();
			return inbox.totalUnread();
		}
	}

	public int mineColorArgb() {
		synchronized (lock) {
			reloadIfNewerLocked();
			return inbox.mineColor();
		}
	}

	public void setMineColorArgb(int argb) {
		synchronized (lock) {
			mutateUnderFileLock(() -> {
				inbox.setMineColor(argb);
				return true;
			});
		}
	}

	public int mineColorArgb(String threadId) {
		synchronized (lock) {
			reloadIfNewerLocked();
			return inbox.mineColor(threadId);
		}
	}

	public void setMineColorArgb(String threadId, int argb) {
		synchronized (lock) {
			mutateUnderFileLock(() -> {
				inbox.setMineColor(threadId, argb);
				return true;
			});
		}
	}

	public int otherColorArgb() {
		synchronized (lock) {
			reloadIfNewerLocked();
			return inbox.otherColor();
		}
	}

	public List<ChatThreadSummary> listThreads() {
		synchronized (lock) {
			reloadIfNewerLocked();
			return inbox.listThreads();
		}
	}

	/** Live unread for one thread; 0 if unknown. Reloads if the file is newer. */
	public int unreadCount(String threadId) {
		synchronized (lock) {
			reloadIfNewerLocked();
			return inbox.unreadCount(threadId);
		}
	}

	public String threadTitle(String threadId) {
		synchronized (lock) {
			reloadIfNewerLocked();
			String title = inbox.threadTitle(threadId);
			return title == null ? "" : title;
		}
	}

	public List<ChatMessage> messages(String threadId, int limit) {
		return messages(threadId, limit, null, null, null);
	}

	public List<ChatMessage> messages(String threadId, int limit, String query,
			Long sinceMsInclusive, Long untilMsExclusive) {
		synchronized (lock) {
			reloadIfNewerLocked();
			return inbox.messages(threadId, limit, query, sinceMsInclusive,
					untilMsExclusive);
		}
	}

	public boolean displayMine(ChatMessage m) {
		synchronized (lock) {
			reloadIfNewerLocked();
			return inbox.displayMine(m);
		}
	}

	public List<ChatMessage> search(String query, Long sinceMsInclusive,
			Long untilMsExclusive) {
		synchronized (lock) {
			reloadIfNewerLocked();
			return inbox.search(query, sinceMsInclusive, untilMsExclusive);
		}
	}

	public void setReplyTemplate(String threadId, String template) {
		synchronized (lock) {
			mutateUnderFileLock(() -> {
				inbox.setReplyTemplate(threadId, template);
				return true;
			});
		}
	}

	/**
	 * Android notification bucket for this conversation. Missing or unknown
	 * becomes {@link ChatNotifyBucket#OTHER}. Not one channel per nick.
	 */
	public String notifyBucket(String threadId) {
		synchronized (lock) {
			reloadIfNewerLocked();
			return inbox.notifyBucket(threadId);
		}
	}

	public void setNotifyBucket(String threadId, String bucket) {
		synchronized (lock) {
			mutateUnderFileLock(() -> {
				inbox.setNotifyBucket(threadId, bucket);
				return true;
			});
		}
	}

	/** Opening a thread in the panel zeros its unread badge. */
	public void markSeen(String threadId) {
		synchronized (lock) {
			mutateUnderFileLock(() -> inbox.markSeen(threadId));
		}
	}

	/**
	 * Drop one conversation and its messages. Returns true when a thread existed.
	 */
	public boolean deleteThread(String threadId) {
		final boolean[] deleted = new boolean[1];
		synchronized (lock) {
			mutateUnderFileLock(() -> {
				deleted[0] = inbox.deleteThread(threadId);
				return deleted[0];
			});
		}
		if (deleted[0]) {
			notifyInboxUpdated();
		}
		return deleted[0];
	}

	/**
	 * Resolve a typed query to a stored thread id. Does not create a thread.
	 */
	public String resolveThreadId(String query) {
		synchronized (lock) {
			reloadIfNewerLocked();
			return inbox.resolveThreadId(query);
		}
	}

	public String replyTemplate(String threadId) {
		synchronized (lock) {
			reloadIfNewerLocked();
			return inbox.replyTemplate(threadId);
		}
	}

	static String fileNameForDisplay(String display) {
		String raw = display == null ? "" : display;
		return NON_WORD.matcher(raw).replaceAll("") + ".chat.json";
	}

	static void clearCacheForTest() {
		synchronized (CACHE_LOCK) {
			CACHE.clear();
		}
	}

	private void ensureContext(Context ctx) {
		if (app == null) {
			app = applicationContext(ctx);
		}
	}

	private void reloadIfNewerLocked() {
		if (app == null || fileName.length() == 0) {
			return;
		}
		long mtime = fileMtime(app, fileName);
		if (mtime > 0L && mtime > loadedMtime) {
			inbox = loadInbox(app, fileName);
			loadedMtime = mtime;
		}
	}

	/**
	 * Writes go through two process-local caches. Take an OS file lock, then
	 * the file as source of truth, then mutate, then persist — so a UI
	 * {@code setReplyTemplate} is not wiped by a concurrent trigger
	 * {@code append}.
	 *
	 * @param mutator return true to persist; false is a no-op after reload
	 * @return true when the file was written, or when this is in-memory
	 */
	private boolean mutateUnderFileLock(Mutator mutator) {
		if (app == null || fileName.length() == 0) {
			return mutator.apply() ? persistLocked() : true;
		}
		File lockFile = new File(app.getFilesDir(), fileName + ".lock");
		RandomAccessFile raf = null;
		FileLock flock = null;
		try {
			raf = new RandomAccessFile(lockFile, "rw");
			flock = raf.getChannel().lock();
			reloadFromDiskLocked();
			if (!mutator.apply()) {
				return true;
			}
			return persistLocked();
		} catch (IOException e) {
			BlowTorchLogger.logThrowable("ChatStore.lock", e);
			return false;
		} catch (RuntimeException e) {
			BlowTorchLogger.logThrowable("ChatStore.lock", e);
			return false;
		} finally {
			if (flock != null) {
				try {
					flock.release();
				} catch (IOException ignored) {
					// Lock held until close below.
				}
			}
			if (raf != null) {
				try {
					raf.close();
				} catch (IOException ignored) {
					// Best-effort.
				}
			}
		}
	}

	private interface Mutator {
		boolean apply();
	}

	private void reloadFromDiskLocked() {
		if (app == null || fileName.length() == 0) {
			return;
		}
		inbox = loadInbox(app, fileName);
		loadedMtime = fileMtime(app, fileName);
	}

	/**
	 * @return true when the file was written, or when this is an in-memory
	 *         store (tests). False when a disk write was required and failed.
	 */
	private boolean persistLocked() {
		if (app == null || fileName.length() == 0) {
			return true;
		}
		try {
			AtomicFiles.writeInternal(app, fileName, inbox.toJsonBytes(), true);
			loadedMtime = fileMtime(app, fileName);
			return true;
		} catch (IOException e) {
			BlowTorchLogger.logThrowable("ChatStore.persist", e);
			return false;
		} catch (RuntimeException e) {
			BlowTorchLogger.logThrowable("ChatStore.persist", e);
			return false;
		}
	}

	private void notifyInboxUpdated() {
		StellarService service = attached;
		if (service == null) {
			return;
		}
		try {
			service.notifyChatInboxUpdated(display);
		} catch (RuntimeException e) {
			BlowTorchLogger.logMinor("ChatStore.notify", e);
		}
	}

	private void notifyUnreadAppended(String threadId, String title, int unread) {
		StellarService service = attached;
		if (service == null) {
			return;
		}
		try {
			service.onChatUnreadAppended(display, threadId, title, unread);
		} catch (RuntimeException e) {
			BlowTorchLogger.logMinor("ChatStore.announce", e);
		}
	}

	private static Context applicationContext(Context ctx) {
		if (ctx == null) {
			return null;
		}
		Context app = ctx.getApplicationContext();
		return app != null ? app : ctx;
	}

	private static ChatInbox loadInbox(Context ctx, String fileName) {
		if (ctx == null || fileName == null || fileName.length() == 0) {
			return new ChatInbox();
		}
		File live = new File(ctx.getFilesDir(), fileName);
		byte[] data = readAll(live);
		if (data == null || data.length == 0) {
			File bak = new File(ctx.getFilesDir(), fileName + ".bak");
			data = readAll(bak);
		}
		if (data == null || data.length == 0) {
			return new ChatInbox();
		}
		return ChatInbox.fromJsonBytes(data);
	}

	private static long fileMtime(Context ctx, String fileName) {
		if (ctx == null || fileName == null || fileName.length() == 0) {
			return 0L;
		}
		File f = new File(ctx.getFilesDir(), fileName);
		return f.isFile() ? f.lastModified() : 0L;
	}

	private static byte[] readAll(File f) {
		if (f == null || !f.isFile() || f.length() == 0) {
			return null;
		}
		FileInputStream in = null;
		try {
			in = new FileInputStream(f);
			byte[] buf = new byte[(int) f.length()];
			int off = 0;
			while (off < buf.length) {
				int n = in.read(buf, off, buf.length - off);
				if (n < 0) {
					break;
				}
				off += n;
			}
			if (off != buf.length) {
				byte[] exact = new byte[off];
				System.arraycopy(buf, 0, exact, 0, off);
				return exact;
			}
			return buf;
		} catch (IOException e) {
			BlowTorchLogger.logMinor("ChatStore.read", e);
			return null;
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException ignored) {
					// Read-only handle.
				}
			}
		}
	}
}
