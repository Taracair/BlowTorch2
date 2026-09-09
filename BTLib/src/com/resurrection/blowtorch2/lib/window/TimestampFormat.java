package com.resurrection.blowtorch2.lib.window;

import java.util.Calendar;
import java.util.Locale;

/**
 * Which pieces of a line's arrival time to show. Android-free.
 *
 * <p>Default is hour and minute ({@code 14:32}). Month adds the day
 * ({@code 18 Aug}). Year adds the year. Second adds {@code :07}.
 */
public final class TimestampFormat {

	public static final int HOUR = 1;
	public static final int MINUTE = 2;
	public static final int SECOND = 4;
	public static final int MONTH = 8;
	public static final int YEAR = 16;
	public static final int ALL = HOUR | MINUTE | SECOND | MONTH | YEAR;
	public static final int DEFAULT = HOUR | MINUTE;
	public static final String DEFAULT_PARTS = "hour,minute";

	private static final String[] MONTHS = {
			"Jan", "Feb", "Mar", "Apr", "May", "Jun",
			"Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
	};

	private TimestampFormat() {
	}

	/** Drop unknown bits; empty becomes {@link #DEFAULT}. */
	public static int clamp(final int flags) {
		int v = flags & ALL;
		if (v == 0) {
			return DEFAULT;
		}
		return v;
	}

	/**
	 * Toggle one bit. Refuses to turn off the last remaining part.
	 */
	public static int toggle(final int flags, final int bit) {
		int allowed = bit & ALL;
		if (allowed == 0) {
			return clamp(flags);
		}
		int v = clamp(flags);
		if ((v & allowed) != 0) {
			int next = v & ~allowed;
			if (next == 0) {
				return v;
			}
			return next;
		}
		return v | allowed;
	}

	public static int withPart(final int flags, final int bit, final boolean on) {
		int allowed = bit & ALL;
		if (allowed == 0) {
			return clamp(flags);
		}
		int v = clamp(flags);
		if (on) {
			return v | allowed;
		}
		int next = v & ~allowed;
		if (next == 0) {
			return v;
		}
		return next;
	}

	public static boolean has(final int flags, final int bit) {
		return (clamp(flags) & bit) != 0;
	}

	/** {@code hour}, {@code minute}, {@code second}, {@code month}, {@code year}. */
	public static int bitForName(final String raw) {
		if (raw == null) {
			return 0;
		}
		String s = raw.trim().toLowerCase(Locale.US);
		if ("hour".equals(s) || "hours".equals(s) || "hr".equals(s) || "h".equals(s)) {
			return HOUR;
		}
		if ("minute".equals(s) || "minutes".equals(s) || "min".equals(s) || "m".equals(s)) {
			return MINUTE;
		}
		if ("second".equals(s) || "seconds".equals(s) || "sec".equals(s) || "s".equals(s)) {
			return SECOND;
		}
		if ("month".equals(s) || "mon".equals(s)) {
			return MONTH;
		}
		if ("year".equals(s) || "yr".equals(s) || "y".equals(s)) {
			return YEAR;
		}
		return 0;
	}

	public static int parseParts(final String raw) {
		if (raw == null) {
			return DEFAULT;
		}
		String t = raw.trim();
		if (t.length() == 0) {
			return DEFAULT;
		}
		int flags = 0;
		String[] bits = t.split("[,\\s]+");
		for (int i = 0; i < bits.length; i++) {
			flags |= bitForName(bits[i]);
		}
		return clamp(flags);
	}

	public static String formatParts(final int flags) {
		int v = clamp(flags);
		StringBuilder sb = new StringBuilder();
		appendPart(sb, v, HOUR, "hour");
		appendPart(sb, v, MINUTE, "minute");
		appendPart(sb, v, SECOND, "second");
		appendPart(sb, v, MONTH, "month");
		appendPart(sb, v, YEAR, "year");
		return sb.toString();
	}

	private static void appendPart(final StringBuilder sb, final int flags,
			final int bit, final String name) {
		if ((flags & bit) == 0) {
			return;
		}
		if (sb.length() > 0) {
			sb.append(',');
		}
		sb.append(name);
	}

	public static String lineLabel(final long epochMillis, final int flags) {
		if (epochMillis <= 0L) {
			return "";
		}
		int v = clamp(flags);
		Calendar c = Calendar.getInstance();
		c.setTimeInMillis(epochMillis);
		StringBuilder sb = new StringBuilder();
		if ((v & MONTH) != 0) {
			sb.append(c.get(Calendar.DAY_OF_MONTH));
			sb.append(' ');
			sb.append(MONTHS[c.get(Calendar.MONTH)]);
		}
		if ((v & YEAR) != 0) {
			if (sb.length() > 0) {
				sb.append(' ');
			}
			sb.append(c.get(Calendar.YEAR));
		}
		boolean time = (v & (HOUR | MINUTE | SECOND)) != 0;
		if (time) {
			if (sb.length() > 0) {
				sb.append(' ');
			}
			boolean started = false;
			if ((v & HOUR) != 0) {
				pad2(sb, c.get(Calendar.HOUR_OF_DAY));
				started = true;
			}
			if ((v & MINUTE) != 0) {
				if (started) {
					sb.append(':');
				}
				pad2(sb, c.get(Calendar.MINUTE));
				started = true;
			}
			if ((v & SECOND) != 0) {
				if (started) {
					sb.append(':');
				}
				pad2(sb, c.get(Calendar.SECOND));
			}
		}
		return sb.toString();
	}

	/** {@code [14:32] } prefix, empty when there is no stamp. */
	public static String logPrefix(final long epochMillis, final int flags) {
		String label = lineLabel(epochMillis, flags);
		if (label.length() == 0) {
			return "";
		}
		return "[" + label + "] ";
	}

	/**
	 * Prefix every {@code \\n}-delimited line, including a trailing fragment
	 * with no newline. Empty input is unchanged.
	 */
	public static String prefixLog(final String stripped, final long epochMillis,
			final int flags) {
		if (stripped == null || stripped.length() == 0) {
			return stripped;
		}
		String prefix = logPrefix(epochMillis, flags);
		if (prefix.length() == 0) {
			return stripped;
		}
		StringBuilder sb = new StringBuilder(stripped.length() + prefix.length() * 4);
		int i = 0;
		int n = stripped.length();
		while (i < n) {
			int nl = stripped.indexOf('\n', i);
			sb.append(prefix);
			if (nl < 0) {
				sb.append(stripped, i, n);
				break;
			}
			sb.append(stripped, i, nl + 1);
			i = nl + 1;
		}
		return sb.toString();
	}

	private static void pad2(final StringBuilder sb, final int v) {
		if (v < 10) {
			sb.append('0');
		}
		sb.append(v);
	}
}
