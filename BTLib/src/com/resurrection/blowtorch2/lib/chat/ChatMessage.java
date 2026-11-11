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
	/** ANSI SGR for {@link #body}, or empty when the line had no color codes. */
	private final String colored;

	public ChatMessage(String threadId, String title, String body, long whenMs) {
		this(threadId, title, body, whenMs, false);
	}

	public ChatMessage(String threadId, String title, String body, long whenMs,
			boolean mine) {
		this(threadId, title, body, whenMs, mine, "");
	}

	public ChatMessage(String threadId, String title, String body, long whenMs,
			boolean mine, String colored) {
		this.threadId = threadId == null ? "" : threadId;
		this.title = title == null ? "" : title;
		this.body = body == null ? "" : body;
		this.whenMs = whenMs;
		this.mine = mine;
		this.colored = colored == null ? "" : colored;
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

	/** Same characters as {@link #getBody()}, with SGR codes, or empty. */
	public String getColored() {
		return colored;
	}
}
