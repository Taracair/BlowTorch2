package com.resurrection.blowtorch2.lib.service.function;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.service.WindowToken;

/**
 * Date overlay while scrolled into history: {@code .when}, {@code .when on|off},
 * {@code .when opacity N}.
 *
 * <p>Same preference as Options → Window → Scroll dates?
 */
public class WhenCommand extends SpecialCommand {

	public static final String OPTION_ENABLED = "scroll_dates";
	public static final String OPTION_OPACITY = "scroll_dates_opacity";

	public WhenCommand() {
		this.commandName = "when";
	}

	public Object execute(Object o, Connection c) {
		String arg = o == null ? "" : ((String) o).trim();
		boolean on = c.getMainWindowBooleanOption(OPTION_ENABLED, false);
		int opacity = WindowToken.clampScrollDatesOpacity(
				c.getMainWindowIntegerOption(OPTION_OPACITY,
						WindowToken.DEFAULT_SCROLL_DATES_OPACITY));

		if (arg.length() == 0) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Scroll dates is " + (on ? "on" : "off")
					+ ", opacity " + opacity + "%.\n"
					+ usage()
					+ "Also: Options → Window → Scroll dates?\n");
			return null;
		}

		String[] parts = arg.split("\\s+");
		String first = parts[0].toLowerCase();
		if ("opacity".equals(first)) {
			if (parts.length < 2) {
				c.sendDataToWindow(getErrorMessage("When command usage:", usage()));
				return null;
			}
			try {
				int next = WindowToken.clampScrollDatesOpacity(Integer.parseInt(parts[1]));
				if (next == opacity) {
					c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
							+ "Scroll date opacity already " + next + "%.\n");
					return null;
				}
				if (!c.updateMainWindowIntegerOption(OPTION_OPACITY, next)) {
					c.sendDataToWindow(getErrorMessage("When command error",
							"There is no game window to change yet."));
					return null;
				}
				c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
						+ "Scroll date opacity " + next + "%.\n");
			} catch (NumberFormatException e) {
				c.sendDataToWindow(getErrorMessage("When command usage:", usage()));
			}
			return null;
		}

		Boolean desired = DimRepeatCommand.parseOnOff(first);
		if ("toggle".equals(first)) {
			desired = Boolean.valueOf(!on);
		}
		if (desired == null) {
			c.sendDataToWindow(getErrorMessage("When command usage:", usage()));
			return null;
		}
		if (desired.booleanValue() == on) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Scroll dates already "
					+ (desired.booleanValue() ? "on" : "off") + ".\n");
			return null;
		}
		if (!c.updateMainWindowBooleanOption(OPTION_ENABLED, desired.booleanValue())) {
			c.sendDataToWindow(getErrorMessage("When command error",
					"There is no game window to change yet."));
			return null;
		}
		c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
				+ "Scroll dates "
				+ (desired.booleanValue() ? "on" : "off") + ".\n");
		return null;
	}

	static String usage() {
		return "Usage: .when on | off | toggle | opacity N\n"
				+ "While scrolled into history: day and time to the left of ⋮, "
				+ "and .search 14:32 or 18 Aug jumps there.\n";
	}
}
