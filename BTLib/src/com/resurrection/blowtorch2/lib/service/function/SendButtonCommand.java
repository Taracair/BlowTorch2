package com.resurrection.blowtorch2.lib.service.function;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BaseOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BooleanOption;

/**
 * Show/hide the input-bar Send button: {@code .sendbutton [on|off]}.
 * Same preference as Options → Window → Show Send button?
 */
public class SendButtonCommand extends SpecialCommand {

	public static final String OPTION_KEY = "input_bar_show_send";

	public SendButtonCommand() {
		this.commandName = "sendbutton";
	}

	public Object execute(Object o, Connection c) {
		String arg = o == null ? "" : ((String) o).trim();
		BooleanOption opt = findOption(c);
		if (opt == null) {
			c.sendDataToWindow(getErrorMessage("Sendbutton command error",
					"Show Send button option is not available yet."));
			return null;
		}

		boolean current = ((Boolean) opt.getValue()).booleanValue();
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

		c.updateBooleanSetting(OPTION_KEY, desired.booleanValue());
		c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
				+ "Send button " + (desired.booleanValue() ? "on" : "off") + ".\n");
		return null;
	}

	private static BooleanOption findOption(Connection c) {
		if (c == null || c.getSettings() == null) {
			return null;
		}
		BaseOption o = (BaseOption) c.getSettings().findOptionByKey(OPTION_KEY);
		if (o instanceof BooleanOption) {
			return (BooleanOption) o;
		}
		return null;
	}
}
