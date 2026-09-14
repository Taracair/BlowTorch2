package com.resurrection.blowtorch2.lib.window;

/**
 * Line to send when the bar (or a pad tile) holds a prefix and the player
 * picks a screen word. Null means refuse (empty / whitespace / a client
 * {@code .} command, or empty word). No trailing space: this is the wire
 * string, not {@link InputWordInsert} which leaves a space for the next tap
 * in the bar.
 *
 * <p>{@code $1}, {@code $0} and {@code $word} are the picked token
 * ({@code fix $1 helmet} + {@code iron} → {@code fix iron helmet}). With no
 * slot, the word is appended ({@code fix} + {@code helmet} → {@code fix helmet}).
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
		if (hasSlot(p)) {
			return fill(p, w);
		}
		return p + " " + w;
	}

	/** {@code $1} / {@code $0} (not {@code $10}) or {@code $word}. */
	static boolean hasSlot(final String p) {
		if (p == null) {
			return false;
		}
		for (int i = 0; i < p.length(); i++) {
			if (p.charAt(i) != '$') {
				continue;
			}
			if (p.startsWith("$word", i)) {
				return true;
			}
			if (i + 1 < p.length()) {
				char n = p.charAt(i + 1);
				if ((n == '0' || n == '1') && !digitAt(p, i + 2)) {
					return true;
				}
			}
		}
		return false;
	}

	private static String fill(final String command, final String word) {
		StringBuilder out = new StringBuilder(command.length() + word.length());
		for (int i = 0; i < command.length(); i++) {
			char ch = command.charAt(i);
			if (ch != '$' || i + 1 >= command.length()) {
				out.append(ch);
				continue;
			}
			if (command.startsWith("$word", i)) {
				out.append(word);
				i += "$word".length() - 1;
				continue;
			}
			char next = command.charAt(i + 1);
			if ((next == '0' || next == '1') && !digitAt(command, i + 2)) {
				out.append(word);
				i++;
				continue;
			}
			out.append(ch);
		}
		return out.toString();
	}

	private static boolean digitAt(final String s, final int i) {
		return i < s.length() && s.charAt(i) >= '0' && s.charAt(i) <= '9';
	}
}
