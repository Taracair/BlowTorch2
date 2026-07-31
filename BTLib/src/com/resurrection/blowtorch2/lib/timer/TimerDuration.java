package com.resurrection.blowtorch2.lib.timer;

/**
 * Timers are stored as a total number of seconds — that is what the parser writes and what
 * the scheduler multiplies by 1000. This is only the hours/minutes/seconds face put on that
 * number for the editor and the list, so nothing here may change how a timer is persisted.
 *
 * Pure int maths on purpose: it keeps the round trip testable on the JVM.
 */
public final class TimerDuration {

	public static final int SECONDS_PER_MINUTE = 60;
	public static final int SECONDS_PER_HOUR = 3600;

	private TimerDuration() {
	}

	public static int hoursOf(int totalSeconds) {
		return clamp(totalSeconds) / SECONDS_PER_HOUR;
	}

	public static int minutesOf(int totalSeconds) {
		return (clamp(totalSeconds) % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE;
	}

	public static int secondsOf(int totalSeconds) {
		return clamp(totalSeconds) % SECONDS_PER_MINUTE;
	}

	/**
	 * Fields are added rather than range-checked, so 90 in the seconds box means the same
	 * as 1 minute 30 — typing the number you were thinking of is the point of the change.
	 */
	public static int toSeconds(int hours, int minutes, int seconds) {
		long total = (long) clamp(hours) * SECONDS_PER_HOUR
				+ (long) clamp(minutes) * SECONDS_PER_MINUTE
				+ (long) clamp(seconds);
		return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
	}

	/** Blank means zero: an empty hours box is not an error, it is "no hours". */
	public static int parseField(String text) {
		if (text == null) {
			return 0;
		}
		String trimmed = text.trim();
		if (trimmed.length() == 0) {
			return 0;
		}
		try {
			return clamp(Integer.parseInt(trimmed));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	/** Short form for list rows: {@code 1h 30m}, {@code 2m 05s}, {@code 45s}. */
	public static String format(int totalSeconds) {
		int t = clamp(totalSeconds);
		int h = hoursOf(t);
		int m = minutesOf(t);
		int s = secondsOf(t);
		if (h > 0) {
			return m > 0 ? h + "h " + m + "m" : h + "h";
		}
		if (m > 0) {
			return s > 0 ? m + "m " + two(s) + "s" : m + "m";
		}
		return s + "s";
	}

	private static String two(int value) {
		return value < 10 ? "0" + value : Integer.toString(value);
	}

	private static int clamp(int value) {
		return value < 0 ? 0 : value;
	}
}
