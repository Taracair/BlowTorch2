package com.resurrection.blowtorch2.lib.window;

import java.util.Locale;

/**
 * Parse result for {@code .scrollv} and {@code .scrollh}. The number is added
 * to where the window already is. Positive vertical moves toward the text
 * that was above. Positive horizontal moves right.
 */
public final class ScrollStep {

	public static final String ACTION_PREFIX = "scroll:";

	/** Same floor as {@code .width}. At this percent the line fits the screen. */
	public static final int FIT_WIDTH_PERCENT = 100;

	public static final String[] COMMANDS = {
		"scrollv", "scrollh",
	};

	private ScrollStep() {
	}

	public enum Kind {
		MISSING_NUMBER,
		BAD_NUMBER,
		NOTHING_SIDEWAYS,
		MOVE,
	}

	public enum Axis {
		VERTICAL,
		HORIZONTAL,
	}

	public static final class Decision {
		public final Kind kind;
		public final Axis axis;
		/** Signed. Positive vertical is toward the text above. Positive horizontal is right. */
		public final int amount;
		public final String command;
		public final int widthPercent;

		private Decision(final Kind kind, final Axis axis, final int amount,
				final String command, final int widthPercent) {
			this.kind = kind;
			this.axis = axis;
			this.amount = amount;
			this.command = command;
			this.widthPercent = widthPercent;
		}

		public boolean horizontal() {
			return axis == Axis.HORIZONTAL;
		}

		/** Player text for a refusal. A move says nothing. */
		public String message() {
			String shown = "." + command;
			if (kind == Kind.MISSING_NUMBER) {
				String unit = horizontal() ? "columns" : "lines";
				return shown + " needs a number of " + unit
						+ ". For example, " + shown + " 10.";
			}
			if (kind == Kind.BAD_NUMBER) {
				String unit = horizontal() ? "columns" : "lines";
				return shown + " needs a whole number of " + unit
						+ ", other than 0. For example, " + shown + " 10 or "
						+ shown + " -10.";
			}
			if (kind == Kind.NOTHING_SIDEWAYS) {
				return "There is nothing to scroll sideways. Text width is "
						+ widthPercent + "% of the screen.";
			}
			return null;
		}

		/** Payload for {@code doRunUiAction}. Null unless this is a move. */
		public String toAction() {
			if (kind != Kind.MOVE || axis == null || amount == 0) {
				return null;
			}
			return ACTION_PREFIX + token(axis) + ":" + amount;
		}
	}

	/**
	 * @param command command name, with or without the leading dot
	 * @param argument the words after the name; empty means no number
	 * @param widthPercent current {@code .width} percent
	 * @return null when {@code command} is not one of the two
	 */
	public static Decision decide(final String command, final String argument,
			final int widthPercent) {
		Axis axis = axisOf(command);
		if (axis == null) {
			return null;
		}
		String name = commandName(command);
		if (axis == Axis.HORIZONTAL && widthPercent <= FIT_WIDTH_PERCENT) {
			return new Decision(Kind.NOTHING_SIDEWAYS, axis, 0, name, widthPercent);
		}
		String arg = argument == null ? "" : argument.trim();
		if (arg.length() == 0) {
			return new Decision(Kind.MISSING_NUMBER, axis, 0, name, widthPercent);
		}
		String[] parts = arg.split("\\s+");
		if (parts.length != 1) {
			return new Decision(Kind.BAD_NUMBER, axis, 0, name, widthPercent);
		}
		Integer n = signedCount(parts[0]);
		if (n == null) {
			return new Decision(Kind.BAD_NUMBER, axis, 0, name, widthPercent);
		}
		return new Decision(Kind.MOVE, axis, n.intValue(), name, widthPercent);
	}

	/** The action string {@link Decision#toAction} writes, or null. */
	public static Decision fromAction(final String action) {
		if (action == null || !action.startsWith(ACTION_PREFIX)) {
			return null;
		}
		String rest = action.substring(ACTION_PREFIX.length());
		int colon = rest.indexOf(':');
		if (colon <= 0 || rest.indexOf(':', colon + 1) >= 0) {
			return null;
		}
		Axis axis = axisFromToken(rest.substring(0, colon));
		Integer n = signedCount(rest.substring(colon + 1));
		if (axis == null || n == null) {
			return null;
		}
		return new Decision(Kind.MOVE, axis, n.intValue(), commandFor(axis), 0);
	}

	/**
	 * True when every command on the line is {@code .scrollv} or {@code .scrollh}.
	 * Sending such a line must not jump to the live edge first: the step is
	 * added to the current view, and that jump would measure it from the bottom.
	 */
	public static boolean keepsPlace(final String line) {
		if (line == null) {
			return false;
		}
		String[] parts = line.split(";", -1);
		boolean any = false;
		for (int i = 0; i < parts.length; i++) {
			String segment = parts[i].trim();
			if (segment.length() == 0) {
				continue;
			}
			any = true;
			if (!isScrollCommand(segment)) {
				return false;
			}
		}
		return any;
	}

