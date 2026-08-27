package com.resurrection.blowtorch2.lib.chat;

/**
 * One conversation in the per-world inbox list.
 *
 * <p>Plain Java: {@link ChatInbox} builds these from the JSON file without
 * Android. {@code unreadCount} grows on each append until a later reader marks
 * the thread seen.
 */
public final class ChatThreadSummary {

	private final String threadId;
	private final String title;
	private final String lastBody;
	private final long lastWhenMs;
	private final int unreadCount;

	public ChatThreadSummary(String threadId, String title, String lastBody,
			long lastWhenMs, int unreadCount) {
		this.threadId = threadId == null ? "" : threadId;
		this.title = title == null ? "" : title;
		this.lastBody = lastBody == null ? "" : lastBody;
		this.lastWhenMs = lastWhenMs;
		this.unreadCount = unreadCount < 0 ? 0 : unreadCount;
	}

	public String getThreadId() {
		return threadId;
	}

	public String getTitle() {
		return title;
	}

	public String getLastBody() {
		return lastBody;
	}

	public long getLastWhenMs() {
		return lastWhenMs;
	}

	public int getUnreadCount() {
		return unreadCount;
	}
}
