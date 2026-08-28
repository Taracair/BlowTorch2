package com.resurrection.blowtorch2.lib.service.function;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;

/**
 * Light paper in the game window: {@code .light}, {@code .light on|off|toggle}.
 *
 * <p>Same preference as Options → Window → Light theme?.
 */
public class LightCommand extends SpecialCommand {

	public static final String OPTION_KEY = "light_paper";

	public LightCommand() {
		this.commandName = "light";
	}

	public Object execute(Object o, Connection c) {
		String arg = o == null ? "" : ((String) o).trim();
		boolean on = c.getMainWindowBooleanOption(OPTION_KEY, false);

		if (arg.length() == 0) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Light theme is " + (on ? "on" : "off") + ".\n"
					+ usage()
					+ "Also: Options → Window → Light theme?\n");
			return null;
		}

		String token = arg.toLowerCase().split("\\s+")[0];
		Boolean desired = DimRepeatCommand.parseOnOff(token);
		if ("toggle".equals(token)) {
			desired = Boolean.valueOf(!on);
		}
		if (desired == null) {
			c.sendDataToWindow(getErrorMessage("Light command usage:", usage()));
			return null;
		}
		if (desired.booleanValue() == on) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Light theme already "
					+ (desired.booleanValue() ? "on" : "off") + ".\n");
			return null;
		}
		if (!c.updateMainWindowBooleanOption(OPTION_KEY, desired.booleanValue())) {
			c.sendDataToWindow(getErrorMessage("Light command error",
					"There is no game window to change yet."));
			return null;
		}
		c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
				+ "Light theme "
				+ (desired.booleanValue() ? "on" : "off") + ".\n");
		return null;
	}

	static String usage() {
		return "Usage: .light on | off | toggle\n"
				+ "Light grey paper and dark ink. Game colours stay; "
				+ "whites and light greys are darkened so they stay readable.\n";
	}
}
