package com.resurrection.blowtorch2.lib.trigger;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Second gate on a trigger: the same line as the Pattern match must also
 * contain this text. Empty needle is a pass. Android-free.
 */
public final class AlsoContainsGate {

	private AlsoContainsGate() {
	}

	/**
	 * @param chunk ANSI-stripped cascade text
	 * @param matchStart inclusive index of the Pattern hit in {@code chunk}
	 */
	public static boolean passes(final CharSequence chunk, final int matchStart,
			final String needle, final boolean literal) {
		if (needle == null || needle.length() == 0) {
			return true;
		}
		String line = lineAt(chunk, matchStart);
		if (literal) {
			return line.contains(needle);
		}
		try {
			return Pattern.compile(needle).matcher(line).find();
		} catch (PatternSyntaxException bad) {
			return false;
		}
	}

	/** The {@code \\n}-delimited line that contains {@code index}. */
	public static String lineAt(final CharSequence chunk, final int index) {
		if (chunk == null || chunk.length() == 0) {
			return "";
		}
		int n = chunk.length();
		int i = index;
		if (i < 0) {
			i = 0;
		}
		if (i > n) {
			i = n;
		}
		int from = i;
		while (from > 0 && chunk.charAt(from - 1) != '\n') {
			from--;
		}
		int to = i;
		while (to < n && chunk.charAt(to) != '\n') {
			to++;
		}
		return chunk.subSequence(from, to).toString();
	}
}
