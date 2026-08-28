package com.resurrection.blowtorch2.lib.chat;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * In-memory inbox plus JSON encode/decode and the message cap.
 *
 * <p>Package-visible so {@link ChatStoreTest} can exercise append/list/search/cap
 * without Android {@code Context} or {@code AtomicFiles}. {@link ChatStore} is
 * the file + notify wrapper around this. Default cap is 4000; 0 in Options
 * means {@link #HARD_MAX_MESSAGES} so the phone does not run out of RAM.
 */
final class ChatInbox {

	static final int DEFAULT_MAX_MESSAGES = 4000;
	static final int HARD_MAX_MESSAGES = 50000;
	/** Same as {@link #DEFAULT_MAX_MESSAGES}; tests and old call sites. */
	static final int MAX_MESSAGES = DEFAULT_MAX_MESSAGES;
	static final int DEFAULT_MINE_COLOR = 0xFF1B6B66;
	static final int DEFAULT_OTHER_COLOR = 0xFF333333;
	static final long ABSORB_MINE_MS = 8000L;
	private static final Charset UTF8 = Charset.forName("UTF-8");
	private static final Pattern PLAIN_NAME = Pattern.compile(
			"^[A-Za-z][A-Za-z0-9_'-]*$");
	/** A channel wrapper with no speaker — every line on that channel. */
	private static final Pattern TOO_BROAD_CHANNEL_TAG = Pattern.compile(
			"^(?:\\[[^\\]]+\\]|\\([^)]+\\)|<[^>]+>)\\s*:?\\s*$");

	private static final class ThreadState {
		String threadId;
		String title;
		String replyTemplate;
		int unreadCount;
		String mineNeedle;
		Pattern mineCompiled;
		/** 0 = inherit the world-level mineColor. */
		int mineColor;

		/** Android notification bucket; {@link ChatNotifyBucket#OTHER} when unset. */
		String notifyBucket;

		ThreadState(String threadId, String title, String replyTemplate, int unreadCount) {
			this.threadId = threadId == null ? "" : threadId;
			this.title = title == null ? "" : title;
			this.replyTemplate = replyTemplate == null ? "" : replyTemplate;
			this.unreadCount = unreadCount < 0 ? 0 : unreadCount;
			this.mineNeedle = "";
			this.mineCompiled = null;
			this.mineColor = 0;
			this.notifyBucket = ChatNotifyBucket.OTHER;
		}

		void setMineNeedle(String needle) {
			this.mineNeedle = needle == null ? "" : needle;
			this.mineCompiled = compileMinePattern(this.mineNeedle);
		}
	}

	private final LinkedHashMap<String, ThreadState> threads =
			new LinkedHashMap<String, ThreadState>();
	private final ArrayList<ChatMessage> messages = new ArrayList<ChatMessage>();
	private int mineColor = DEFAULT_MINE_COLOR;
	private int otherColor = DEFAULT_OTHER_COLOR;
	private int maxMessages = DEFAULT_MAX_MESSAGES;
	/** Last non-empty per-thread needle; JSON root fallback after a process death. */
	private String defaultMineNeedle = "";

	void append(String threadId, String title, String body, long whenMs) {
		append(threadId, title, body, whenMs, false, true);
	}

	void append(String threadId, String title, String body, long whenMs,
			boolean mine, boolean countUnread) {
		String id = threadId == null ? "" : threadId;
		String resolvedTitle = title == null || title.length() == 0 ? id : title;
		String resolvedBody = body == null ? "" : body;
		ThreadState state = threads.get(id);
		if (state == null) {
			state = new ThreadState(id, resolvedTitle, "", 0);
			threads.put(id, state);
		} else if (title != null && title.length() > 0) {
			state.title = title;
		}
		if (countUnread) {
			state.unreadCount++;
		}
		messages.add(new ChatMessage(id, resolvedTitle, resolvedBody, whenMs, mine));
		capOldest();
	}

	/**
	 * Last mine bubble in this thread was the typed reply; the MUD echo just
	 * arrived. Replace the short local line with the full echo, still mine.
	 * Walks past other people's lines so a busy channel does not block absorb.
	 *
	 * @param markMine upgrade the kept bubble to mine when the trigger said so
	 * @return true when the file should be persisted
	 */
	boolean absorbRecentMine(String threadId, String incomingBody, long whenMs,
			boolean markMine) {
		String id = threadId == null ? "" : threadId;
		String incoming = incomingBody == null ? "" : incomingBody;
		if (incoming.length() == 0) {
			return false;
		}
		for (int i = messages.size() - 1; i >= 0; i--) {
			ChatMessage m = messages.get(i);
			if (!id.equals(m.getThreadId())) {
				continue;
			}
			if (whenMs - m.getWhenMs() > ABSORB_MINE_MS) {
				continue;
			}
			if (incoming.equals(m.getBody())) {
				if (markMine && !m.isMine()) {
					messages.set(i, new ChatMessage(m.getThreadId(), m.getTitle(),
							incoming, whenMs, true));
					return true;
				}
				return false;
			}
			if (!m.isMine()) {
				continue;
			}
			String prev = m.getBody();
			if (prev.length() == 0) {
				continue;
			}
			boolean quoted = incoming.indexOf("\"" + prev + "\"") >= 0
					|| incoming.indexOf("'" + prev + "'") >= 0;
			if (!quoted && (prev.length() < 4 || incoming.indexOf(prev) < 0)) {
				continue;
			}
			messages.set(i, new ChatMessage(m.getThreadId(), m.getTitle(), incoming,
					whenMs, true));
			return true;
		}
		return false;
	}

	/**
	 * Chat-module mine trigger for one thread: the stored line matches that
	 * thread's {@code mineNeedle}. A plain name is you as speaker (start of
	 * line, or after {@code ]}/{@code >}/{@code )} with an optional colon).
	 * A pasted {@code [channel]} alone is too broad and matches nothing.
	 * {@code |} / {@code (?} compile as regex; everything else is a literal
	 * substring.
	 */
	boolean bodyLooksMine(String threadId, String body) {
		if (body == null) {
			return false;
		}
		ThreadState state = threads.get(threadId == null ? "" : threadId);
		if (state == null) {
			return false;
		}
		Pattern p = compiledMine(state);
		if (p == null) {
			return false;
		}
		return p.matcher(body).find();
	}

	private static Pattern compiledMine(ThreadState state) {
		if (state.mineCompiled != null) {
			return state.mineCompiled;
		}
		if (state.mineNeedle == null || state.mineNeedle.length() == 0) {
			return null;
		}
		state.mineCompiled = compileMinePattern(state.mineNeedle);
		return state.mineCompiled;
	}

	/**
	 * Paint as an own bubble: that message's thread mine trigger, or a stored
	 * {@code mine} flag (Send / absorb / a legacy Send-to-thread action).
	 * Do not unpaint stored mine on a channel echo — absorb rewrites the
	 * Send bubble to {@code [channel] Name says} and that line may not match
	 * a {@code You say} pattern.
	 */
	boolean displayMine(ChatMessage m) {
		if (m == null) {
			return false;
		}
		if (m.isMine()) {
			return true;
		}
		if ("You".equals(m.getTitle())) {
			return true;
		}
		return bodyLooksMine(m.getThreadId(), m.getBody());
	}

	/**
	 * Upgrade stored {@code mine} from the current needles so a process
	 * death still paints own-bubbles even if paint only looked at the flag.
	 * A non-empty needle also unpaints lines that no longer match, except
	 * Send bubbles titled {@code You} (absorb rewrites those to a channel
	 * echo that may not match {@code You say}).
	 */
	void restampMineFlags() {
		restampMineFlags(null);
	}

	void restampMineFlags(String threadId) {
		String only = threadId == null ? null : threadId;
		for (int i = 0; i < messages.size(); i++) {
			ChatMessage m = messages.get(i);
			if (only != null && !only.equals(m.getThreadId())) {
				continue;
			}
			boolean sticky = "You".equals(m.getTitle());
			boolean match = sticky
					|| bodyLooksMine(m.getThreadId(), m.getBody());
			if (match == m.isMine()) {
				continue;
			}
			if (!match) {
				String needle = mineNeedle(m.getThreadId());
				if (needle == null || needle.length() == 0) {
					continue;
				}
			}
			messages.set(i, new ChatMessage(m.getThreadId(), m.getTitle(),
					m.getBody(), m.getWhenMs(), match));
		}
	}

	/** Leftover: world-level Me is unused. Prefer {@link #mineNeedle(String)}. */
	String mineNeedle() {
		return "";
	}

	String mineNeedle(String threadId) {
		ThreadState state = threads.get(threadId == null ? "" : threadId);
		if (state == null || state.mineNeedle == null) {
			return "";
		}
		return state.mineNeedle;
	}

	void setMineNeedle(String threadId, String needle) {
		String id = threadId == null ? "" : threadId;
		ThreadState state = threads.get(id);
		if (state == null) {
			state = new ThreadState(id, id, "", 0);
			threads.put(id, state);
		}
		String previous = state.mineNeedle;
		state.setMineNeedle(needle);
		if (state.mineNeedle.length() > 0) {
			defaultMineNeedle = state.mineNeedle;
		} else if (previous != null && previous.length() > 0) {
			refreshDefaultMineNeedle();
		}
		restampMineFlags(id);
	}

	private void refreshDefaultMineNeedle() {
		defaultMineNeedle = "";
		for (ThreadState s : threads.values()) {
			if (s.mineNeedle != null && s.mineNeedle.length() > 0) {
				defaultMineNeedle = s.mineNeedle;
				return;
			}
		}
	}

	/**
	 * Compile the chat-module mine trigger. Package-visible for tests.
	 * A single word is you as speaker: start of line, or after {@code ]},
	 * {@code >} or {@code )} with an optional colon (worlds print chat
	 * differently). A channel tag with no speaker matches nothing.
	 * {@code |} or {@code (?} is regex (invalid syntax is matched literally).
	 * Anything else, including a pasted {@code [channel] Ada says, "}, is
	 * a literal substring — {@code [} must not become a character class.
	 */
	static Pattern compileMinePattern(String source) {
		String s = source == null ? "" : source.trim();
		if (s.length() < 2) {
			return null;
		}
		if (PLAIN_NAME.matcher(s).matches()) {
			return Pattern.compile("(?:\\A|[\\]>)]\\s*:?\\s*)" + Pattern.quote(s) + "\\b",
					Pattern.CASE_INSENSITIVE);
		}
		if (TOO_BROAD_CHANNEL_TAG.matcher(s).matches()) {
			return null;
		}
		if (looksLikeRegex(s)) {
			try {
				return Pattern.compile(s);
			} catch (PatternSyntaxException bad) {
				return Pattern.compile(Pattern.quote(s), Pattern.CASE_INSENSITIVE);
			}
		}
		return Pattern.compile(Pattern.quote(s), Pattern.CASE_INSENSITIVE);
	}

	static boolean looksLikeRegex(String s) {
		return s != null && (s.indexOf('|') >= 0 || s.indexOf("(?") >= 0);
	}

	int mineColor() {
		return mineColor;
	}

	void setMineColor(int argb) {
		mineColor = argb;
	}

	/**
	 * Own-bubble colour for one thread. {@code 0} on the thread inherits
	 * the world default ({@link #mineColor()}). Missing threads inherit.
	 */
	int mineColor(String threadId) {
		ThreadState state = threads.get(threadId == null ? "" : threadId);
		if (state == null || state.mineColor == 0) {
			return mineColor;
		}
		return state.mineColor;
	}

	void setMineColor(String threadId, int argb) {
		String id = threadId == null ? "" : threadId;
		ThreadState state = threads.get(id);
		if (state == null) {
			state = new ThreadState(id, id, "", 0);
			threads.put(id, state);
		}
		state.mineColor = argb;
	}

	int otherColor() {
		return otherColor;
	}

	void setOtherColor(int argb) {
		otherColor = argb;
	}

	/**
	 * {@code 0} or negative is {@link #HARD_MAX_MESSAGES} (Options "no limit").
	 */
	void setMaxMessages(int max) {
		maxMessages = coerceMaxMessages(Integer.valueOf(max));
	}

	int maxMessages() {
		return maxMessages;
	}

	static int coerceMaxMessages(Object value) {
		int n = DEFAULT_MAX_MESSAGES;
		if (value instanceof Number) {
			n = ((Number) value).intValue();
		} else if (value instanceof String) {
			try {
				n = Integer.parseInt(((String) value).trim());
			} catch (NumberFormatException e) {
				n = DEFAULT_MAX_MESSAGES;
			}
		} else if (value != null) {
			n = DEFAULT_MAX_MESSAGES;
		}
		if (n <= 0) {
			return HARD_MAX_MESSAGES;
		}
		if (n > HARD_MAX_MESSAGES) {
			return HARD_MAX_MESSAGES;
		}
		return n;
	}

	void capOldest() {
		int cap = maxMessages <= 0 ? HARD_MAX_MESSAGES : maxMessages;
		if (cap > HARD_MAX_MESSAGES) {
			cap = HARD_MAX_MESSAGES;
		}
		boolean dropped = false;
		while (messages.size() > cap) {
			messages.remove(0);
			dropped = true;
		}
		if (dropped) {
			pruneThreadsWithoutMessages();
		}
	}

	void pruneThreadsWithoutMessages() {
		HashSet<String> live = new HashSet<String>();
		for (int i = 0; i < messages.size(); i++) {
			live.add(messages.get(i).getThreadId());
		}
		ArrayList<String> gone = new ArrayList<String>();
		for (String id : threads.keySet()) {
			if (!live.contains(id)) {
				gone.add(id);
			}
		}
		for (int i = 0; i < gone.size(); i++) {
			threads.remove(gone.get(i));
		}
		for (ThreadState state : threads.values()) {
			int n = 0;
			for (int i = 0; i < messages.size(); i++) {
				if (state.threadId.equals(messages.get(i).getThreadId())) {
					n++;
				}
			}
			if (state.unreadCount > n) {
				state.unreadCount = n;
			}
		}
	}

	List<ChatThreadSummary> listThreads() {
		ArrayList<ChatThreadSummary> out = new ArrayList<ChatThreadSummary>();
		for (ThreadState state : threads.values()) {
			ChatMessage last = lastMessage(state.threadId);
			out.add(new ChatThreadSummary(
					state.threadId,
					state.title,
					last == null ? "" : last.getBody(),
					last == null ? 0L : last.getWhenMs(),
					state.unreadCount));
		}
		Collections.sort(out, new Comparator<ChatThreadSummary>() {
			public int compare(ChatThreadSummary a, ChatThreadSummary b) {
				if (a.getLastWhenMs() < b.getLastWhenMs()) {
					return 1;
				}
				if (a.getLastWhenMs() > b.getLastWhenMs()) {
					return -1;
				}
				return a.getThreadId().compareTo(b.getThreadId());
			}
		});
		return out;
	}

	List<ChatMessage> messages(String threadId, int limit) {
		return messages(threadId, limit, null, null, null);
	}

	List<ChatMessage> messages(String threadId, int limit, String query,
			Long sinceMsInclusive, Long untilMsExclusive) {
		String id = threadId == null ? "" : threadId;
		String needle = query == null ? "" : query.trim().toLowerCase(Locale.US);
		ArrayList<ChatMessage> matched = new ArrayList<ChatMessage>();
		for (int i = 0; i < messages.size(); i++) {
			ChatMessage m = messages.get(i);
			if (!id.equals(m.getThreadId())) {
				continue;
			}
			if (sinceMsInclusive != null && m.getWhenMs() < sinceMsInclusive.longValue()) {
				continue;
			}
			if (untilMsExclusive != null && m.getWhenMs() >= untilMsExclusive.longValue()) {
				continue;
			}
			if (needle.length() > 0) {
				String body = m.getBody() == null ? "" : m.getBody().toLowerCase(Locale.US);
				String title = m.getTitle() == null ? "" : m.getTitle().toLowerCase(Locale.US);
				if (body.indexOf(needle) < 0 && title.indexOf(needle) < 0) {
					continue;
				}
			}
			matched.add(m);
		}
		if (limit < 0 || matched.size() <= limit) {
			return matched;
		}
		return new ArrayList<ChatMessage>(
				matched.subList(matched.size() - limit, matched.size()));
	}

	List<ChatMessage> search(String query, Long sinceMsInclusive, Long untilMsExclusive) {
		String needle = query == null ? "" : query.toLowerCase(Locale.US);
		ArrayList<ChatMessage> out = new ArrayList<ChatMessage>();
		for (int i = 0; i < messages.size(); i++) {
			ChatMessage m = messages.get(i);
			if (sinceMsInclusive != null && m.getWhenMs() < sinceMsInclusive.longValue()) {
				continue;
			}
			if (untilMsExclusive != null && m.getWhenMs() >= untilMsExclusive.longValue()) {
				continue;
			}
			if (needle.length() > 0) {
				String body = m.getBody().toLowerCase(Locale.US);
				String title = m.getTitle().toLowerCase(Locale.US);
				String tid = m.getThreadId().toLowerCase(Locale.US);
				if (body.indexOf(needle) < 0 && title.indexOf(needle) < 0
						&& tid.indexOf(needle) < 0) {
					continue;
				}
			}
			out.add(m);
		}
		return out;
	}

	void setReplyTemplate(String threadId, String template) {
		String id = threadId == null ? "" : threadId;
		ThreadState state = threads.get(id);
		if (state == null) {
			state = new ThreadState(id, id, template == null ? "" : template, 0);
			threads.put(id, state);
			return;
		}
		state.replyTemplate = template == null ? "" : template;
	}

	String notifyBucket(String threadId) {
		ThreadState state = threads.get(threadId == null ? "" : threadId);
		if (state == null) {
			return ChatNotifyBucket.OTHER;
		}
		return ChatNotifyBucket.coerce(state.notifyBucket);
	}

	void setNotifyBucket(String threadId, String bucket) {
		String id = threadId == null ? "" : threadId;
		ThreadState state = threads.get(id);
		if (state == null) {
			state = new ThreadState(id, id, "", 0);
			threads.put(id, state);
		}
		state.notifyBucket = ChatNotifyBucket.coerce(bucket);
	}

	boolean markSeen(String threadId) {
		ThreadState state = threads.get(threadId == null ? "" : threadId);
		if (state == null || state.unreadCount == 0) {
			return false;
		}
		state.unreadCount = 0;
		return true;
	}

	/**
	 * Drop the thread and every message with that id. Does not create a thread.
	 *
	 * @return true when a thread existed
	 */
	boolean deleteThread(String threadId) {
		String id = threadId == null ? "" : threadId;
		boolean existed = threads.containsKey(id);
		threads.remove(id);
		for (int i = messages.size() - 1; i >= 0; i--) {
			if (id.equals(messages.get(i).getThreadId())) {
				messages.remove(i);
			}
		}
		return existed;
	}

	int unreadCount(String threadId) {
		ThreadState state = threads.get(threadId == null ? "" : threadId);
		return state == null ? 0 : state.unreadCount;
	}

	String threadTitle(String threadId) {
		ThreadState state = threads.get(threadId == null ? "" : threadId);
		if (state == null || state.title == null) {
			return "";
		}
		return state.title;
	}

	/**
	 * Find a thread without creating one. Exact id, then case-insensitive id,
	 * then case-insensitive exact title, then a unique title substring.
	 * Ambiguous contains is not a match.
	 */
	String resolveThreadId(String query) {
		if (query == null) {
			return null;
		}
		String q = query.trim();
		if (q.length() == 0) {
			return null;
		}
		if (threads.containsKey(q)) {
			return q;
		}
		String lower = q.toLowerCase(Locale.US);
		for (String id : threads.keySet()) {
			if (id.toLowerCase(Locale.US).equals(lower)) {
				return id;
			}
		}
		for (ThreadState state : threads.values()) {
			if (state.title != null
					&& state.title.toLowerCase(Locale.US).equals(lower)) {
				return state.threadId;
			}
		}
		String unique = null;
		int hits = 0;
		for (ThreadState state : threads.values()) {
			if (state.title != null
					&& state.title.toLowerCase(Locale.US).indexOf(lower) >= 0) {
				hits++;
				unique = state.threadId;
				if (hits > 1) {
					return null;
				}
			}
		}
		return unique;
	}

	String replyTemplate(String threadId) {
		ThreadState state = threads.get(threadId == null ? "" : threadId);
		if (state == null || state.replyTemplate == null) {
			return "";
		}
		return state.replyTemplate;
	}

	int totalUnread() {
		int n = 0;
		for (ThreadState state : threads.values()) {
			n += state.unreadCount;
		}
		return n;
	}

	int messageCount() {
		return messages.size();
	}

	byte[] toJsonBytes() {
		try {
			JSONObject root = new JSONObject();
			String rootNeedle = defaultMineNeedle == null ? "" : defaultMineNeedle;
			if (rootNeedle.length() == 0) {
				for (ThreadState state : threads.values()) {
					if (state.mineNeedle != null && state.mineNeedle.length() > 0) {
						rootNeedle = state.mineNeedle;
						break;
					}
				}
			}
			root.put("mineNeedle", rootNeedle);
			root.put("mineColor", mineColor);
			root.put("otherColor", otherColor);
			root.put("maxMessages", maxMessages);
			JSONArray threadArr = new JSONArray();
			for (ThreadState state : threads.values()) {
				JSONObject t = new JSONObject();
				t.put("threadId", state.threadId);
				t.put("title", state.title);
				t.put("replyTemplate", state.replyTemplate);
				t.put("unreadCount", state.unreadCount);
				t.put("mineNeedle", state.mineNeedle == null ? "" : state.mineNeedle);
				t.put("mineColor", state.mineColor);
				t.put("notifyBucket", ChatNotifyBucket.coerce(state.notifyBucket));
				threadArr.put(t);
			}
			JSONArray messageArr = new JSONArray();
			for (int i = 0; i < messages.size(); i++) {
				ChatMessage m = messages.get(i);
				JSONObject o = new JSONObject();
				o.put("threadId", m.getThreadId());
				o.put("title", m.getTitle());
				o.put("body", m.getBody());
				o.put("whenMs", m.getWhenMs());
				o.put("mine", m.isMine());
				messageArr.put(o);
			}
			root.put("threads", threadArr);
			root.put("messages", messageArr);
			return root.toString().getBytes(UTF8);
		} catch (JSONException e) {
			return "{\"threads\":[],\"messages\":[]}".getBytes(UTF8);
		}
	}

	static ChatInbox fromJsonBytes(byte[] json) {
		ChatInbox inbox = new ChatInbox();
		if (json == null || json.length == 0) {
			return inbox;
		}
		String raw;
		try {
			raw = new String(json, UTF8);
		} catch (Exception e) {
			return inbox;
		}
		raw = raw.trim();
		if (raw.length() == 0) {
			return inbox;
		}
		try {
			JSONObject root = new JSONObject(raw);
			String rootNeedle = root.optString("mineNeedle", "");
			if ("null".equals(rootNeedle)) {
				rootNeedle = "";
			}
			inbox.defaultMineNeedle = rootNeedle;
			inbox.mineColor = root.optInt("mineColor", DEFAULT_MINE_COLOR);
			inbox.otherColor = root.optInt("otherColor", DEFAULT_OTHER_COLOR);
			HashSet<String> inheritRoot = new HashSet<String>();
			JSONArray threadArr = root.optJSONArray("threads");
			if (threadArr != null) {
				for (int i = 0; i < threadArr.length(); i++) {
					JSONObject t = threadArr.optJSONObject(i);
					if (t == null) {
						continue;
					}
					String id = t.optString("threadId", "");
					ThreadState state = new ThreadState(
							id,
							t.optString("title", id),
							t.optString("replyTemplate", ""),
							t.optInt("unreadCount", 0));
					if (t.has("mineNeedle")) {
						state.setMineNeedle(t.optString("mineNeedle", ""));
					} else {
						inheritRoot.add(id);
					}
					state.mineColor = t.optInt("mineColor", 0);
					state.notifyBucket = ChatNotifyBucket.coerce(
							t.optString("notifyBucket", ChatNotifyBucket.OTHER));
					inbox.threads.put(id, state);
					if (inbox.defaultMineNeedle.length() == 0
							&& state.mineNeedle != null
							&& state.mineNeedle.length() > 0) {
						inbox.defaultMineNeedle = state.mineNeedle;
					}
				}
			}
			JSONArray messageArr = root.optJSONArray("messages");
			if (messageArr != null) {
				for (int i = 0; i < messageArr.length(); i++) {
					JSONObject o = messageArr.optJSONObject(i);
					if (o == null) {
						continue;
					}
					String id = o.optString("threadId", "");
					String title = o.optString("title", id);
					boolean mine = readMineFlag(o, title);
					inbox.messages.add(new ChatMessage(
							id,
							title,
							o.optString("body", ""),
							o.optLong("whenMs", 0L),
							mine));
					if (!inbox.threads.containsKey(id)) {
						inbox.threads.put(id, new ThreadState(id, title, "", 0));
						inheritRoot.add(id);
					}
				}
			}
			if (root.has("maxMessages")) {
				inbox.setMaxMessages(root.optInt("maxMessages",
						DEFAULT_MAX_MESSAGES));
			} else {
				inbox.setMaxMessages(0);
			}
			inbox.capOldest();
			inbox.pruneThreadsWithoutMessages();
			// Old files stored one world-level needle and no per-thread key.
			// Threads that saved an empty My lines keep it (has("mineNeedle")).
			if (rootNeedle.length() > 0) {
				for (String id : inheritRoot) {
					ThreadState state = inbox.threads.get(id);
					if (state != null
							&& (state.mineNeedle == null || state.mineNeedle.length() == 0)) {
						state.setMineNeedle(rootNeedle);
					}
				}
			}
			inbox.restampMineFlags();
			return inbox;
		} catch (JSONException e) {
			return new ChatInbox();
		} catch (Exception e) {
			return new ChatInbox();
		}
	}

	private static boolean readMineFlag(JSONObject o, String title) {
		if (o.optBoolean("mine", false)) {
			return true;
		}
		String raw = o.optString("mine", "");
		if ("true".equalsIgnoreCase(raw) || "1".equals(raw)) {
			return true;
		}
		return "You".equals(title);
	}

	private ChatMessage lastMessage(String threadId) {
		for (int i = messages.size() - 1; i >= 0; i--) {
			ChatMessage m = messages.get(i);
			if (threadId.equals(m.getThreadId())) {
				return m;
			}
		}
		return null;
	}
}
