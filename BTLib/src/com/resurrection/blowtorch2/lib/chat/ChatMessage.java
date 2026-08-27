package com.resurrection.blowtorch2.lib.chat;

/**
 * One copied line in a world's chat inbox.
 *
 * <p>Plain Java so {@link ChatInbox} can encode it without Android. The MUD
 * line this was copied from stays in the game window; this is the copy.
 */
public final class ChatMessage {

	private final String threadId;
	private final String title;
	private final String body;
	private final long whenMs;

	public ChatMessage(String threadId, String title, String body, long whenMs) {
		this.threadId = threadId == null ? "" : threadId;
		this.title = title == null ? "" : title;
		this.body = body == null ? "" : body;
		this.whenMs = whenMs;
	}

	public String getThreadId() {
		return threadId;
	}

	public String getTitle() {
		return title;
	}

	public String getBody() {
		return body;
	}

	public long getWhenMs() {
		return whenMs;
	}
}
