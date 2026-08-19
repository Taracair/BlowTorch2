package com.resurrection.blowtorch2.lib.service.function;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;

/**
 * Date overlay while scrolled into history: {@code .when}, {@code .when on|off}.
 *
 * <p>Same preference as Options → Window → Scroll dates?
 */
public class WhenCommand extends SpecialCommand {

	public static final String OPTION_ENABLED = "scroll_dates";

	public WhenCommand() {
		this.commandName = "when";
	}

	public Object execute(Object o, Connection c) {
		String arg = o == null ? "" : ((String) o).trim();
		boolean on = c.getMainWindowBooleanOption(OPTION_ENABLED, false);

		if (arg.length() == 0) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Scroll dates is " + (on ? "on" : "off") + ".\n"
					+ usage()
					+ "Also: Options → Window → Scroll dates?\n");
			return null;
		}

		String first = arg.split("\\s+")[0].toLowerCase();
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
		return "Usage: .when on | off | toggle\n"
				+ "While scrolled into history: day and time next to the jump-to-live "
				+ "arrow, and .search 14:32 or 18 Aug jumps there.\n";
	}
}
