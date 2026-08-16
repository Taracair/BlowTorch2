package com.resurrection.blowtorch2.lib.service.function;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.window.RepeatedLineDimmer;

/**
 * Dim repeated long lines from the input bar: {@code .dimrepeat},
 * {@code .dimrepeat on|off}, {@code .dimrepeat lines N},
 * {@code .dimrepeat strength N}.
 *
 * <p>Same preferences as Options → Window → Dim repeated lines? / Remember how
 * many lines? / Dim strength (%). Window options, not connection options — see
 * {@link Connection#updateMainWindowBooleanOption}.
 */
public class DimRepeatCommand extends SpecialCommand {

	public static final String OPTION_ENABLED = "dim_repeated_lines";
	public static final String OPTION_WINDOW = "dim_repeated_window";
	public static final String OPTION_STRENGTH = "dim_repeated_strength";

	public DimRepeatCommand() {
		this.commandName = "dimrepeat";
	}

	public Object execute(Object o, Connection c) {
		String arg = o == null ? "" : ((String) o).trim();
		boolean on = c.getMainWindowBooleanOption(OPTION_ENABLED, false);
		int lines = RepeatedLineDimmer.clampWindow(
				c.getMainWindowIntegerOption(OPTION_WINDOW,
						RepeatedLineDimmer.DEFAULT_WINDOW));
		int strength = RepeatedLineDimmer.clampStrength(
				c.getMainWindowIntegerOption(OPTION_STRENGTH,
						RepeatedLineDimmer.DEFAULT_STRENGTH));

		if (arg.length() == 0) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ statusLine(on, lines, strength) + "\n"
					+ usage()
					+ "Also: Options → Window → Dim repeated lines?\n");
			return null;
		}

		String[] parts = arg.split("\\s+");
		String first = parts[0].toLowerCase();

		Boolean desired = parseOnOff(first);
		if ("toggle".equals(first) && parts.length == 1) {
			desired = Boolean.valueOf(!on);
		}
		if (desired != null && parts.length == 1) {
			if (desired.booleanValue() == on) {
				c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
						+ "Dim repeated lines already "
						+ (desired.booleanValue() ? "on" : "off") + ".\n");
				return null;
			}
			if (!c.updateMainWindowBooleanOption(OPTION_ENABLED,
					desired.booleanValue())) {
				c.sendDataToWindow(getErrorMessage("Dimrepeat command error",
						"There is no game window to change yet."));
				return null;
			}
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Dim repeated lines "
					+ (desired.booleanValue() ? "on" : "off") + ".\n");
			return null;
		}

		if (isLinesWord(first)) {
			if (parts.length < 2) {
				c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
						+ "Remembering the last " + lines + " long lines.\n"
						+ "Usage: .dimrepeat lines "
						+ RepeatedLineDimmer.MIN_WINDOW + ".."
						+ RepeatedLineDimmer.MAX_WINDOW
						+ " (default " + RepeatedLineDimmer.DEFAULT_WINDOW
						+ "). After that many other long lines, an old room is bright again.\n");
				return null;
			}
			Integer n = parseInt(parts[1]);
			if (n == null) {
				c.sendDataToWindow(getErrorMessage("Dimrepeat command usage:",
						usage()));
				return null;
			}
			int use = RepeatedLineDimmer.clampWindow(n.intValue());
			if (!c.updateMainWindowIntegerOption(OPTION_WINDOW, use)) {
				c.sendDataToWindow(getErrorMessage("Dimrepeat command error",
						"There is no game window to change yet."));
				return null;
			}
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Remembering the last " + use + " long lines"
					+ (on ? "." : " (dimming is off).") + "\n");
			return null;
		}

		if (isStrengthWord(first)) {
			if (parts.length < 2) {
				c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
						+ strengthLine(strength) + "\n"
						+ "Usage: .dimrepeat strength "
						+ RepeatedLineDimmer.MIN_STRENGTH + ".."
						+ RepeatedLineDimmer.MAX_STRENGTH
						+ " (default " + RepeatedLineDimmer.DEFAULT_STRENGTH
						+ "). Higher is darker.\n");
				return null;
			}
			Integer n = parseInt(parts[1]);
			if (n == null) {
				c.sendDataToWindow(getErrorMessage("Dimrepeat command usage:",
						usage()));
				return null;
			}
			int use = RepeatedLineDimmer.clampStrength(n.intValue());
			if (!c.updateMainWindowIntegerOption(OPTION_STRENGTH, use)) {
				c.sendDataToWindow(getErrorMessage("Dimrepeat command error",
						"There is no game window to change yet."));
				return null;
			}
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ strengthLine(use) + "\n");
			return null;
		}

		c.sendDataToWindow(getErrorMessage("Dimrepeat command usage:", usage()));
		return null;
	}

	static String statusLine(final boolean on, final int lines,
			final int strength) {
		return "Dim repeated lines is " + (on ? "on" : "off")
				+ ". Remember last " + lines + " long lines. "
				+ strengthLine(strength);
	}

	static String strengthLine(final int strength) {
		int keep = 100 - RepeatedLineDimmer.clampStrength(strength);
		return "Strength " + RepeatedLineDimmer.clampStrength(strength)
				+ "% — repeated lines keep " + keep + "% brightness.";
	}

	static String usage() {
		return "Usage: .dimrepeat on | off | toggle\n"
				+ "       .dimrepeat lines "
				+ RepeatedLineDimmer.MIN_WINDOW + ".."
				+ RepeatedLineDimmer.MAX_WINDOW + "\n"
				+ "       .dimrepeat strength "
				+ RepeatedLineDimmer.MIN_STRENGTH + ".."
				+ RepeatedLineDimmer.MAX_STRENGTH + "\n";
	}

	static boolean isLinesWord(final String token) {
		return "lines".equals(token) || "window".equals(token)
				|| "remember".equals(token);
	}

	static boolean isStrengthWord(final String token) {
		return "strength".equals(token) || "power".equals(token)
				|| "dim".equals(token);
	}

	static Boolean parseOnOff(final String token) {
		if (token == null) {
			return null;
		}
		if (token.equals("on") || token.equals("true") || token.equals("1")
				|| token.equals("yes")) {
			return Boolean.TRUE;
		}
		if (token.equals("off") || token.equals("false") || token.equals("0")
				|| token.equals("no")) {
			return Boolean.FALSE;
		}
		return null;
	}

	static Integer parseInt(final String token) {
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
