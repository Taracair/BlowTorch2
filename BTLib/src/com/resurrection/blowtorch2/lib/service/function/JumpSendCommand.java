package com.resurrection.blowtorch2.lib.service.function;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;

/**
 * After a send, jump to the live edge: {@code .jumpsend on|off}.
 *
 * <p>Default on. Incoming text near the live edge is unchanged.
 */
public class JumpSendCommand extends SpecialCommand {

	public static final String OPTION_KEY = "jump_on_send";

	public JumpSendCommand() {
		this.commandName = "jumpsend";
	}

	public Object execute(Object o, Connection c) {
		String arg = o == null ? "" : ((String) o).trim();
		boolean on = c.getMainWindowBooleanOption(OPTION_KEY, true);

		if (arg.length() == 0) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Jump to the live edge after a send is currently "
					+ (on ? "on" : "off") + ".\n"
					+ usage()
					+ "Also: Options → Window → Jump to the live edge when you send?\n");
			return null;
		}

		String first = arg.toLowerCase().split("\\s+")[0];
		Boolean desired = DimRepeatCommand.parseOnOff(first);
		if ("toggle".equals(first)) {
			desired = Boolean.valueOf(!on);
		}
		if (desired == null) {
			c.sendDataToWindow(getErrorMessage("Jumpsend command usage:",
					usage()));
			return null;
		}
		if (desired.booleanValue() == on) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Jump to the live edge after a send already "
					+ (desired.booleanValue() ? "on" : "off") + ".\n");
			return null;
		}
		if (!c.updateMainWindowBooleanOption(OPTION_KEY, desired.booleanValue())) {
			c.sendDataToWindow(getErrorMessage("Jumpsend command error",
					"There is no game window to change yet."));
			return null;
		}
		c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
				+ "Jump to the live edge after a send "
				+ (desired.booleanValue() ? "on" : "off") + ".\n");
		return null;
	}

	static String usage() {
		return "Usage: .jumpsend on | off | toggle\n"
				+ "After you send a line, scroll to the newest text. On by default.\n"
				+ "Incoming text near the live edge still snaps there.\n";
	}
}
