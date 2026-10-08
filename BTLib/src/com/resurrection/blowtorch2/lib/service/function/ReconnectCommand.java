package com.resurrection.blowtorch2.lib.service.function;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;

public class ReconnectCommand extends SpecialCommand {
	public ReconnectCommand() {
		this.commandName = "reconnect";
	}
	/** Blank reconnects. Any other word does not. */
	static boolean isBare(Object o) {
		if (o == null) {
			return true;
		}
		return o.toString().trim().length() == 0;
	}

	public Object execute(Object o,Connection c) {
		if (!isBare(o)) {
			c.sendDataToWindow(getErrorMessage("Reconnect command usage:",
					".reconnect         — close and open again (no arguments)"));
			return null;
		}
		String msg = "\n" + Colorizer.getRedColor() + "Reconnecting . . ." + Colorizer.getWhiteColor() + "\n";
		c.sendDataToWindow(msg);
		c.startReconnect();
		return null;
	}
}
