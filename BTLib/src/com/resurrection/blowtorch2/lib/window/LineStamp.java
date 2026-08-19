package com.resurrection.blowtorch2.lib.window;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * When a line of game text arrived, carried through the byte stream so a window
 * rebuild still knows.
 *
 * <p>OSC {@code ESC ] 1337 ; btstamp=&lt;epochMillis&gt; BEL}. Same reason as
 * the inline-picture marker: {@code TextTree} already skips OSC, so a build
 * that does not know this sequence drops it instead of printing it.
 */
public final class LineStamp {

	private static final char ESC = 0x1B;
	private static final char BEL = 0x07;

	/** Payload prefix inside OSC (after {@code ESC ]}). */
	public static final String PREFIX = "1337;btstamp=";

	private static final Pattern TIME = Pattern.compile(
			"^\\d{1,2}:\\d{2}(:\\d{2})?$");
	private static final Pattern ISO_DAY = Pattern.compile(
			"^\\d{4}-\\d{2}-\\d{2}$");
	private static final Pattern CLOCK = Pattern.compile(
			"(\\d{1,2}):(\\d{2})(?::(\\d{2}))?");
	private static final Pattern NUMBER = Pattern.compile("\\d+");
	private static final String[] MONTH_PREFIX = {
			"jan", "feb", "mar", "apr", "may", "jun",
			"jul", "aug", "sep", "oct", "nov", "dec"
	};

	private LineStamp() {
	}

	/** Bytes that stamp {@code epochMillis} onto the next line. */
	public static byte[] marker(final long epochMillis) {
		String s = "" + ESC + "]" + PREFIX + epochMillis + BEL;
		return s.getBytes(StandardCharsets.US_ASCII);
	}

	/**
	 * Epoch millis from an OSC payload, or null when this is some other OSC.
	 *
	 * @param payload the bytes between {@code ESC ]} and the terminator
	 */
	public static Long parse(final String payload) {
		if (payload == null || !payload.startsWith(PREFIX)) {
			return null;
		}
		String rest = payload.substring(PREFIX.length()).trim();
		if (rest.length() == 0) {
			return null;
		}
		try {
			long v = Long.parseLong(rest);
			if (v <= 0L) {
				return null;
			}
			return Long.valueOf(v);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/**
	 * Short label for the jump-to-live cluster.
	 *
	 * <p>Same calendar day: {@code 14:32}. Same year: {@code 18 Aug, 14:32}.
	 * Else {@code 18 Aug 2025, 14:32}.
	 */
	public static String overlayLabel(final long epochMillis, final long nowMillis) {
		if (epochMillis <= 0L) {
			return "";
		}
		Calendar then = Calendar.getInstance();
		then.setTimeInMillis(epochMillis);
		Calendar now = Calendar.getInstance();
		now.setTimeInMillis(nowMillis);
		Date d = new Date(epochMillis);
		if (then.get(Calendar.YEAR) == now.get(Calendar.YEAR)
				&& then.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)) {
			return new SimpleDateFormat("HH:mm", Locale.US).format(d);
		}
		if (then.get(Calendar.YEAR) == now.get(Calendar.YEAR)) {
			return new SimpleDateFormat("d MMM, HH:mm", Locale.US).format(d);
		}
		return new SimpleDateFormat("d MMM yyyy, HH:mm", Locale.US).format(d);
	}

	/** True when a search box query is about when, not about game text. */
	public static boolean looksLikeWhenQuery(final String query) {
		if (query == null) {
			return false;
		}
		String q = query.trim();
		if (q.length() == 0) {
			return false;
		}
		if (TIME.matcher(q).matches()) {
			return true;
		}
		if (ISO_DAY.matcher(q).matches()) {
			return true;
		}
		// "18 Aug" / "18 Aug, 14:32" / "Aug 18"
		return monthIn(q) != null && q.matches(".*\\d.*");
	}

	/** True when this stamp would show up for {@code query} in {@code .search}. */
	public static boolean matchesQuery(final long epochMillis, final String query) {
		if (epochMillis <= 0L || query == null) {
			return false;
		}
		String q = query.trim();
		if (q.length() == 0 || !looksLikeWhenQuery(q)) {
			return false;
		}
		Calendar then = Calendar.getInstance();
		then.setTimeInMillis(epochMillis);
		if (TIME.matcher(q).matches()) {
			return clockMatches(then, q);
		}
		if (ISO_DAY.matcher(q).matches()) {
			return then.get(Calendar.YEAR) == Integer.parseInt(q.substring(0, 4))
					&& then.get(Calendar.MONTH) == Integer.parseInt(q.substring(5, 7)) - 1
					&& then.get(Calendar.DAY_OF_MONTH) == Integer.parseInt(q.substring(8, 10));
		}
		Integer month = monthIn(q);
		java.util.regex.Matcher clock = CLOCK.matcher(q);
		boolean hasClock = clock.find();
		String withoutClock = hasClock
				? q.substring(0, clock.start()) + " " + q.substring(clock.end())
				: q;
		Integer day = null;
		Integer year = null;
		java.util.regex.Matcher nums = NUMBER.matcher(withoutClock);
		while (nums.find()) {
			int n = Integer.parseInt(nums.group());
			if (n >= 1000) {
				year = Integer.valueOf(n);
			} else if (n >= 1 && n <= 31) {
				day = Integer.valueOf(n);
			}
		}
		if (month != null && then.get(Calendar.MONTH) != month.intValue()) {
			return false;
		}
		if (day != null && then.get(Calendar.DAY_OF_MONTH) != day.intValue()) {
			return false;
		}
		if (year != null && then.get(Calendar.YEAR) != year.intValue()) {
			return false;
		}
		if (hasClock && !clockMatches(then, clock.group())) {
			return false;
		}
		return month != null || day != null || year != null || hasClock;
	}

	private static Integer monthIn(final String query) {
		String lower = query.toLowerCase(Locale.US);
		for (int i = 0; i < MONTH_PREFIX.length; i++) {
			if (lower.matches(".*\\b" + MONTH_PREFIX[i] + "[a-z]*\\b.*")) {
				return Integer.valueOf(i);
			}
		}
		return null;
	}

	private static boolean clockMatches(final Calendar then, final String clock) {
		java.util.regex.Matcher m = CLOCK.matcher(clock.trim());
		if (!m.matches()) {
			return false;
		}
		if (then.get(Calendar.HOUR_OF_DAY) != Integer.parseInt(m.group(1))) {
			return false;
		}
		if (then.get(Calendar.MINUTE) != Integer.parseInt(m.group(2))) {
			return false;
		}
		if (m.group(3) != null) {
			return then.get(Calendar.SECOND) == Integer.parseInt(m.group(3));
		}
		return true;
	}
}
