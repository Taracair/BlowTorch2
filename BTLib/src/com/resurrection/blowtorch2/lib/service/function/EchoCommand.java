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

	@Override
	public Object execute(final Object o, final Connection c) {
		String arg = o == null ? "" : ((String) o).trim().toLowerCase(Locale.US);
		if (arg.equals("help") || arg.equals("?")) {
			c.sendDataToWindow(help());
			return null;
		}
		if (arg.equals("on")) {
			c.setTelnetEchoFromCommand(true);
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Input bar unmasked. The server may still be echoing.\n");
			return null;
		}
		if (arg.equals("off")) {
			c.setTelnetEchoFromCommand(false);
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor() + "Input bar masked.\n");
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