	/**
	 * Pixels to add to {@code mScrollback}. Positive shows the text that was
	 * above when the newest line is at the bottom.
	 */
	public static int verticalPixels(final int signedLines, final boolean newestAtTop,
			final int lineSize) {
		if (signedLines == 0 || lineSize <= 0) {
			return 0;
		}
		int lines = signedLines;
		if (newestAtTop) {
			if (lines == Integer.MIN_VALUE) {
				lines = Integer.MAX_VALUE;
			} else {
				lines = -lines;
			}
		}
		long px = (long) lines * (long) lineSize;
		if (px > Integer.MAX_VALUE) {
			return Integer.MAX_VALUE;
		}
		if (px < Integer.MIN_VALUE) {
			return Integer.MIN_VALUE;
		}
		return (int) px;
	}

	/** Keep a scroll offset inside the live edge and the far end of the buffer. */
	public static double clampScrollback(final double value, final double scrollMin,
			final double max) {
		double hi = max < scrollMin ? scrollMin : max;
		if (value < scrollMin) {
			return scrollMin;
		}
		if (value > hi) {
			return hi;
		}
		return value;
	}

	/** One line for {@code .help}, first clause is what the bare name does. */
	public static String summary(final String command) {
		Axis axis = axisOf(command);
		if (axis == Axis.VERTICAL) {
			return "needs a number of lines, added to where the window is now";
		}
		if (axis == Axis.HORIZONTAL) {
			return "nothing to scroll sideways when text width is 100; otherwise a number of columns";
		}
		return "";
	}

	public static boolean isScrollHelp(final String filter) {
		if (filter == null || filter.length() == 0) {
			return false;
		}
		return "scroll".equals(filter) || axisOf(filter) != null;
	}

	public static String helpText() {
		return "  .scrollv <N>        — N lines from where the window is now. "
				+ "10 moves toward the text that was above. -10 moves toward the text that was below.\n"
				+ "  .scrollh <N>        — N columns from where the canvas is now, when text width is above 100. "
				+ "10 moves right. -10 moves left.\n"
				+ "No number does not pick a count. 0 does not move. "
				+ "At 100% there is nothing to scroll sideways.\n"
				+ "Sending one of these does not jump to the newest text.\n"
				+ "The number is how far one press moves. A shortcut can send either line.\n"
				+ "Not sent to the world.\n";
	}

	private static boolean isScrollCommand(final String segment) {
		String s = segment.trim();
		if (!s.startsWith(".")) {
			return false;
		}
		s = s.substring(1).trim();
		int sp = 0;
		while (sp < s.length() && !Character.isWhitespace(s.charAt(sp))) {
			sp++;
		}
		if (sp == 0) {
			return false;
		}
		String name = s.substring(0, sp).toLowerCase(Locale.US);
		return "scrollv".equals(name) || "scrollh".equals(name);
	}

	private static String commandFor(final Axis axis) {
		if (axis == Axis.VERTICAL) {
			return "scrollv";
		}
		if (axis == Axis.HORIZONTAL) {
			return "scrollh";
		}
		return "";
	}

	private static String token(final Axis axis) {
		if (axis == Axis.VERTICAL) {
			return "v";
		}
		if (axis == Axis.HORIZONTAL) {
			return "h";
		}
		return "";
	}

	private static Axis axisFromToken(final String token) {
		if ("v".equals(token)) {
			return Axis.VERTICAL;
		}
		if ("h".equals(token)) {
			return Axis.HORIZONTAL;
		}
		return null;
	}

	private static Axis axisOf(final String command) {
		String n = commandName(command);
		if ("scrollv".equals(n)) {
			return Axis.VERTICAL;
		}
		if ("scrollh".equals(n)) {
			return Axis.HORIZONTAL;
		}
		return null;
	}

	private static String commandName(final String command) {
		String n = command == null ? "" : command.trim().toLowerCase(Locale.US);
		if (n.startsWith(".")) {
			n = n.substring(1);
		}
		return n;
	}

	/** A whole number other than 0. A leading + or - is allowed. No fraction. */
	private static Integer signedCount(final String token) {
		if (token == null || token.length() == 0 || token.length() > 10) {
			return null;
		}
		int i = 0;
		boolean neg = false;
		char sign = token.charAt(0);
		if (sign == '+' || sign == '-') {
			neg = sign == '-';
			i = 1;
		}
		if (i >= token.length() || token.length() - i > 9) {
			return null;
		}
		for (int c = i; c < token.length(); c++) {
			char ch = token.charAt(c);
			if (ch < '0' || ch > '9') {
				return null;
			}
		}
		try {
			int n = Integer.parseInt(token.substring(i));
			if (n < 1) {
				return null;
			}
			if (neg) {
				n = -n;
			}
			return Integer.valueOf(n);
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
