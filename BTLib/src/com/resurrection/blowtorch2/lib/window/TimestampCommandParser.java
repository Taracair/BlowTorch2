package com.resurrection.blowtorch2.lib.window;

/**
 * Pure parser for {@code .timestamp} arguments. No Connection, no Android.
 */
public final class TimestampCommandParser {

	public static final String ACTION_STATUS = "status";
	public static final String ACTION_SHOW = "show";
	public static final String ACTION_HIDE = "hide";
	public static final String ACTION_TOGGLE = "toggle";
	public static final String ACTION_LOG = "log";
	public static final String ACTION_PART = "part";

	public static final class Result {
		public String error;
		public String action;
		/** For {@link #ACTION_LOG}: true/false, or null to toggle. */
		public Boolean logOn;
		/** For {@link #ACTION_PART}: one of {@link TimestampFormat} bits. */
		public int partBit;
		/** For {@link #ACTION_PART}: true/false, or null to toggle. */
		public Boolean partOn;
	}

	private TimestampCommandParser() {
	}

	public static Result parse(final String arg) {
		Result r = new Result();
		String line = arg == null ? "" : arg.trim();
		if (line.length() == 0) {
			r.action = ACTION_STATUS;
			return r;
		}
		String[] parts = line.split("\\s+");
		String first = parts[0].toLowerCase();
		if ("show".equals(first) || "on".equals(first)) {
			if (parts.length != 1) {
				r.error = usage();
				return r;
			}
			r.action = ACTION_SHOW;
			return r;
		}
		if ("hide".equals(first) || "off".equals(first)) {
			if (parts.length != 1) {
				r.error = usage();
				return r;
			}
			r.action = ACTION_HIDE;
			return r;
		}
		if ("toggle".equals(first)) {
			if (parts.length != 1) {
				r.error = usage();
				return r;
			}
			r.action = ACTION_TOGGLE;
			return r;
		}
		if ("log".equals(first)) {
			if (parts.length == 1) {
				r.error = usage();
				return r;
			}
			if (parts.length != 2) {
				r.error = usage();
				return r;
			}
			String second = parts[1].toLowerCase();
			r.action = ACTION_LOG;
			if ("on".equals(second) || "show".equals(second)) {
				r.logOn = Boolean.TRUE;
				return r;
			}
			if ("off".equals(second) || "hide".equals(second)) {
				r.logOn = Boolean.FALSE;
				return r;
			}
			if ("toggle".equals(second)) {
				r.logOn = null;
				return r;
			}
			r.error = usage();
			r.action = null;
			return r;
		}
		int bit = TimestampFormat.bitForName(first);
		if (bit != 0) {
			if (parts.length == 1) {
				r.action = ACTION_PART;
				r.partBit = bit;
				r.partOn = null;
				return r;
			}
			if (parts.length != 2) {
				r.error = usage();
				return r;
			}
			String second = parts[1].toLowerCase();
			r.action = ACTION_PART;
			r.partBit = bit;
			if ("on".equals(second) || "show".equals(second)) {
				r.partOn = Boolean.TRUE;
				return r;
			}
			if ("off".equals(second) || "hide".equals(second)) {
				r.partOn = Boolean.FALSE;
				return r;
			}
			if ("toggle".equals(second)) {
				r.partOn = null;
				return r;
			}
			r.error = usage();
			r.action = null;
			return r;
		}
		r.error = usage();
		return r;
	}

	public static String usage() {
		return "Usage: .timestamp on|off|toggle | log on|off|toggle"
				+ " | hour|minute|second|month|year [on|off]\n"
				+ "Time each line arrived, on the right of the game. Does not change"
				+ " wrapping, triggers or copy. Log prefixes the same stamp on the left"
				+ " of each session-log line.\n";
	}
}
