package com.resurrection.blowtorch2.lib.service.function;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BaseOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BooleanOption;

/** Special command for input-bar growth: {@code .wrap [on|off]}. */
public class WrapCommand extends SpecialCommand {

	public static final String OPTION_KEY = "grow_input_bar";

	public WrapCommand() {
		this.commandName = "wrap";
	}

	public Object execute(Object o, Connection c) {
		String arg = o == null ? "" : ((String) o).trim();
		BooleanOption opt = findGrowOption(c);
		if (opt == null) {
			c.sendDataToWindow(getErrorMessage("Wrap command error",
					"Grow Input Bar option is not available yet."));
			return null;
		}

		boolean current = ((Boolean) opt.getValue()).booleanValue();
		if (arg.length() == 0) {
			String state = current ? "on" : "off";
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Input bar growth (.wrap) is currently " + state + ".\n"
					+ "Usage: .wrap on | .wrap off\n"
					+ "Also: Options → Typing → Grow Input Bar?\n");
			return null;
		}

		Boolean desired = parseArgument(arg);
		if (desired == null) {
			c.sendDataToWindow(getErrorMessage("Wrap command usage:",
					".wrap on | .wrap off\n"
							+ "Controls whether the input bar grows with multiline text.\n"
							+ "Also available under Options → Typing → Grow Input Bar?"));
			return null;
		}

		if (desired.booleanValue() == current) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Input bar growth already " + (desired ? "on" : "off") + ".\n");
			return null;
		}

		c.updateBooleanSetting(OPTION_KEY, desired.booleanValue());
		c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
				+ "Input bar growth " + (desired.booleanValue() ? "on" : "off") + ".\n");
		return null;
	}

	private static BooleanOption findGrowOption(Connection c) {
		if (c == null || c.getSettings() == null) {
			return null;
		}
		BaseOption o = (BaseOption) c.getSettings().findOptionByKey(OPTION_KEY);
		if (o instanceof BooleanOption) {
			return (BooleanOption) o;
		}
		return null;
	}

	/** One word. {@code on}/{@code off} and the synonyms this command already takes. */
	static Boolean parseArgument(String arg) {
		if (arg == null) {
			return null;
		}
		String token = arg.trim().toLowerCase(java.util.Locale.US);
		if (token.length() == 0) {
			return null;
		}
		for (int i = 0; i < token.length(); i++) {
			if (Character.isWhitespace(token.charAt(i))) {
				return null;
			}
		}
		return parseOnOff(token);
	}

	private static Boolean parseOnOff(String token) {
		if (token == null) {
			return null;
		}
		if (token.equals("on") || token.equals("true") || token.equals("1") || token.equals("yes")) {
			return Boolean.TRUE;
		}
		if (token.equals("off") || token.equals("false") || token.equals("0") || token.equals("no")) {
			return Boolean.FALSE;
		}
		return null;
	}
}
