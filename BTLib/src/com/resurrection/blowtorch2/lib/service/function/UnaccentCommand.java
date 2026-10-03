package com.resurrection.blowtorch2.lib.service.function;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BaseOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BooleanOption;

/** Special command for outbound accent folding: {@code .unaccent [on|off]}. */
public class UnaccentCommand extends SpecialCommand {

	public static final String OPTION_KEY = "unaccent_send";

	public UnaccentCommand() {
		this.commandName = "unaccent";
	}

	public Object execute(Object o, Connection c) {
		String arg = o == null ? "" : ((String) o).trim();
		BooleanOption opt = findOption(c);
		if (opt == null) {
			c.sendDataToWindow(getErrorMessage("Unaccent command error",
					"Strip accents when sending is not available yet."));
			return null;
		}

		boolean current = ((Boolean) opt.getValue()).booleanValue();
		if (arg.length() == 0) {
			String state = current ? "on" : "off";
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Strip accents when sending (.unaccent) is currently " + state + ".\n"
					+ "Usage: .unaccent on | .unaccent off\n"
					+ "Also: Options → Input → Strip accents when sending\n");
			return null;
		}

		String token = arg.toLowerCase().split("\\s+")[0];
		Boolean desired = parseOnOff(token);
		if (desired == null) {
			c.sendDataToWindow(getErrorMessage("Unaccent command usage:",
					".unaccent on | .unaccent off\n"
							+ "Strips accents on the way to the game (usiądź przy stole → usiadz przy stole).\n"
							+ "Local echo shows the folded form; the input bar still shows what you typed. Off by default.\n"
							+ "Also available under Options → Input → Strip accents when sending"));
			return null;
		}

		if (desired.booleanValue() == current) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Strip accents when sending already " + (desired ? "on" : "off") + ".\n");
			return null;
		}

		c.updateBooleanSetting(OPTION_KEY, desired.booleanValue());
		c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
				+ "Strip accents when sending " + (desired.booleanValue() ? "on" : "off") + ".\n");
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
