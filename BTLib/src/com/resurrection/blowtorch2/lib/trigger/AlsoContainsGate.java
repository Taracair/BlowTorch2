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

	/** Inclusive start of the {@code \\n}-delimited line that contains {@code index}. */
	public static int lineStart(final CharSequence chunk, final int index) {
		if (chunk == null || chunk.length() == 0) {
			return 0;
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
		return from;
	}

	/** Exclusive end of that line (index of {@code \\n}, or {@code chunk.length()}). */
	public static int lineEnd(final CharSequence chunk, final int index) {
		if (chunk == null || chunk.length() == 0) {
			return 0;
		}
		int n = chunk.length();
		int i = index;
		if (i < 0) {
			i = 0;
		}
		if (i > n) {
			i = n;
		}
		int to = i;
		while (to < n && chunk.charAt(to) != '\n') {
			to++;
		}
		return to;
	}

	/** The {@code \\n}-delimited line that contains {@code index}. */
	public static String lineAt(final CharSequence chunk, final int index) {
		if (chunk == null || chunk.length() == 0) {
			return "";
		}
		return chunk.subSequence(lineStart(chunk, index), lineEnd(chunk, index)).toString();
	}
}
