package com.resurrection.blowtorch2.lib.util;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Day-file names and markers for session logs. No Android.
 *
 * <p>New files are {@code {sanitized}_{yyyy-MM-dd}.txt}. Legacy leftovers
 * {@code {sanitized}_{yyyy-MM-dd_HH-mm-ss}.txt} still parse.
 */
public final class SessionLogDay {

	/**
	 * World prefix, then a calendar day, optionally a legacy second stamp.
	 * Greedy {@code (.+)_} would otherwise treat {@code foo_bar_2026-01-01.txt}
	 * as world {@code foo} when asking for {@code foo}.
	 */
	private static final Pattern FILE_NAME = Pattern.compile(
			"^(.+)_(\\d{4}-\\d{2}-\\d{2})(?:_(\\d{2}-\\d{2}-\\d{2}))?\\.txt$");

	private SessionLogDay() {
	}

	public static final class ParsedName {
		public final String sanitizedWorld;
		public final String dayKey;
		/** {@code HH-mm-ss}, or null for a day-only file. */
		public final String timeStamp;

		public ParsedName(String sanitizedWorld, String dayKey, String timeStamp) {
			this.sanitizedWorld = sanitizedWorld;
			this.dayKey = dayKey;
			this.timeStamp = timeStamp;
		}
	}

	public static String dayKey(long nowMs, TimeZone tz) {
		Calendar c = calendar(nowMs, tz);
		return String.format(Locale.US, "%04d-%02d-%02d",
				c.get(Calendar.YEAR),
				c.get(Calendar.MONTH) + 1,
				c.get(Calendar.DAY_OF_MONTH));
	}

	public static String fileName(String sanitizedProfile, String dayKey) {
		String p = sanitizedProfile == null || sanitizedProfile.length() == 0
				? "session" : sanitizedProfile;
		String day = dayKey == null ? "" : dayKey;
		return p + "_" + day + ".txt";
	}

	public static ParsedName parseFileName(String name) {
		if (name == null) {
			return null;
		}
		Matcher m = FILE_NAME.matcher(name);
		if (!m.matches()) {
			return null;
		}
		return new ParsedName(m.group(1), m.group(2), m.group(3));
	}

	public static String dayKeyFromFileName(String name) {
		ParsedName parsed = parseFileName(name);
		return parsed == null ? null : parsed.dayKey;
	}

	public static boolean sameLocalDay(long aMs, long bMs, TimeZone tz) {
		return dayKey(aMs, tz).equals(dayKey(bMs, tz));
	}

	public static boolean needsRollover(String openDayKey, long nowMs, TimeZone tz) {
		if (openDayKey == null || openDayKey.length() == 0) {
			return false;
		}
		return !openDayKey.equals(dayKey(nowMs, tz));
	}

	/**
	 * Newest first: later {@code dayKey} wins; the day-only file of that day
	 * sits above leftover {@code _HH-mm-ss} names (the live file is the one
	 * still being appended). Unparseable names go last.
	 */
	public static int compareNamesNewestFirst(String a, String b) {
		ParsedName pa = parseFileName(a);
		ParsedName pb = parseFileName(b);
		if (pa == null && pb == null) {
			if (a == null && b == null) {
				return 0;
			}
			if (a == null) {
				return 1;
			}
			if (b == null) {
				return -1;
			}
			return b.compareTo(a);
		}
		if (pa == null) {
			return 1;
		}
		if (pb == null) {
			return -1;
		}
		int day = pb.dayKey.compareTo(pa.dayKey);
		if (day != 0) {
			return day;
		}
		if (pa.timeStamp == null && pb.timeStamp != null) {
			return -1;
		}
		if (pa.timeStamp != null && pb.timeStamp == null) {
			return 1;
		}
		if (pa.timeStamp == null) {
			return 0;
		}
		return pb.timeStamp.compareTo(pa.timeStamp);
	}

	/**
	 * Epoch millis of the stamp in {@code name}. Day-only files use the start
	 * of that local day in {@code tz}. Null when the name is not a session log
	 * or the date is not a real calendar day.
	 */
	public static Long fileNameStampMs(String name, TimeZone tz) {
		ParsedName parsed = parseFileName(name);
		if (parsed == null) {
			return null;
		}
		return stampMs(parsed.dayKey, parsed.timeStamp, tz);
	}

