package com.resurrection.blowtorch2.lib.service;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code .wait 5s} / {@code #wait 5m10s}: pause the rest of this outbound
 * batch. Units may appear in any order. A bare number is seconds.
 */
public final class CommandWait {

	/** One hour, inclusive. Longer is refused rather than clamped. */
	public static final long MAX_MS = 60L * 60L * 1000L;

	public static final String USAGE =
			"Usage: .wait 5s | #wait 5m10s | .wait 1h | .wait 500ms\n"
					+ "       .wait stop   (or #wait 0) cancels a wait still running\n"
					+ "Units h, m, s, ms in any order. A bare number is seconds. Max 1h.\n"
					+ "Only the rest of this line waits (north;.wait 2s;south). "
					+ "The game still prints; other triggers still send.";

	public enum Kind {
		NOT_WAIT,
		DELAY,
		STOP,
		ERROR
	}

	public static final class Result {
		public final Kind kind;
		public final long delayMs;
		public final String message;

		Result(final Kind kind, final long delayMs, final String message) {
			this.kind = kind;
			this.delayMs = delayMs;
			this.message = message;
		}

		public static Result notWait() {
			return new Result(Kind.NOT_WAIT, 0L, null);
		}

		public static Result delay(final long delayMs) {
			return new Result(Kind.DELAY, delayMs, null);
		}

		public static Result stop() {
			return new Result(Kind.STOP, 0L, null);
		}

		public static Result error(final String message) {
			return new Result(Kind.ERROR, 0L, message);
		}
	}

	private static final Pattern PREFIX = Pattern.compile(
			"(?i)^[.#]wait(?:\\s+(.*))?$");

	/** {@code ms} before {@code s} so 500ms is not 500m + leftover s. */
	private static final Pattern UNIT = Pattern.compile(
			"(?i)(\\d+(?:\\.\\d+)?)(ms|h|m|s)");

	private static final Pattern BARE_NUMBER = Pattern.compile(
			"^\\d+(?:\\.\\d+)?$");

	private CommandWait() {
	}

	/**
	 * One semicolon segment, already split. {@code #wait 5s} and {@code .wait 5s}
	 * are waits; {@code #5 north} is not.
	 */
	public static Result parseSegment(final String segment) {
		if (segment == null) {
			return Result.notWait();
		}
		String trimmed = segment.trim();
		if (trimmed.length() == 0) {
			return Result.notWait();
		}
		Matcher prefix = PREFIX.matcher(trimmed);
		if (!prefix.matches()) {
			return Result.notWait();
		}
		String arg = prefix.group(1);
		return parseArgument(arg == null ? "" : arg);
	}

	/** Argument after {@code .wait} / {@code #wait}. */
	public static Result parseArgument(final String raw) {
		String arg = raw == null ? "" : raw.trim();
		if (arg.length() == 0) {
			return Result.error(USAGE);
		}
		if (arg.equalsIgnoreCase("stop")) {
			return Result.stop();
		}
		try {
			long ms = parseDurationMs(arg);
			if (ms == 0L) {
				return Result.stop();
			}
			if (ms > MAX_MS) {
				return Result.error("Wait refused: " + arg
						+ " is longer than 1h (the maximum).");
			}
			return Result.delay(ms);
		} catch (IllegalArgumentException bad) {
			return Result.error(bad.getMessage());
		}
	}

	/**
	 * {@code 5s5m} and {@code 5m 10s} and {@code 1.5s} and {@code 5} (seconds).
	 *
	 * @throws IllegalArgumentException leftover text, negative, or unreadable
	 */
	public static long parseDurationMs(final String spec) {
		if (spec == null) {
			throw new IllegalArgumentException("Wait needs a duration, e.g. 5s.");
		}
		String s = spec.trim();
		if (s.length() == 0) {
			throw new IllegalArgumentException("Wait needs a duration, e.g. 5s.");
		}
		if (BARE_NUMBER.matcher(s).matches()) {
			return fromUnit(parseNumber(s), 1000L, spec);
		}
		Matcher m = UNIT.matcher(s);
		long total = 0L;
		int consumed = 0;
		while (m.find()) {
			if (m.start() != consumed) {
				String gap = s.substring(consumed, m.start());
				if (!isOnlySpace(gap)) {
					throw leftover(spec);
				}
			}
			consumed = m.end();
			double n = parseNumber(m.group(1));
			String unit = m.group(2).toLowerCase(Locale.US);
			long unitMs;
			if ("h".equals(unit)) {
				unitMs = 3600000L;
			} else if ("m".equals(unit)) {
				unitMs = 60000L;
			} else if ("ms".equals(unit)) {
				unitMs = 1L;
			} else {
				unitMs = 1000L;
			}
			total = addMs(total, fromUnit(n, unitMs, spec));
		}
		if (consumed == 0) {
			throw leftover(spec);
		}
		if (consumed < s.length()) {
			String tail = s.substring(consumed);
			if (!isOnlySpace(tail)) {
				throw leftover(spec);
			}
		}
		return total;
	}

	/** Compact {@code 1h5m10s} / {@code 1s500ms} for the local [wait …] line. */
	public static String format(final long delayMs) {
		if (delayMs <= 0L) {
			return "0s";
		}
		long ms = delayMs;
		long h = ms / 3600000L;
		ms %= 3600000L;
		long m = ms / 60000L;
		ms %= 60000L;
		long s = ms / 1000L;
		long rem = ms % 1000L;
		StringBuilder sb = new StringBuilder();
		if (h > 0L) {
			sb.append(h).append('h');
		}
		if (m > 0L) {
			sb.append(m).append('m');
		}
		if (s > 0L) {
			sb.append(s).append('s');
		}
		if (rem > 0L) {
			sb.append(rem).append("ms");
		}
		if (sb.length() == 0) {
			sb.append("0s");
		}
		return sb.toString();
	}

	private static long fromUnit(final double n, final long unitMs, final String spec) {
		if (n < 0.0d || Double.isNaN(n) || Double.isInfinite(n)) {
			throw leftover(spec);
		}
		double add = n * (double) unitMs;
		if (add > (double) MAX_MS + 1.0d) {
			throw new IllegalArgumentException("Wait refused: " + spec
					+ " is longer than 1h (the maximum).");
		}
		return Math.round(add);
	}

	private static long addMs(final long a, final long b) {
		if (b < 0L || a > Long.MAX_VALUE - b) {
			throw new IllegalArgumentException("Wait refused: duration is longer than 1h (the maximum).");
		}
		return a + b;
	}

	private static double parseNumber(final String raw) {
		try {
			return Double.parseDouble(raw);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Wait needs a duration, e.g. 5s.");
		}
	}

	private static boolean isOnlySpace(final String s) {
		for (int i = 0; i < s.length(); i++) {
			if (!Character.isWhitespace(s.charAt(i))) {
				return false;
			}
		}
		return true;
	}

	private static IllegalArgumentException leftover(final String spec) {
		return new IllegalArgumentException(
				"Wait did not understand \"" + spec + "\".\n" + USAGE);
	}
}
