package com.resurrection.blowtorch2.lib.util;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

import com.resurrection.blowtorch2.lib.ui.SDCardUtils;

/**
 * Incremental plain-text session log (append-only). Default directory is
 * {@code /BlowTorch/session_logs/} (see {@link SDCardUtils}).
 *
 * <p><b>All file work happens on one daemon thread.</b> Callers stamp what they
 * need and enqueue; the writer opens, appends, rotates, flushes and closes.
 *
 * <p><b>Measured, 30 July 2026.</b> This class used to do that work on whatever
 * thread called it, and its callers are the {@code :stellar} main thread —
 * {@code Connection.dispatch} (per packet), {@code Processor.logGmcp} and
 * {@code McpEngine.logDir} (per marker). StrictMode on the test build recorded
 * <b>522 blocking-disk violations through {@code flushLocked}, about 8 s of
 * main-thread disk in total and up to 301 ms in one hit</b>, which makes it the
 * largest single main-thread disk source in the app after directory resolution.
 * Only 45 of those came from the old main-looper flush {@code Handler}; the rest
 * were the marker and packet paths flushing inline, so moving the timer alone
 * would have fixed under a tenth of it. There is no {@code Handler} here any
 * more — the writer does its own timed flush.
 *
 * <p>Writes reach the OS within {@link #FLUSH_INTERVAL_MS} (or sooner, once a
 * few KB accumulate), so a file manager still sees near-live growth.
 */
public final class SessionLogger {

	private static final String TAG = "SessionLogger";
	private static final String PREFS = "SESSION_LOG_PREFS";
	private static final String KEY_ENABLED = "enabled";
	private static final String KEY_CUSTOM_DIR = "custom_dir";
	private static final long MAX_BYTES = 8 * 1024 * 1024;
	/** Flush to OS buffers at least this often while data arrives. */
	private static final long FLUSH_INTERVAL_MS = 750L;
	/** Flush sooner if this much is buffered. */
	private static final int FLUSH_BYTES = 4 * 1024;
	/**
	 * Work waiting for the writer.
	 *
	 * <p>Bounded, because a world that floods faster than external storage can
	 * absorb must cost log lines rather than unbounded memory in the service.
	 * Larger than the GMCP trace queue on purpose: this is the player's own
	 * record of their session, and one entry here is a whole packet, not a line.
	 */
	private static final int QUEUE_LIMIT = 8192;
	/** How long {@link #endSession} will wait for the tail to reach disk. */
	private static final long END_SESSION_DRAIN_MS = 400L;
	private static final Pattern ANSI = Pattern.compile("\\u001B\\[[0-9;]*[A-Za-z]");

	// ---- caller-side state ------------------------------------------------

	private static boolean enabledCached = false;
	private static String customDirCached = "";
	private static boolean prefsLoaded = false;

	/**
	 * What the caller has asked for, as opposed to what the writer has managed
	 * so far. {@link #hasActiveSessionFor} has to answer from this: the answer
	 * decides the wording of a marker that is enqueued in the same breath, and
	 * queue order, not disk order, is what those two share.
	 */
	private static volatile String intendedProfile;
	private static volatile boolean intendedHasFile;

	/** Published by the writer once it knows; read by the UI for display. */
	private static volatile File resolvedFile;
	private static volatile Uri resolvedDocUri;
	private static volatile String resolvedDirLabel;

	// ---- writer-owned state ----------------------------------------------

	private static File currentFile;
	private static Uri currentDocUri;
	private static String currentProfile;
	private static FileOutputStream currentFos;
	private static OutputStream currentOut;
	private static long pendingBytes;
	private static long lastFlushElapsed;
	private static long bytesWrittenThisFile;

	private static final BlockingQueue<Op> QUEUE = new LinkedBlockingQueue<Op>(QUEUE_LIMIT);
	private static final AtomicInteger DROPPED = new AtomicInteger();
	private static Thread writer;
	private static Context writerContext;

