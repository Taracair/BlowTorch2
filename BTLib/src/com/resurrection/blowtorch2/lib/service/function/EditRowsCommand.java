package com.resurrection.blowtorch2.lib.service.function;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;

/**
 * Landscape Edit strip on two rows: {@code .editrows on|off}.
 *
 * <p>Default off. Portrait and the off state stay one full-width row.
 */
public class EditRowsCommand extends SpecialCommand {

	public static final String OPTION_KEY = "input_edit_tools_two_rows";

	public EditRowsCommand() {
		this.commandName = "editrows";
	}

	public Object execute(Object o, Connection c) {
		String arg = o == null ? "" : ((String) o).trim();
		boolean on = c.getMainWindowBooleanOption(OPTION_KEY, false);

		if (arg.length() == 0) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Two-row Edit strip in landscape is currently "
					+ (on ? "on" : "off") + ".\n"
					+ usage()
					+ "Also: Options → Window → Edit strip: two rows in landscape?\n");
			return null;
		}

		String first = arg.toLowerCase().split("\\s+")[0];
		Boolean desired = DimRepeatCommand.parseOnOff(first);
		if ("toggle".equals(first)) {
			desired = Boolean.valueOf(!on);
		}
		if (desired == null) {
			c.sendDataToWindow(getErrorMessage("Editrows command usage:", usage()));
			return null;
		}
		if (desired.booleanValue() == on) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Two-row Edit strip in landscape already "
					+ (desired.booleanValue() ? "on" : "off") + ".\n");
			return null;
		}
		if (!c.updateMainWindowBooleanOption(OPTION_KEY, desired.booleanValue())) {
			c.sendDataToWindow(getErrorMessage("Editrows command error",
					"There is no game window to change yet."));
			return null;
		}
		c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
				+ "Two-row Edit strip in landscape "
				+ (desired.booleanValue() ? "on" : "off") + ".\n");
		return null;
	}

	static String usage() {
		return "Usage: .editrows on | off | toggle\n"
				+ "Landscape only. Off (the default) keeps one full-width row.\n";
	}
}
