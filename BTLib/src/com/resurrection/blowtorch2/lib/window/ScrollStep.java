package com.resurrection.blowtorch2.lib.window;

import java.util.Locale;

/**
 * Parse result for the four scroll commands. Sideways distance is columns:
 * text width is a percent turned into wrap columns of {@code mOneCharWidth}.
 */
public final class ScrollStep {

	public static final String ACTION_PREFIX = "scroll:";

	/** Same floor as {@code .width}. At this percent the line fits the screen. */
	public static final int FIT_WIDTH_PERCENT = 100;

	public static final String[] COMMANDS = {
		"scrollup", "scrolldown", "scrollleft", "scrollright",
	};

	private ScrollStep() {
	}

	public enum Kind {
		MISSING_NUMBER,
		BAD_NUMBER,
		NOTHING_SIDEWAYS,
		MOVE,
	}

	public enum Way {
		UP,
		DOWN,
		LEFT,
		RIGHT,
	}

	public static final class Decision {
		public final Kind kind;
		public final Way way;
		public final int amount;
		public final String command;
		public final int widthPercent;

		private Decision(final Kind kind, final Way way, final int amount,
				final String command, final int widthPercent) {
			this.kind = kind;
			this.way = way;
			this.amount = amount;
			this.command = command;
			this.widthPercent = widthPercent;
		}

		public boolean columns() {
			return way == Way.LEFT || way == Way.RIGHT;
		}

		/** Player text for a refusal. A move says nothing. */
		public String message() {
			String shown = "." + command;
			String example = exampleFor(way);
			if (kind == Kind.MISSING_NUMBER) {
				String unit = columns() ? "columns" : "lines";
				return shown + " needs a number of " + unit
						+ ". For example, " + shown + " " + example + ".";
			}
			if (kind == Kind.BAD_NUMBER) {
				String unit = columns() ? "columns" : "lines";
				return shown + " needs a whole number of " + unit
						+ ", 1 or more. For example, " + shown + " " + example + ".";
			}
			if (kind == Kind.NOTHING_SIDEWAYS) {
				return "There is nothing to scroll sideways. Text width is "
						+ widthPercent + "% of the screen.";
			}
			return null;
		}

		/** Payload for {@code doRunUiAction}. Null unless this is a move. */
		public String toAction() {
			if (kind != Kind.MOVE || way == null || amount < 1) {
				return null;
			}
			return ACTION_PREFIX + token(way) + ":" + amount;
		}
	}

	/**
	 * @param command command name, with or without the leading dot
	 * @param argument the words after the name; empty means no number
	 * @param widthPercent current {@code .width} percent
	 * @return null when {@code command} is not one of the four
	 */
	public static Decision decide(final String command, final String argument,
			final int widthPercent) {
		Way way = wayOf(command);
		if (way == null) {
			return null;
		}
		String name = commandName(command);
		if ((way == Way.LEFT || way == Way.RIGHT) && widthPercent <= FIT_WIDTH_PERCENT) {
			return new Decision(Kind.NOTHING_SIDEWAYS, way, 0, name, widthPercent);
		}
		String arg = argument == null ? "" : argument.trim();
		if (arg.length() == 0) {
			return new Decision(Kind.MISSING_NUMBER, way, 0, name, widthPercent);
		}
		String[] parts = arg.split("\\s+");
		if (parts.length != 1) {
			return new Decision(Kind.BAD_NUMBER, way, 0, name, widthPercent);
		}
		Integer n = plainCount(parts[0]);
		if (n == null) {
			return new Decision(Kind.BAD_NUMBER, way, 0, name, widthPercent);
		}
		return new Decision(Kind.MOVE, way, n.intValue(), name, widthPercent);
	}

	/** The action string {@link Decision#toAction} writes, or null. */
	public static Decision fromAction(final String action) {
		if (action == null || !action.startsWith(ACTION_PREFIX)) {
			return null;
		}
		String rest = action.substring(ACTION_PREFIX.length());
		int colon = rest.indexOf(':');
		if (colon <= 0) {
			return null;
		}
		Way way = wayFromToken(rest.substring(0, colon));
		Integer n = plainCount(rest.substring(colon + 1));
		if (way == null || n == null) {
			return null;
		}
		return new Decision(Kind.MOVE, way, n.intValue(), commandFor(way), 0);
	}

