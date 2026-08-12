package com.resurrection.blowtorch2.lib.service.function;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;

/**
 * Show/hide the input-bar Send button: {@code .sendbutton [on|off]}.
 * Same preference as Options → Window → Show Send button?
 * Per-world ({@code WindowToken}), not app-wide.
 */
public class SendButtonCommand extends SpecialCommand {

	public static final String OPTION_KEY = "input_bar_show_send";

	public SendButtonCommand() {
		this.commandName = "sendbutton";
	}

	public Object execute(Object o, Connection c) {
		String arg = o == null ? "" : ((String) o).trim();
		boolean current = c.getMainWindowBooleanOption(OPTION_KEY, true);
		if (arg.length() == 0) {
			String state = current ? "on" : "off";
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Send button (.sendbutton) is currently " + state + ".\n"
					+ "Usage: .sendbutton on | .sendbutton off\n"
					+ "Also: Options → Window → Show Send button?\n"
					+ "When off, send with keyboard Send/Enter or .kb flush.\n");
			return null;
		}

		Boolean desired = EditButtonCommand.parseOnOff(arg.toLowerCase().split("\\s+")[0]);
		if (desired == null) {
			c.sendDataToWindow(getErrorMessage("Sendbutton command usage:",
					".sendbutton on | .sendbutton off\n"
							+ "Also: Options → Window → Show Send button?\n"
							+ "When off, send with keyboard Send/Enter or .kb flush."));
			return null;
		}

		if (desired.booleanValue() == current) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Send button already " + (desired ? "on" : "off") + ".\n");
			return null;
		}

		if (!c.updateMainWindowBooleanOption(OPTION_KEY, desired.booleanValue())) {
			c.sendDataToWindow(getErrorMessage("Sendbutton command error",
					"There is no game window to change yet."));
			return null;
		}
		c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
				+ "Send button " + (desired.booleanValue() ? "on" : "off") + ".\n");
		return null;
	}
}
