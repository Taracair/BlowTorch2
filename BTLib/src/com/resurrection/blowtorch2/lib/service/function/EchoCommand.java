package com.resurrection.blowtorch2.lib.service.function;

import java.util.Locale;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;

/**
 * Escape hatch for telnet ECHO (option 1). The server owns the masking, and a
 * server that takes echoing over and never hands it back would leave the input
 * bar hiding what is typed for the rest of the session with nothing the player
 * could do about it.
 */
public class EchoCommand extends SpecialCommand {

	public EchoCommand() {
		this.commandName = "echo";
	}

	static final int ECHO_STATUS = 0;
	static final int ECHO_ON = 1;
	static final int ECHO_OFF = 2;
	static final int ECHO_HELP = 3;
	static final int ECHO_BAD = 4;

	/** Blank is the state line. One word {@code on} or {@code off}. Anything else is usage. */
	static int classify(String raw) {
		String arg = raw == null ? "" : raw.trim().toLowerCase(Locale.US);
		if (arg.length() == 0) {
			return ECHO_STATUS;
		}
		if (arg.equals("help") || arg.equals("?")) {
			return ECHO_HELP;
		}
		if (arg.equals("on")) {
			return ECHO_ON;
		}
		if (arg.equals("off")) {
			return ECHO_OFF;
		}
		return ECHO_BAD;
	}

	@Override
	public Object execute(final Object o, final Connection c) {
		int kind = classify(o == null ? "" : o.toString());
		if (kind == ECHO_HELP) {
			c.sendDataToWindow(help());
			return null;
		}
		if (kind == ECHO_ON) {
			c.setTelnetEchoFromCommand(true);
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Input bar unmasked. The server may still be echoing.\n");
			return null;
		}
		if (kind == ECHO_OFF) {
			c.setTelnetEchoFromCommand(false);
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor() + "Input bar masked.\n");
			return null;
		}
		if (kind == ECHO_BAD) {
			c.sendDataToWindow(getErrorMessage("Echo command usage:",
					".echo              — say whether the input bar is masked\n"
							+ ".echo on|off       — show or hide what you type"));
			return null;
		}
		c.sendDataToWindow("\n" + Colorizer.getWhiteColor() + "Input bar is currently "
				+ (c.isTelnetEchoLocal() ? "visible" : "masked")
				+ " (telnet ECHO " + (c.isTelnetEchoLocal() ? "not held" : "held")
				+ " by the server).\n" + help());
		return null;
	}

	private String help() {
		return "Usage: .echo [on|off]\n"
				+ "  on  — show what you type, even if the server holds telnet ECHO\n"
				+ "  off — hide it\n"
				+ "The server sets this by itself at a password prompt; the next\n"
				+ "change from the server wins over this command.\n";
	}
}
