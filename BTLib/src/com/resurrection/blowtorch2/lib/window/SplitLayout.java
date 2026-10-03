package com.resurrection.blowtorch2.lib.window;

/**
 * Pure parser for {@code .split} arguments: on/off, percent for the primary
 * pane, and left/right vs top/bottom. No Connection, no Android.
 */
public final class SplitLayout {

	public static final int DEFAULT_PERCENT = 50;
	public static final int MIN_PERCENT = 15;
	public static final int MAX_PERCENT = 85;

	public static final int ORIENTATION_HORIZONTAL = 1;
	public static final int ORIENTATION_VERTICAL = 2;

	public static final String ACTION_STATUS = "status";
	public static final String ACTION_OFF = "off";
	public static final String ACTION_APPLY = "apply";

	public static final class Result {
		public String error;
		public String action;
		/** When {@link #ACTION_APPLY}: true if the argument set orientation. */
		public boolean orientationSet;
		public int orientation = ORIENTATION_HORIZONTAL;
		/** When {@link #ACTION_APPLY}: true if the argument set percent. */
		public boolean percentSet;
		public int percent = DEFAULT_PERCENT;
	}

	private SplitLayout() {
	}

	public static int clampPercent(final int percent) {
		if (percent < MIN_PERCENT) {
			return MIN_PERCENT;
		}
		if (percent > MAX_PERCENT) {
			return MAX_PERCENT;
		}
		return percent;
	}

	public static Result parse(final String arg) {
		Result r = new Result();
		String line = arg == null ? "" : arg.trim();
		if (line.length() == 0) {
			r.action = ACTION_STATUS;
			return r;
		}
		String[] parts = line.split("\\s+");
		for (int i = 0; i < parts.length; i++) {
			String tok = parts[i].toLowerCase();
			if ("off".equals(tok) || "hide".equals(tok) || "close".equals(tok)) {
				if (parts.length != 1) {
					r.error = usage();
					return r;
				}
				r.action = ACTION_OFF;
				return r;
			}
			if ("help".equals(tok) || "?".equals(tok)) {
				r.error = usage();
				return r;
			}
			if ("on".equals(tok) || "show".equals(tok) || "open".equals(tok)) {
				r.action = ACTION_APPLY;
				continue;
			}
			Integer orient = orientationToken(tok);
			if (orient != null) {
				r.action = ACTION_APPLY;
				r.orientationSet = true;
				r.orientation = orient.intValue();
				continue;
			}
			Integer pct = parsePercent(tok);
			if (pct != null) {
				r.action = ACTION_APPLY;
				r.percentSet = true;
				r.percent = clampPercent(pct.intValue());
				continue;
			}
			r.error = usage();
			return r;
		}
		if (r.action == null) {
			r.error = usage();
			return r;
		}
		return r;
	}

	private static Integer orientationToken(final String tok) {
		if ("left".equals(tok) || "right".equals(tok)
				|| "horizontal".equals(tok) || "h".equals(tok)
				|| "lr".equals(tok)) {
			return Integer.valueOf(ORIENTATION_HORIZONTAL);
		}
		if ("top".equals(tok) || "bottom".equals(tok)
				|| "vertical".equals(tok) || "v".equals(tok)
				|| "tb".equals(tok)) {
			return Integer.valueOf(ORIENTATION_VERTICAL);
		}
		return null;
	}

	private static Integer parsePercent(final String tok) {
		if (tok == null || tok.length() == 0) {
			return null;
		}
		String s = tok;
		if (s.endsWith("%")) {
			s = s.substring(0, s.length() - 1);
		}
		if (s.length() == 0) {
			return null;
		}
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c < '0' || c > '9') {
				return null;
			}
		}
		try {
			return Integer.valueOf(Integer.parseInt(s));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	public static String usage() {
		return "Usage: .split [on|off] [horizontal|h|vertical|v|left|right|top|bottom] [15-85]\n"
				+ "Two views of the same game text. horizontal/h is left/right;"
				+ " vertical/v is top/bottom. Primary (left or top) gets the"
				+ " percent; default 50. Buttons, mapper, chat and NAWS stay on the"
				+ " primary pane. .split off returns to one pane.\n";
	}

	public static String describe(final boolean on, final int orientation,
			final int percent) {
		if (!on) {
			return "Split is off (one pane).";
		}
		String side = orientation == ORIENTATION_VERTICAL
				? "vertical (top/bottom)" : "horizontal (left/right)";
		return "Split " + side + ", primary " + clampPercent(percent) + "%.";
	}
}
