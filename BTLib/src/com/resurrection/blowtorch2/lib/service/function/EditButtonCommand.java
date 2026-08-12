package com.resurrection.blowtorch2.lib.service.function;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;

/**
 * Show/hide the input-bar Edit button: {@code .editbutton [on|off]}.
 * Same preference as Options → Window → Show Edit button?
 * Per-world ({@code WindowToken}), not app-wide.
 */
public class EditButtonCommand extends SpecialCommand {

	public static final String OPTION_KEY = "input_bar_show_edit";

	public EditButtonCommand() {
		this.commandName = "editbutton";
	}

	public Object execute(Object o, Connection c) {
		String arg = o == null ? "" : ((String) o).trim();
		boolean current = c.getMainWindowBooleanOption(OPTION_KEY, true);
		if (arg.length() == 0) {
			String state = current ? "on" : "off";
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Edit button (.editbutton) is currently " + state + ".\n"
					+ "Usage: .editbutton on | .editbutton off\n"
					+ "Also: Options → Window → Show Edit button?\n"
					+ "Edit tools panel: .editpanel on|off\n");
			return null;
		}

		Boolean desired = parseOnOff(arg.toLowerCase().split("\\s+")[0]);
		if (desired == null) {
			c.sendDataToWindow(getErrorMessage("Editbutton command usage:",
					".editbutton on | .editbutton off\n"
							+ "Also: Options → Window → Show Edit button?"));
			return null;
		}

		if (desired.booleanValue() == current) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Edit button already " + (desired ? "on" : "off") + ".\n");
			return null;
		}

		if (!c.updateMainWindowBooleanOption(OPTION_KEY, desired.booleanValue())) {
			c.sendDataToWindow(getErrorMessage("Editbutton command error",
					"There is no game window to change yet."));
			return null;
		}
		c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
				+ "Edit button " + (desired.booleanValue() ? "on" : "off") + ".\n");
		return null;
	}

	/** Strict {@code on}/{@code off} only — no synonym aliases. */
	static Boolean parseOnOff(String token) {
		if (token == null) {
			return null;
		}
		if (token.equals("on")) {
			return Boolean.TRUE;
		}
		if (token.equals("off")) {
			return Boolean.FALSE;
		}
		return null;
	}
}