	public static String headerLine(String profile, String dayKey) {
		String p = profile == null ? "session" : profile;
		String day = dayKey == null ? "" : dayKey;
		return "=== BlowTorch session log: " + p + " @ " + day + " ===\n";
	}

	public static String connectedMarker(long nowMs, TimeZone tz) {
		return wrap("client connected at " + formatTimeOfDay(nowMs, tz));
	}

	public static String disconnectedMarker(long nowMs, TimeZone tz) {
		return wrap("client disconnected at " + formatTimeOfDay(nowMs, tz));
	}

	public static String loggingEnabledMarker(long nowMs, TimeZone tz) {
		return wrap("logging enabled at " + formatTimeOfDay(nowMs, tz));
	}

	public static String loggingDisabledMarker(long nowMs, TimeZone tz) {
		return wrap("logging disabled at " + formatTimeOfDay(nowMs, tz));
	}

	public static String midnightRolloverMarker(long nowMs, TimeZone tz) {
		return wrap("local day ended at " + formatTimeOfDay(nowMs, tz)
				+ "; continuing in the next day's file");
	}

	public static String midnightContinuationMarker(long nowMs, TimeZone tz) {
		return wrap("continued from the previous local day at "
				+ formatTimeOfDay(nowMs, tz));
	}

	/**
	 * Location tail for the first connect (or enable) of a process.
	 *
	 * @param event {@code client connected} or {@code logging enabled}
	 */
	public static String locationMarker(long nowMs, TimeZone tz, String event,
			String location) {
		String ev = event == null ? "" : event;
		String loc = location == null ? "" : location;
		return wrap(ev + " at " + formatTimeOfDay(nowMs, tz) + " → " + loc);
	}

	/** Generic timed marker: {@code --- HH:mm:ss {body} ---}. */
	public static String timedMarker(long nowMs, TimeZone tz, String body) {
		String b = body == null ? "" : body;
		return wrap(formatTimeOfDay(nowMs, tz) + " " + b);
	}

	public static String formatTimeOfDay(long nowMs, TimeZone tz) {
		Calendar c = calendar(nowMs, tz);
		return String.format(Locale.US, "%02d:%02d:%02d",
				c.get(Calendar.HOUR_OF_DAY),
				c.get(Calendar.MINUTE),
				c.get(Calendar.SECOND));
	}

	private static String wrap(String inner) {
		return "\n--- " + inner + " ---\n";
	}

	private static Calendar calendar(long nowMs, TimeZone tz) {
		Calendar c = Calendar.getInstance(tzOrDefault(tz));
		c.setTimeInMillis(nowMs);
		return c;
	}

	private static TimeZone tzOrDefault(TimeZone tz) {
		return tz != null ? tz : TimeZone.getDefault();
	}

	private static Long stampMs(String dayKey, String timeStamp, TimeZone tz) {
		if (dayKey == null || dayKey.length() != 10
				|| dayKey.charAt(4) != '-' || dayKey.charAt(7) != '-') {
			return null;
		}
		int hour = 0;
		int minute = 0;
		int second = 0;
		if (timeStamp != null) {
			if (timeStamp.length() != 8
					|| timeStamp.charAt(2) != '-' || timeStamp.charAt(5) != '-') {
				return null;
			}
			try {
				hour = Integer.parseInt(timeStamp.substring(0, 2));
				minute = Integer.parseInt(timeStamp.substring(3, 5));
				second = Integer.parseInt(timeStamp.substring(6, 8));
			} catch (NumberFormatException e) {
				return null;
			}
		}
		int year;
		int month;
		int day;
		try {
			year = Integer.parseInt(dayKey.substring(0, 4));
			month = Integer.parseInt(dayKey.substring(5, 7));
			day = Integer.parseInt(dayKey.substring(8, 10));
		} catch (NumberFormatException e) {
			return null;
		}
		Calendar c = Calendar.getInstance(tzOrDefault(tz));
		c.clear();
		c.setLenient(false);
		c.set(Calendar.YEAR, year);
		c.set(Calendar.MONTH, month - 1);
		c.set(Calendar.DAY_OF_MONTH, day);
		c.set(Calendar.HOUR_OF_DAY, hour);
		c.set(Calendar.MINUTE, minute);
		c.set(Calendar.SECOND, second);
		c.set(Calendar.MILLISECOND, 0);
		try {
			return Long.valueOf(c.getTimeInMillis());
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
