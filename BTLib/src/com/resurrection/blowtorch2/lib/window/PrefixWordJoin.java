package com.resurrection.blowtorch2.lib.window;

/**
 * Line to send when the bar holds a prefix and the player picks a screen word.
 * Null means refuse (empty bar or empty word). No trailing space: this is the
 * wire string, not {@link InputWordInsert} which leaves a space for the next
 * tap in the bar.
 */
public final class PrefixWordJoin {

	private PrefixWordJoin() {
	}

	public static String sendLine(final String prefix, final String word) {
		String p = prefix == null ? "" : prefix.trim();
		String w = word == null ? "" : word.trim();
		if (p.length() == 0 || w.length() == 0) {
			return null;
		}
		return p + " " + w;
	}
}