	/** One unit of work for the writer. */
	private static final class Op {
		/** Append {@link #text}. */
		static final int WRITE = 0;
		/** Start (or continue) a file for {@link #profile}. */
		static final int START = 1;
		/** Append {@link #text} with the resolved log location appended to it. */
		static final int LOCATION = 2;
		/** Push to the OS; {@link #flag} asks for fsync as well. */
		static final int FLUSH = 3;
		/**
		 * Flush and close, keeping which file this was; {@link #flag} asks for
		 * fsync first. A closed stream over a remembered file is exactly the
		 * state a reconnect needs — see {@link #endSession}.
		 */
		static final int CLOSE = 4;
		/** Close and forget the file, so the next write starts a new one. */
		static final int RESET = 5;
		/** Count {@link #latch} down once everything before it is done. */
		static final int BARRIER = 6;

		final int kind;
		final String text;
		final String profile;
		final boolean flag;
		final CountDownLatch latch;

		Op(int kind, String text, String profile, boolean flag, CountDownLatch latch) {
			this.kind = kind;
			this.text = text;
			this.profile = profile;
			this.flag = flag;
			this.latch = latch;
		}
	}

	private SessionLogger() {
	}

	public static synchronized void setEnabled(Context context, boolean enabled) {
		if (context == null) {
			return;
		}
		ensurePrefs(context);
		boolean wasEnabled = enabledCached;
		context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
				.edit()
				.putBoolean(KEY_ENABLED, enabled)
				.apply();
		enabledCached = enabled;
		prefsLoaded = true;
		if (!enabled && wasEnabled) {
			if (intendedHasFile) {
				enqueue(context, new Op(Op.WRITE,
						markerText("logging disabled"), null, false, null));
			}
			// RESET, not CLOSE: turning logging off must not leave a file that a
			// later start would append to.
			enqueue(context, new Op(Op.RESET, null, null, true, null));
			clearIntendedMeta();
		}
	}

	public static synchronized void setCustomDirectory(Context context, String path) {
		if (context == null) {
			return;
		}
		String normalized = path == null ? "" : path.trim();
		ensurePrefs(context);
		boolean changed = !normalized.equals(customDirCached);
		context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
				.edit()
				.putString(KEY_CUSTOM_DIR, normalized)
				.apply();
		customDirCached = normalized;
		prefsLoaded = true;
		// Only start a new file when the directory actually changes — re-applying
		// the same Options value must not truncate an active session log.
		if (changed) {
			resolvedDirLabel = null;
			// RESET: the old file is in the old directory, so it is not the one
			// to keep appending to.
			enqueue(context, new Op(Op.RESET, null, null, true, null));
			clearIntendedMeta();
		}
	}

	public static synchronized boolean isEnabled(Context context) {
		if (context == null) {
			return false;
		}
		ensurePrefs(context);
		return enabledCached;
	}

	/** The file being written, once the writer has opened one. */
	public static File getCurrentLogFile() {
		return resolvedFile;
	}

	/** Display path for UI: filesystem path, SAF document URI, or directory label. */
	public static String getLogLocationLabel(Context context) {
		Uri doc = resolvedDocUri;
		if (doc != null) {
			return doc.toString();
		}
		File file = resolvedFile;
		if (file != null) {
			return file.getAbsolutePath();
		}
		String custom;
		synchronized (SessionLogger.class) {
			ensurePrefs(context);
			custom = customDirCached;
		}
		if (SDCardUtils.isContentUri(custom)) {
			return custom;
		}
		// Last known directory before resolving one again: resolveBlowTorchSubdir
		// and ensureWritableDirectory are themselves main-thread disk (517 ms in
		// the worst measured hit), and this getter is called from the UI.
		String cached = resolvedDirLabel;
		if (cached != null) {
			return cached;
		}
		File dir = getLogDirectory(context);
		return dir != null ? dir.getAbsolutePath() : "";
	}

	/**
	 * Resolve the log directory, creating it if need be.
	 *
	 * <p>Touches the filesystem, so the writer thread is the right caller. It
	 * stays public for {@code .gmcp} / options code that wants to name the
	 * directory; those run off the main thread.
	 *
	 * @param context Any context.
	 * @return The directory, or null when none is writable.
	 */
	public static File getLogDirectory(Context context) {
		String custom;
		synchronized (SessionLogger.class) {
			ensurePrefs(context);
			custom = customDirCached;
		}
		File resolved = resolveLogDirectory(context, custom);
		if (resolved != null) {
			resolvedDirLabel = resolved.getAbsolutePath();
		}
		return resolved;
	}

