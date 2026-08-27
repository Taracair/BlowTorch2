package com.resurrection.blowtorch2.lib.chat;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * In-memory inbox plus JSON encode/decode and the 4000-message cap.
 *
 * <p>Package-visible so {@link ChatStoreTest} can exercise append/list/search/cap
 * without Android {@code Context} or {@code AtomicFiles}. {@link ChatStore} is
 * the file + notify wrapper around this.
 */
final class ChatInbox {

	static final int MAX_MESSAGES = 4000;
	static final int DEFAULT_MINE_COLOR = 0xFF1B6B66;
	static final int DEFAULT_OTHER_COLOR = 0xFF333333;
	static final long ABSORB_MINE_MS = 8000L;
	private static final Charset UTF8 = Charset.forName("UTF-8");

	private static final class ThreadState {
		String threadId;
		String title;
		String replyTemplate;
		int unreadCount;

		ThreadState(String threadId, String title, String replyTemplate, int unreadCount) {
			this.threadId = threadId == null ? "" : threadId;
			this.title = title == null ? "" : title;
			this.replyTemplate = replyTemplate == null ? "" : replyTemplate;
			this.unreadCount = unreadCount < 0 ? 0 : unreadCount;
		}
	}

	private final LinkedHashMap<String, ThreadState> threads =
			new LinkedHashMap<String, ThreadState>();
	private final ArrayList<ChatMessage> messages = new ArrayList<ChatMessage>();
	private String mineNeedle = "";
	private int mineColor = DEFAULT_MINE_COLOR;
	private int otherColor = DEFAULT_OTHER_COLOR;

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

	boolean bodyLooksMine(String body) {
		String needle = mineNeedle == null ? "" : mineNeedle.trim();
		if (needle.length() < 2 || body == null) {
			return false;
		}
		return body.toLowerCase(Locale.US).indexOf(needle.toLowerCase(Locale.US)) >= 0;
	}

	String mineNeedle() {
		return mineNeedle == null ? "" : mineNeedle;
	}

	void setMineNeedle(String needle) {
		mineNeedle = needle == null ? "" : needle;
	}

	int mineColor() {
		return mineColor;
	}

	void setMineColor(int argb) {
		mineColor = argb;
	}

	int otherColor() {
		return otherColor;
	}

	void setOtherColor(int argb) {
		otherColor = argb;
	}

	void capOldest() {
		boolean dropped = false;
		while (messages.size() > MAX_MESSAGES) {
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
		String id = threadId == null ? "" : threadId;
		ArrayList<ChatMessage> matched = new ArrayList<ChatMessage>();
		for (int i = 0; i < messages.size(); i++) {
			ChatMessage m = messages.get(i);
			if (id.equals(m.getThreadId())) {
				matched.add(m);
			}
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

	boolean markSeen(String threadId) {
		ThreadState state = threads.get(threadId == null ? "" : threadId);
		if (state == null || state.unreadCount == 0) {
			return false;
		}
		state.unreadCount = 0;
		return true;
	}

	String replyTemplate(String threadId) {
		ThreadState state = threads.get(threadId == null ? "" : threadId);
		if (state == null || state.replyTemplate == null) {
			return "";
		}
		return state.replyTemplate;
	}

	int messageCount() {
		return messages.size();
	}

	byte[] toJsonBytes() {
		try {
			JSONObject root = new JSONObject();
			root.put("mineNeedle", mineNeedle == null ? "" : mineNeedle);
			root.put("mineColor", mineColor);
			root.put("otherColor", otherColor);
			JSONArray threadArr = new JSONArray();
			for (ThreadState state : threads.values()) {
				JSONObject t = new JSONObject();
				t.put("threadId", state.threadId);
				t.put("title", state.title);
				t.put("replyTemplate", state.replyTemplate);
				t.put("unreadCount", state.unreadCount);
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
			inbox.mineNeedle = root.optString("mineNeedle", "");
			inbox.mineColor = root.optInt("mineColor", DEFAULT_MINE_COLOR);
			inbox.otherColor = root.optInt("otherColor", DEFAULT_OTHER_COLOR);
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
					inbox.threads.put(id, state);
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
					inbox.messages.add(new ChatMessage(
							id,
							title,
							o.optString("body", ""),
							o.optLong("whenMs", 0L),
							o.optBoolean("mine", false)));
					if (!inbox.threads.containsKey(id)) {
						inbox.threads.put(id, new ThreadState(id, title, "", 0));
					}
				}
			}
			inbox.capOldest();
			inbox.pruneThreadsWithoutMessages();
			return inbox;
		} catch (JSONException e) {
			return new ChatInbox();
		} catch (Exception e) {
			return new ChatInbox();
		}
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
