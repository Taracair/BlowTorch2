package com.resurrection.blowtorch2.lib.chat;

/**
 * One copied line in a world's chat inbox.
 *
 * <p>Plain Java so {@link ChatInbox} can encode it without Android. The MUD
 * line this was copied from stays in the game window; this is the copy.
 * {@code mine} is the player's own line (Send, a Mine trigger, or the
 * configured name needle).
 */
public final class ChatMessage {

	private final String threadId;
	private final String title;
	private final String body;
	private final long whenMs;
	private final boolean mine;

	public ChatMessage(String threadId, String title, String body, long whenMs) {
		this(threadId, title, body, whenMs, false);
	}

	public ChatMessage(String threadId, String title, String body, long whenMs,
			boolean mine) {
		this.threadId = threadId == null ? "" : threadId;
		this.title = title == null ? "" : title;
		this.body = body == null ? "" : body;
		this.whenMs = whenMs;
		this.mine = mine;
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

	public boolean isMine() {
		return mine;
	}
}