	private static File resolveLogDirectory(Context context, String custom) {
		if (!TextUtils.isEmpty(custom)) {
			if (SDCardUtils.isContentUri(custom)) {
				File mapped = SDCardUtils.mapTreeUriToFile(Uri.parse(custom));
				if (mapped != null && SDCardUtils.ensureWritableDirectory(mapped)) {
					return mapped;
				}
				return null;
			}
			File dir = new File(custom);
			if (SDCardUtils.ensureWritableDirectory(dir)) {
				return dir;
			}
			BlowTorchLogger.logError(context, TAG,
					"Cannot write session log directory: " + dir.getAbsolutePath());
			return null;
		}
		return SDCardUtils.resolveBlowTorchSubdir(context, SDCardUtils.SUBDIR_SESSION_LOGS);
	}

	/** Begin a new file for this profile. */
	public static void startSession(Context context, String profile) {
		if (context == null || !isEnabled(context)) {
			return;
		}
		intendedProfile = sanitizeProfile(profile);
		intendedHasFile = true;
		enqueue(context, new Op(Op.START, null, profile, false, null));
	}

	/**
	 * Keep writing to the current profile file after a reconnect; otherwise
	 * start a new one. The writer decides which, since only it knows whether the
	 * file it opened is still there.
	 *
	 * @param context Any context.
	 * @param profile Connection display name.
	 */
	public static void continueOrStartSession(Context context, String profile) {
		if (context == null || !isEnabled(context)) {
			return;
		}
		intendedProfile = sanitizeProfile(profile);
		intendedHasFile = true;
		enqueue(context, new Op(Op.START, null, profile, true, null));
	}

	/** True if a log file is already associated with this profile. */
	public static boolean hasActiveSessionFor(String profile) {
		String safe = sanitizeProfile(profile);
		String intended = intendedProfile;
		return intendedHasFile && intended != null && intended.equals(safe);
	}

	public static void appendIncoming(Context context, String profile, String text) {
		if (context == null || text == null || text.length() == 0 || !isEnabled(context)) {
			return;
		}
		// Stripping ANSI is CPU, not disk, and doing it here keeps the writer
		// from becoming the bottleneck on a busy world.
		String plain = ANSI.matcher(text).replaceAll("").replace('\r', '\n');
		ensureIntended(context, profile);
		enqueue(context, new Op(Op.WRITE, plain, null, false, null));
	}

	public static void appendMarker(Context context, String profile, String marker) {
		if (context == null || marker == null || !isEnabled(context)) {
			return;
		}
		ensureIntended(context, profile);
		enqueue(context, new Op(Op.WRITE, markerText(marker), null, false, null));
	}

	/**
	 * A marker that names the log's own location, e.g. {@code "connected → "}.
	 *
	 * <p>The caller cannot build this itself any more: the location is only known
	 * once the writer has resolved a directory and opened a file, and it enqueues
	 * this marker before that has happened. So the writer fills in the tail.
	 *
	 * @param context Any context.
	 * @param profile Connection display name.
	 * @param prefix Text before the location.
	 */
	public static void appendLocationMarker(Context context, String profile, String prefix) {
		if (context == null || !isEnabled(context)) {
			return;
		}
		ensureIntended(context, profile);
		enqueue(context, new Op(Op.LOCATION, prefix != null ? prefix : "", null, false, null));
	}

	/**
	 * Push buffered log bytes to disk and fsync.
	 *
	 * <p>Enqueued like everything else, so it returns immediately and the syscall
	 * lands on the writer within a hair. {@link #endSession} is the one that
	 * waits, because that is the one where the process may be about to stop.
	 *
	 * @param context Any context.
	 */
	public static void flush(Context context) {
		if (context == null) {
			return;
		}
		enqueue(context, new Op(Op.FLUSH, null, null, true, null));
	}

