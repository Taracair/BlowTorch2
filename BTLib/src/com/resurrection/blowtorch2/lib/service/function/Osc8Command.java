package com.resurrection.blowtorch2.lib.service.function;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;

/**
 * OSC 8 hyperlinks from the input bar: {@code .osc8}, {@code .osc8 on|off}.
 *
 * <p>Same preference as Options → Window → Hyperlink Settings → OSC 8 links?.
 * Window option, not a connection option — see
 * {@link Connection#updateMainWindowBooleanOption}.
 */
public class Osc8Command extends SpecialCommand {

	public static final String OPTION_KEY = "osc8_links";

	public Osc8Command() {
		this.commandName = "osc8";
	}

	public Object execute(Object o, Connection c) {
		String arg = o == null ? "" : ((String) o).trim();
		boolean on = c.getMainWindowBooleanOption(OPTION_KEY, true);

		if (arg.length() == 0) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "OSC 8 links are " + (on ? "on" : "off") + ".\n"
					+ "Usage: .osc8 on | .osc8 off\n"
					+ "Server-declared hyperlinks (ESC ]8;params;URI). "
					+ "Tap the marked words. Display text need not be the URL. "
					+ "http(s)/mailto/ftp open the browser; send: types a command; "
					+ "prompt: fills the input bar (StickMUD / Mudlet).\n"
					+ "Also: Options → Window → Hyperlink Settings → OSC 8 links?\n"
					+ "Sample without a MUD: .probe osc8\n");
			return null;
		}

		String token = arg.toLowerCase().split("\\s+")[0];
		Boolean desired = parseOnOff(token);
		if (desired == null) {
			c.sendDataToWindow(getErrorMessage("Osc8 command usage:",
					".osc8 on | .osc8 off\n"
							+ "Controls OSC 8 hyperlinks in this window.\n"
							+ "Also: Options → Window → Hyperlink Settings → OSC 8 links?"));
			return null;
		}

		if (desired.booleanValue() == on) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "OSC 8 links already " + (desired.booleanValue() ? "on" : "off")
					+ ".\n");
			return null;
		}

		if (!c.updateMainWindowBooleanOption(OPTION_KEY, desired.booleanValue())) {
			c.sendDataToWindow(getErrorMessage("Osc8 command error",
					"There is no game window to change yet."));
			return null;
		}
		c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
				+ "OSC 8 links " + (desired.booleanValue() ? "on" : "off") + ".\n");
		return null;
	}

	private static Boolean parseOnOff(final String token) {
		if (token == null) {
			return null;
		}
		if (token.equals("on") || token.equals("true") || token.equals("1")
				|| token.equals("yes")) {
			return Boolean.TRUE;
		}
		if (token.equals("off") || token.equals("false") || token.equals("0")
				|| token.equals("no")) {
			return Boolean.FALSE;
		}
		return null;
	}
}
