package com.resurrection.blowtorch2.lib.service.function;

import java.util.List;

import com.resurrection.blowtorch2.lib.service.CommandSemicolon;

/**
 * {@code .last} and {@code .Nlast}: which recent command to send again.
 * {@code .last input} and {@code .Nlast input} name the same slot and only
 * fill the input bar.
 *
 * <p>N is 1-based from the newest. {@code .last} is 1. A line that is not this
 * form ({@code last}, {@code 2last}, {@code .last 3}, {@code last input},
 * {@code .lastinput}) is {@link #NOT_RECALL}. The form with N outside
 * 1..history max ({@code .0last}, {@code .99999last}) is {@link #BAD_INDEX}:
 * local, not a line for the world. One space before {@code input}.
 */
public final class LastRecall {
	/** Not a recall line. The world may still see it. */
	public static final int NOT_RECALL = -1;
	/** Recall form, but N is not a usable index. */
	public static final int BAD_INDEX = -2;

	/** {@code .Nlast input}: put that command in the bar, do not send it. */
	public static final class Fill {
		public final int index;

		public Fill(final int index) {
			this.index = index;
		}
	}

	private static final int KEEPER_MIN = 10;
	private static final int KEEPER_MAX = 100;

	private LastRecall() {
	}

	/**
	 * @param line the segment, newline already removed
	 * @param maxInclusive input-history size before the 10–100 clamp
	 * @return 1-based N, {@link #NOT_RECALL}, or {@link #BAD_INDEX}
	 */
	public static int parse(final String line, final int maxInclusive) {
		String s = trimTrailing(line);
		if (s.length() < 5 || s.charAt(0) != '.') {
			return NOT_RECALL;
		}
		if (!s.regionMatches(s.length() - 4, "last", 0, 4)) {
			return NOT_RECALL;
		}
		int stop = s.length() - 4;
		if (stop == 1) {
			return 1;
		}
		int n = 0;
		for (int i = 1; i < stop; i++) {
			char c = s.charAt(i);
			if (c < '0' || c > '9') {
				return NOT_RECALL;
			}
			int digit = c - '0';
			if (n > (Integer.MAX_VALUE - digit) / 10) {
				return BAD_INDEX;
			}
			n = n * 10 + digit;
		}
		if (n < 1) {
			return BAD_INDEX;
		}
		if (n > cap(maxInclusive)) {
			return BAD_INDEX;
		}
		return n;
	}

	/**
	 * {@code .last input} / {@code .Nlast input}: the same index as
	 * {@link #parse}, for the bar instead of the socket. Exactly one space
	 * before {@code input}. {@code .lastinput} and {@code .last  input} are
	 * {@link #NOT_RECALL}.
	 */
	public static int parseFill(final String line, final int maxInclusive) {
		String s = trimTrailing(line);
		if (s.length() < 11 || !s.endsWith("input")) {
			return NOT_RECALL;
		}
		int inputAt = s.length() - 5;
		if (s.charAt(inputAt - 1) != ' ') {
			return NOT_RECALL;
		}
		if (inputAt >= 2 && s.charAt(inputAt - 2) == ' ') {
			return NOT_RECALL;
		}
		return parse(s.substring(0, inputAt - 1), maxInclusive);
	}

	/**
	 * {@code .last} plus more text ({@code .last 3}). Not an index, and not a
	 * game line: the command reports usage and sends nothing.
	 * {@code .last input} is {@link #parseFill}, not this.
	 */
	public static boolean isMisused(final String line) {
		if (parseFill(line, KEEPER_MAX) != NOT_RECALL) {
			return false;
		}
		String s = trimTrailing(line);
		if (s.length() < 6 || !s.startsWith(".last")) {
			return false;
		}
		char c = s.charAt(5);
		return c == ' ' || c == '\t';
	}

	/**
	 * True when this typed line, or one {@code ;} segment of it, is a recall
	 * (including a bad index or {@code .last 3}). Those are not history entries:
	 * storing them would make the next {@code .last} repeat itself.
	 */
	public static boolean embeds(final String line, final int maxInclusive) {
		if (line == null || line.length() == 0) {
			return false;
		}
		if (isRecall(line, maxInclusive)) {
			return true;
		}
		if (line.indexOf(';') < 0) {
			return false;
		}
		List<String> parts = CommandSemicolon.split(line);
		for (int i = 0; i < parts.size(); i++) {
			if (isRecall(parts.get(i), maxInclusive)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isRecall(final String line, final int maxInclusive) {
		return parse(line, maxInclusive) != NOT_RECALL
				|| parseFill(line, maxInclusive) != NOT_RECALL
				|| isMisused(line);
	}

	private static int cap(final int maxInclusive) {
		return Math.max(KEEPER_MIN, Math.min(KEEPER_MAX, maxInclusive));
	}

	private static String trimTrailing(final String line) {
		if (line == null) {
			return "";
		}
		int end = line.length();
		while (end > 0) {
			char c = line.charAt(end - 1);
			if (c != ' ' && c != '\t' && c != '\r' && c != '\n') {
				break;
			}
			end--;
		}
		return end == line.length() ? line : line.substring(0, end);
	}
}
