package com.resurrection.blowtorch2.lib.window;

/**
 * Line to send when the bar holds a prefix and the player picks a screen word.
 * Null means refuse (empty / whitespace / a client {@code .} command, or empty
 * word). No trailing space: this is the wire string, not
 * {@link InputWordInsert} which leaves a space for the next tap in the bar.
 */
public final class PrefixWordJoin {

	private PrefixWordJoin() {
	}

	/** False for empty, whitespace, or a client {@code .} command (Keep Last
	 * leaves {@code .pick} in the bar; that is not a game prefix). */
	public static boolean usablePrefix(final String prefix) {
		String p = prefix == null ? "" : prefix.trim();
		return p.length() > 0 && !p.startsWith(".");
	}

	public static String sendLine(final String prefix, final String word) {
		if (!usablePrefix(prefix)) {
			return null;
		}
		String p = prefix.trim();
		String w = word == null ? "" : word.trim();
		if (w.length() == 0) {
			return null;
		}
		return p + " " + w;
	}
}
