package com.resurrection.blowtorch2.lib.service.function;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.service.WindowToken;

/**
 * Wrap game text around on-screen buttons: {@code .avoidbuttons},
 * {@code .avoidbuttons on|off|toggle}, {@code .avoidbuttons letters|words},
 * {@code .avoidbuttons margin N}.
 *
 * <p>Same preference as Options → Window → Text avoids on-screen buttons?
 * and Avoid-buttons break. Window options, not connection options — see
 * {@link Connection#updateMainWindowBooleanOption}.
 */
public class AvoidButtonsCommand extends SpecialCommand {

	public static final String OPTION_KEY = "text_avoid_buttons";
	public static final String OPTION_BREAK_KEY = "text_avoid_buttons_break";
	public static final String OPTION_MARGIN_KEY = "text_avoid_buttons_margin";

	public AvoidButtonsCommand() {
		this.commandName = "avoidbuttons";
	}

	public Object execute(Object o, Connection c) {
		String arg = o == null ? "" : ((String) o).trim();
		boolean on = c.getMainWindowBooleanOption(OPTION_KEY, false);
		int breakMode = clampBreak(c.getMainWindowIntegerOption(OPTION_BREAK_KEY,
				WindowToken.DEFAULT_AVOID_BUTTONS_BREAK));
		int margin = WindowToken.clampAvoidMargin(c.getMainWindowIntegerOption(OPTION_MARGIN_KEY,
				WindowToken.DEFAULT_AVOID_BUTTONS_MARGIN));

		if (arg.length() == 0) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Text avoiding on-screen buttons is currently "
					+ (on ? "on" : "off")
					+ ", break " + breakLabel(breakMode)
					+ ", margin " + margin + " px.\n"
					+ usage()
					+ "Also: Options → Window → Text avoids on-screen buttons?\n");
			return null;
		}

		String first = arg.toLowerCase().split("\\s+")[0];
		if ("margin".equals(first)) {
			return setMargin(c, arg, margin);
		}
		Integer asBreak = parseBreakToken(first);
		if (asBreak != null) {
			return setBreak(c, asBreak.intValue(), breakMode);
		}

		Boolean desired = DimRepeatCommand.parseOnOff(first);
		if ("toggle".equals(first)) {
			desired = Boolean.valueOf(!on);
		}
		if (desired == null) {
			c.sendDataToWindow(getErrorMessage("Avoidbuttons command usage:",
					usage()));
			return null;
		}
		if (desired.booleanValue() == on) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Text avoiding on-screen buttons already "
					+ (desired.booleanValue() ? "on" : "off") + ".\n");
			return null;
		}
		if (!c.updateMainWindowBooleanOption(OPTION_KEY, desired.booleanValue())) {
			c.sendDataToWindow(getErrorMessage("Avoidbuttons command error",
					"There is no game window to change yet."));
			return null;
		}
		c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
				+ "Text avoiding on-screen buttons "
				+ (desired.booleanValue() ? "on" : "off")
				+ (desired.booleanValue()
						? ", break " + breakLabel(breakMode)
						: "")
				+ ".\n");
		return null;
	}

	private Object setBreak(Connection c, final int next, final int current) {
		int clamped = clampBreak(next);
		if (clamped == current) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Avoid-buttons break already "
					+ breakLabel(clamped) + ".\n");
			return null;
		}
		if (!c.updateMainWindowIntegerOption(OPTION_BREAK_KEY, clamped)) {
			c.sendDataToWindow(getErrorMessage("Avoidbuttons command error",
					"There is no game window to change yet."));
			return null;
		}
		boolean on = c.getMainWindowBooleanOption(OPTION_KEY, false);
		c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
				+ "Avoid-buttons break " + breakLabel(clamped) + "."
				+ (on ? "" : " Text avoiding is off (.avoidbuttons on).")
				+ "\n");
		return null;
	}

	private Object setMargin(final Connection c, final String arg, final int current) {
		String rest = arg.replaceFirst("(?i)^margin", "").trim();
		if (rest.length() == 0) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Avoid-buttons margin is " + current + " px.\n");
			return null;
		}
		int next;
		try {
			next = Integer.parseInt(rest.split("\\s+")[0]);
		} catch (NumberFormatException e) {
			c.sendDataToWindow(getErrorMessage("Avoidbuttons command usage:", usage()));
			return null;
		}
		next = WindowToken.clampAvoidMargin(next);
		if (next == current) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Avoid-buttons margin already " + next + " px.\n");
			return null;
		}
		if (!c.updateMainWindowIntegerOption(OPTION_MARGIN_KEY, next)) {
			c.sendDataToWindow(getErrorMessage("Avoidbuttons command error",
					"There is no game window to change yet."));
			return null;
		}
		boolean on = c.getMainWindowBooleanOption(OPTION_KEY, false);
		c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
				+ "Avoid-buttons margin " + next + " px."
				+ (on ? "" : " Text avoiding is off (.avoidbuttons on).")
				+ "\n");
		return null;
	}

	static Integer parseBreakToken(final String token) {
		if ("letters".equals(token)) {
			return Integer.valueOf(WindowToken.AVOID_BUTTONS_BREAK_LETTERS);
		}
		if ("words".equals(token)) {
			return Integer.valueOf(WindowToken.AVOID_BUTTONS_BREAK_WORDS);
		}
		return null;
	}

	static int clampBreak(final int value) {
		if (value == WindowToken.AVOID_BUTTONS_BREAK_WORDS) {
			return WindowToken.AVOID_BUTTONS_BREAK_WORDS;
		}
		return WindowToken.AVOID_BUTTONS_BREAK_LETTERS;
	}

	static String breakLabel(final int mode) {
		return mode == WindowToken.AVOID_BUTTONS_BREAK_WORDS ? "words" : "letters";
	}

	static String usage() {
		return "Usage: .avoidbuttons on | off | toggle | letters | words | margin N\n"
				+ "Game text wraps around on-screen buttons (grid pad and floating copies).\n"
				+ "letters = one character at a time (default); words = keep whole words.\n"
				+ "margin N is extra pixels around every button (0–"
				+ WindowToken.MAX_AVOID_BUTTONS_MARGIN
				+ "). A button can add its own.\n"
				+ "Off by default — ASCII maps may break. Live while dragging a floating button.\n";
	}
}
