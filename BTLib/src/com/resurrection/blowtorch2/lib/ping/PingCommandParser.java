package com.resurrection.blowtorch2.lib.ping;

/**
 * Pure parser for {@code .ping} arguments. No Connection, no Android.
 */
public final class PingCommandParser {

	public static final String ACTION_STATUS = "status";
	public static final String ACTION_SHOW = "show";
	public static final String ACTION_HIDE = "hide";
	public static final String ACTION_TOGGLE = "toggle";
	public static final String ACTION_OPACITY = "opacity";
	public static final String ACTION_SIZE = "size";
	public static final String ACTION_POS = "pos";

	public static final class Result {
		public String error;
		public String action;
		public Integer opacity;
		public Integer size;
		public Integer x;
		public Integer y;
	}

	private PingCommandParser() {
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
		if ("opacity".equals(first)) {
			if (parts.length != 2) {
				r.error = usage();
				return r;
			}
			Integer n = parseInt(parts[1]);
			if (n == null) {
				r.error = usage();
				return r;
			}
			r.action = ACTION_OPACITY;
			r.opacity = Integer.valueOf(PingHudLayout.clampOpacity(n.intValue()));
			return r;
		}
		if ("size".equals(first)) {
			if (parts.length != 2) {
				r.error = usage();
				return r;
			}
			Integer n = parseInt(parts[1]);
			if (n == null) {
				r.error = usage();
				return r;
			}
			r.action = ACTION_SIZE;
			r.size = Integer.valueOf(PingHudLayout.clampSize(n.intValue()));
			return r;
		}
		if ("pos".equals(first) || "move".equals(first)) {
			if (parts.length != 3) {
				r.error = usage();
				return r;
			}
			Integer x = parseInt(parts[1]);
			Integer y = parseInt(parts[2]);
			if (x == null || y == null) {
				r.error = usage();
				return r;
			}
			r.action = ACTION_POS;
			r.x = Integer.valueOf(PingHudLayout.clampPercent(x.intValue()));
			r.y = Integer.valueOf(PingHudLayout.clampPercent(y.intValue()));
			return r;
		}
		r.error = usage();
		return r;
	}

	public static String usage() {
		return "Usage: .ping show|hide|toggle | opacity N | size N | pos X Y\n"
				+ "Times a command you send until the next game line (not ICMP).\n"
				+ "Long-press the number, then drag. Opacity "
				+ PingHudLayout.OPACITY_MIN + "–"
				+ PingHudLayout.OPACITY_MAX + ", size "
				+ PingHudLayout.SIZE_MIN + "–"
				+ PingHudLayout.SIZE_MAX + ".\n";
	}

	private static Integer parseInt(final String token) {
		if (token == null || token.length() == 0) {
			return null;
		}
		try {
			return Integer.valueOf(Integer.parseInt(token));
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