	/**
	 * Flush and close the open stream without starting a new file.
	 *
	 * <p>Waits up to {@link #END_SESSION_DRAIN_MS} for the queue to drain. This
	 * is the one place worth blocking a caller: the alternative is losing the
	 * tail of a log on the path where nothing will come back for it. The bound
	 * matters as much as the wait — {@code onDisconnected} runs on the service
	 * main thread, and an unbounded wait there is how a disconnect stalls.
	 *
	 * @param context Any context.
	 */
	public static void endSession(Context context) {
		if (context == null) {
			return;
		}
		if (intendedHasFile) {
			enqueue(context, new Op(Op.WRITE, markerText("disconnected"), null, false, null));
		}
		// CLOSE, not RESET, and intendedHasFile stays true. A dropped TCP session
		// is usually followed by a reconnect, and onConnected wants to keep
		// appending to the same file rather than fragment the player's log into
		// one file per drop — it asks hasActiveSessionFor to pick the wording and
		// continueOrStartSession to pick the file, and both have to still say
		// yes. The stream is closed; the writer reopens it in append mode on the
		// next write, because currentProfile is still set.
		enqueue(context, new Op(Op.CLOSE, null, null, true, null));
		CountDownLatch done = new CountDownLatch(1);
		if (!enqueue(context, new Op(Op.BARRIER, null, null, false, done))) {
			return;
		}
		try {
			if (!done.await(END_SESSION_DRAIN_MS, TimeUnit.MILLISECONDS)) {
				Log.w(TAG, "Session log still draining after " + END_SESSION_DRAIN_MS
						+ " ms; leaving it to the writer");
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private static String markerText(String marker) {
		String stamp = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
		return "\n--- " + stamp + " " + marker + " ---\n";
	}

	/** A write for a profile we have not opened a file for yet implies a start. */
	private static void ensureIntended(Context context, String profile) {
		String safe = sanitizeProfile(profile);
		if (!intendedHasFile || intendedProfile == null || !intendedProfile.equals(safe)) {
			intendedProfile = safe;
			intendedHasFile = true;
			enqueue(context, new Op(Op.START, null, profile, true, null));
		}
	}

	private static void clearIntendedMeta() {
		intendedProfile = null;
		intendedHasFile = false;
		resolvedFile = null;
		resolvedDocUri = null;
	}

	private static synchronized void ensurePrefs(Context context) {
		if (prefsLoaded || context == null) {
			return;
		}
		// Still synchronous, and still main-thread disk on the first call in a
		// process: 30 violations, worst 180 ms. Deferring it would mean isEnabled
		// answering "no" before the answer is known, which loses the opening
		// lines of a session. Reported, not fixed here.
		SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
		enabledCached = prefs.getBoolean(KEY_ENABLED, false);
		customDirCached = prefs.getString(KEY_CUSTOM_DIR, "");
		if (customDirCached == null) {
			customDirCached = "";
		}
		prefsLoaded = true;
	}

	// ---- the writer ------------------------------------------------------

	/**
	 * Hand one unit of work to the writer, starting it on first use.
	 *
	 * @return false when the queue was full and the work was dropped.
	 */
	private static boolean enqueue(Context context, Op op) {
		startWriter(context.getApplicationContext());
		if (QUEUE.offer(op)) {
			return true;
		}
		// A full queue is already the busiest moment there is; counting is all
		// that happens here, and the writer says so once it has room again.
		DROPPED.incrementAndGet();
		if (op.latch != null) {
			op.latch.countDown();
		}
		return false;
	}

	private static synchronized void startWriter(Context app) {
		writerContext = app;
		if (writer != null) {
			return;
		}
		writer = new Thread(new Runnable() {
			@Override
			public void run() {
				writerLoop();
			}
		}, "session-log");
		writer.setDaemon(true);
		writer.setPriority(Thread.MIN_PRIORITY);
		writer.start();
	}

	private static void writerLoop() {
		while (true) {
			Op op;
			try {
				if (pendingBytes > 0) {
					long due = FLUSH_INTERVAL_MS
							- (SystemClock.elapsedRealtime() - lastFlushElapsed);
					op = due <= 0 ? QUEUE.poll() : QUEUE.poll(due, TimeUnit.MILLISECONDS);
					if (op == null) {
						// Nothing arrived before the interval was up: this is the
						// timed flush the main-looper Handler used to do.
						flushWriter(false);
						continue;
					}
				} else {
					op = QUEUE.take();
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
			reportDrops();
			try {
				apply(op);
			} catch (RuntimeException e) {
				// The writer must outlive one bad op; a dead logging thread would
				// silently stop logging for the rest of the process's life.
				Log.e(TAG, "Session log op failed", e);
			}
			if (op.latch != null) {
				op.latch.countDown();
			}
		}
	}

	/** Say in the log itself that it has a hole in it. */
	private static void reportDrops() {
		int dropped = DROPPED.getAndSet(0);
		if (dropped <= 0) {
			return;
		}
		Log.w(TAG, "Session log queue was full; dropped " + dropped + " entries");
		if (currentOut != null) {
			writeWriter(markerText(dropped + " log entries dropped: writing could not keep up"),
					false);
		}
	}

	private static void apply(Op op) {
		switch (op.kind) {
		case Op.WRITE:
			if (op.text != null && op.text.length() > 0) {
				rotateIfFull();
				writeWriter(op.text, false);
			}
			break;
		case Op.LOCATION:
			rotateIfFull();
			writeWriter(markerText(op.text + locationLabelWriter()), false);
			break;
		case Op.START:
			startWriterSession(op.profile, op.flag);
			break;
		case Op.FLUSH:
			flushWriter(op.flag);
			break;
		case Op.CLOSE:
			closeWriter(op.flag);
			break;
		case Op.RESET:
			closeWriter(op.flag);
			clearWriterMeta();
			break;
		case Op.BARRIER:
		default:
			break;
		}
	}

	/** What the writer knows the log's location to be, for a LOCATION marker. */
	private static String locationLabelWriter() {
		if (currentDocUri != null) {
			return currentDocUri.toString();
		}
		if (currentFile != null) {
			return currentFile.getAbsolutePath();
		}
		String dir = resolvedDirLabel;
		return dir != null ? dir : "(nowhere writable)";
	}

	private static void rotateIfFull() {
		if (currentOut == null || bytesWrittenThisFile <= MAX_BYTES) {
			return;
		}
		writeWriter("\n=== log rotated (size limit) ===\n", true);
		startWriterSession(currentProfile, false);
	}

	private static void startWriterSession(String profile, boolean continueIfSame) {
		Context context = writerContext;
		if (context == null) {
			return;
		}
		String safe = sanitizeProfile(profile);
		if (continueIfSame && safe.equals(currentProfile)) {
			boolean haveFile = (currentFile != null && currentFile.exists())
					|| currentDocUri != null;
			if (haveFile && (currentOut != null || openWriterStream(context))) {
				return;
			}
		}

		String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date());
		String header = "=== BlowTorch session log: " + profile + " @ " + stamp + " ===\n";
		closeWriter(true);
		clearWriterMeta();

		String custom;
		synchronized (SessionLogger.class) {
			custom = customDirCached;
		}
		if (SDCardUtils.isContentUri(custom)
				&& SDCardUtils.mapTreeUriToFile(Uri.parse(custom)) == null) {
			DocumentFile tree = DocumentFile.fromTreeUri(context, Uri.parse(custom));
			if (tree != null && tree.canWrite()) {
				DocumentFile file = tree.createFile("text/plain", safe + "_" + stamp + ".txt");
				if (file != null) {
					currentDocUri = file.getUri();
					currentProfile = safe;
					resolvedDocUri = currentDocUri;
					if (!openWriterStream(context) || !writeWriter(header, true)) {
						BlowTorchLogger.logError(context, TAG,
								"Failed to write session log via SAF: " + currentDocUri);
						closeWriter(false);
						clearWriterMeta();
					}
					return;
				}
			}
			BlowTorchLogger.logError(context, TAG,
					"SAF session log tree unusable; falling back to /BlowTorch/session_logs");
		}

		File dir = resolveLogDirectory(context, custom);
		if (dir == null || !SDCardUtils.ensureWritableDirectory(dir)) {
			dir = SDCardUtils.resolveBlowTorchSubdir(context, SDCardUtils.SUBDIR_SESSION_LOGS);
		}
		if (!SDCardUtils.ensureWritableDirectory(dir)) {
			BlowTorchLogger.logError(context, TAG,
					"Cannot create session log directory: "
							+ (dir != null ? dir.getAbsolutePath() : "(null)")
							+ " — grant All files access (Options → Manage Storage Access)");
			return;
		}
		resolvedDirLabel = dir.getAbsolutePath();
		File target = new File(dir, safe + "_" + stamp + ".txt");
		currentFile = target;
		currentProfile = safe;
		resolvedFile = target;
		if (!openWriterStream(context) || !writeWriter(header, true)) {
			BlowTorchLogger.logError(context, TAG,
					"Failed to create session log file: " + target.getAbsolutePath());
			closeWriter(false);
			clearWriterMeta();
		} else {
			Log.i(TAG, "Session log started: " + target.getAbsolutePath());
		}
	}

	private static void clearWriterMeta() {
		currentFile = null;
		currentDocUri = null;
		currentProfile = null;
		bytesWrittenThisFile = 0L;
		pendingBytes = 0L;
		resolvedDocUri = null;
	}

	private static boolean openWriterStream(Context context) {
		closeWriter(false);
		pendingBytes = 0L;
		bytesWrittenThisFile = 0L;
		lastFlushElapsed = SystemClock.elapsedRealtime();
		try {
			if (currentDocUri != null) {
				OutputStream raw = context.getContentResolver()
						.openOutputStream(currentDocUri, "wa");
				if (raw == null) {
					return false;
				}
				currentFos = null;
				currentOut = new BufferedOutputStream(raw, 8192);
				return true;
			}
			if (currentFile != null) {
				File parent = currentFile.getParentFile();
				if (parent != null && !SDCardUtils.ensureWritableDirectory(parent)) {
					Log.e(TAG, "Parent not writable: " + parent.getAbsolutePath());
					return false;
				}
				currentFos = new FileOutputStream(currentFile, true);
				currentOut = new BufferedOutputStream(currentFos, 8192);
				bytesWrittenThisFile = currentFile.length();
				return true;
			}
		} catch (IOException e) {
			Log.e(TAG, "Open stream failed", e);
			currentOut = null;
			currentFos = null;
			return false;
		}
		return false;
	}

	private static boolean writeWriter(String text, boolean forceFlush) {
		Context context = writerContext;
		if (text == null || text.length() == 0) {
			return true;
		}
		if (currentOut == null) {
			if (context == null || currentProfile == null) {
				return false;
			}
			if (!openWriterStream(context)) {
				return false;
			}
		}
		try {
			byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
			currentOut.write(bytes);
			pendingBytes += bytes.length;
			bytesWrittenThisFile += bytes.length;
			// No fsync on this path. Markers no longer force one either: with the
			// timed flush on this thread, the file grows within FLUSH_INTERVAL_MS
			// anyway, and forcing a sync per marker was 46 fsyncs of up to 332 ms
			// in one session. endSession and flush() still sync.
			if (forceFlush || pendingBytes >= FLUSH_BYTES) {
				flushWriter(false);
			}
			return true;
		} catch (IOException e) {
			Log.e(TAG, "Write failed", e);
			closeWriter(false);
			clearWriterMeta();
			return false;
		}
	}

	private static void flushWriter(boolean syncToDisk) {
		if (currentOut == null) {
			pendingBytes = 0L;
			return;
		}
		try {
			currentOut.flush();
			if (syncToDisk && currentFos != null) {
				try {
					currentFos.getFD().sync();
				} catch (IOException ignored) {
					// Some filesystems reject sync; flush is still enough for most readers.
				}
			}
			pendingBytes = 0L;
			lastFlushElapsed = SystemClock.elapsedRealtime();
		} catch (IOException e) {
			Log.e(TAG, "Flush failed", e);
			closeWriter(false);
			clearWriterMeta();
		}
	}

	private static void closeWriter(boolean flushFirst) {
		if (currentOut == null) {
			currentFos = null;
			pendingBytes = 0L;
			return;
		}
		try {
			if (flushFirst) {
				currentOut.flush();
				if (currentFos != null) {
					try {
						currentFos.getFD().sync();
					} catch (IOException ignored) {
					}
				}
			}
		} catch (IOException e) {
			Log.e(TAG, "Close flush failed", e);
		}
		try {
			currentOut.close();
		} catch (IOException e) {
			Log.e(TAG, "Close failed", e);
		}
		currentOut = null;
		currentFos = null;
		pendingBytes = 0L;
	}

	private static String sanitizeProfile(String profile) {
		if (profile == null || profile.trim().isEmpty()) {
			return "session";
		}
		return profile.replaceAll("[^A-Za-z0-9._-]+", "_");
	}
}