	/**
	 * Lines to add to the distance from the live edge. Up shows the text
	 * that was above; that distance grows only when the newest line is at the bottom.
	 */
	public static int lineDelta(final Way way, final int amount, final boolean newestAtTop) {
		if (amount < 1) {
			return 0;
		}
		int towardAbove;
		if (way == Way.UP) {
			towardAbove = amount;
		} else if (way == Way.DOWN) {
			towardAbove = -amount;
		} else {
			return 0;
		}
		return newestAtTop ? -towardAbove : towardAbove;
	}

	/** Columns to add to {@code mScrollX}. Right shows the text further right. */
	public static int columnDelta(final Way way, final int amount) {
		if (amount < 1) {
			return 0;
		}
		if (way == Way.RIGHT) {
			return amount;
		}
		if (way == Way.LEFT) {
			return -amount;
		}
		return 0;
	}

	/** One line for {@code .help}, first clause is what the bare name does. */
	public static String summary(final String command) {
		Way way = wayOf(command);
		if (way == Way.UP) {
			return "needs a number of lines, then moves toward the text that was above";
		}
		if (way == Way.DOWN) {
			return "needs a number of lines, then moves toward the text that was below";
		}
		if (way == Way.LEFT || way == Way.RIGHT) {
			return "nothing to scroll sideways when text width is 100; otherwise a number of columns";
		}
		return "";
	}

	public static boolean isScrollHelp(final String filter) {
		if (filter == null || filter.length() == 0) {
			return false;
		}
		return "scroll".equals(filter) || wayOf(filter) != null;
	}

	public static String helpText() {
		return "  .scrollup <N>       — N lines toward the text that was above. For example, .scrollup 10\n"
				+ "  .scrolldown <N>     — N lines toward the text that was below. For example, .scrolldown 5\n"
				+ "  .scrollleft <N>     — N columns to the left when text width is above 100\n"
				+ "  .scrollright <N>    — N columns to the right when text width is above 100\n"
				+ "No number does not pick a count. At 100% there is nothing to scroll sideways.\n"
				+ "The number on left and right is how far one press moves. A shortcut can send any of these.\n"
				+ "Not sent to the world.\n";
	}

	private static String exampleFor(final Way way) {
		if (way == Way.DOWN) {
			return "5";
		}
		return "10";
	}

	private static String commandFor(final Way way) {
		if (way == Way.UP) {
			return "scrollup";
		}
		if (way == Way.DOWN) {
			return "scrolldown";
		}
		if (way == Way.LEFT) {
			return "scrollleft";
		}
		if (way == Way.RIGHT) {
			return "scrollright";
		}
		return "";
	}

	private static String token(final Way way) {
		if (way == Way.UP) {
			return "up";
		}
		if (way == Way.DOWN) {
			return "down";
		}
		if (way == Way.LEFT) {
			return "left";
		}
		if (way == Way.RIGHT) {
			return "right";
		}
		return "";
	}

	private static Way wayFromToken(final String token) {
		if ("up".equals(token)) {
			return Way.UP;
		}
		if ("down".equals(token)) {
			return Way.DOWN;
		}
		if ("left".equals(token)) {
			return Way.LEFT;
		}
		if ("right".equals(token)) {
			return Way.RIGHT;
		}
		return null;
	}

	private static Way wayOf(final String command) {
		String n = commandName(command);
		if ("scrollup".equals(n)) {
			return Way.UP;
		}
		if ("scrolldown".equals(n)) {
			return Way.DOWN;
		}
		if ("scrollleft".equals(n)) {
			return Way.LEFT;
		}
		if ("scrollright".equals(n)) {
			return Way.RIGHT;
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

	/** A whole number, 1 or more. No sign, no fraction, no extra word. */
	private static Integer plainCount(final String token) {
		if (token == null || token.length() == 0 || token.length() > 9) {
			return null;
		}
		for (int i = 0; i < token.length(); i++) {
			char c = token.charAt(i);
			if (c < '0' || c > '9') {
				return null;
			}
		}
		try {
			int n = Integer.parseInt(token);
			if (n < 1) {
				return null;
			}
			return Integer.valueOf(n);
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
