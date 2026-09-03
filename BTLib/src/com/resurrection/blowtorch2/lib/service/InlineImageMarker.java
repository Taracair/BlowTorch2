package com.resurrection.blowtorch2.lib.service;

/**
 * Inline picture marker: {@code ESC ] BTIMG ; key ; lines BEL}.
 * OSC so {@code TextTree} skips an unknown marker instead of printing it.
 * The key is generated here (not a server id or URL) and names a UI-side store
 * entry sent separately via {@link FrameEvent#OP_INLINE}.
 */
public final class InlineImageMarker {

	private static final char ESC = 0x1B;
	private static final char OSC = ']';
	private static final char BEL = 0x07;

	/** What a marker's payload starts with, so other OSC traffic is left alone. */
	public static final String PREFIX = "BTIMG;";

	/** Fewest lines a picture may take. Below this there is nothing to look at. */
	public static final int MIN_LINES = 2;

	/**
	 * Most lines a picture may take.
	 *
	 * <p>A picture taller than a screen would push the text that explains it out
	 * of sight, and the height is a number a player types into Options.
	 */
	public static final int MAX_LINES = 40;

	/** Used when the setting is missing or nonsense. */
	public static final int DEFAULT_LINES = 12;

	private InlineImageMarker() {
	}

	/** Force a line count into range. */
	public static int clampLines(final int lines) {
		if (lines < MIN_LINES) {
			return MIN_LINES;
		}
		if (lines > MAX_LINES) {
			return MAX_LINES;
		}
		return lines;
	}

	/**
	 * The bytes that place a picture in the text.
	 *
	 * <p>The marker sits on a line of its own, and the newlines after it are the
	 * space the picture is drawn over: the marker's own line plus
	 * {@code lines - 1} blank ones. The picture does not change the height of a
	 * line, it covers whole ones — that is what keeps scrolling, selection and
	 * tap targets working exactly as they did, all of which assume every line is
	 * the same height.
	 *
	 * @param key From {@link #keyFor}. Must not contain {@code ;}.
	 * @param lines How many lines of text the picture covers.
	 */
	public static String encode(final String key, final int lines) {
		int n = clampLines(lines);
		StringBuilder sb = new StringBuilder(32 + n);
		sb.append('\n');
		sb.append(ESC).append(OSC).append(PREFIX).append(key).append(';').append(n).append(BEL);
		for (int i = 0; i < n; i++) {
			sb.append('\n');
		}
		return sb.toString();
	}

	/**
	 * A key of our own for a server's frame id.
	 *
	 * <p>The counter is what makes it unique: the same frame sending a new
	 * picture must not overwrite the one already sitting in the scrollback,
	 * because that one belongs to the room it was printed next to.
	 */
	public static String keyFor(final String frameId, final int counter) {
		StringBuilder sb = new StringBuilder(24);
		sb.append("btimg-").append(counter).append('-');
		String id = frameId == null ? "" : frameId;
		for (int i = 0; i < id.length() && sb.length() < 40; i++) {
			char c = id.charAt(i);
			if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
				sb.append(c);
			}
		}
		return sb.toString();
	}

	/** One marker's contents, as the buffer parser reads them back. */
	public static final class Parsed {
		public final String key;
		public final int lines;

		Parsed(final String key, final int lines) {
			this.key = key;
			this.lines = lines;
		}
	}

	/**
	 * Read an OSC payload, if it is one of ours.
	 *
	 * @param payload What sat between {@code ESC ]} and the terminator.
	 * @return The marker, or null for any other OSC sequence and for anything
	 *         malformed. A marker that cannot be read is one that draws nothing,
	 *         which is the right outcome — the blank lines are already there.
	 */
	public static Parsed parse(final String payload) {
		if (payload == null || !payload.startsWith(PREFIX)) {
			return null;
		}
		int sep = payload.indexOf(';', PREFIX.length());
		if (sep < 0) {
			return null;
		}
		String key = payload.substring(PREFIX.length(), sep);
		if (key.length() == 0) {
			return null;
		}
		int lines;
		try {
			lines = Integer.parseInt(payload.substring(sep + 1).trim());
		} catch (NumberFormatException e) {
			return null;
		}
		if (lines < MIN_LINES) {
			return null;
		}
		return new Parsed(key, clampLines(lines));
	}
}
