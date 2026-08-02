package com.resurrection.blowtorch2.lib.service.function;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BaseOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BooleanOption;

/**
 * Show/hide the input-bar Edit button: {@code .editbutton [on|off]}.
 * Same preference as Options → Window → Show Edit button?
 */
public class EditButtonCommand extends SpecialCommand {

	public static final String OPTION_KEY = "input_bar_show_edit";

	public EditButtonCommand() {
		this.commandName = "editbutton";
	}

	public Object execute(Object o, Connection c) {
		String arg = o == null ? "" : ((String) o).trim();
		BooleanOption opt = findOption(c);
		if (opt == null) {
			c.sendDataToWindow(getErrorMessage("Editbutton command error",
					"Show Edit button option is not available yet."));
			return null;
		}

		boolean current = ((Boolean) opt.getValue()).booleanValue();
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

		c.updateBooleanSetting(OPTION_KEY, desired.booleanValue());
		c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
				+ "Edit button " + (desired.booleanValue() ? "on" : "off") + ".\n");
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
