package com.resurrection.blowtorch2.lib.service.function;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;

public class DisconnectCommand extends SpecialCommand {
	
	public DisconnectCommand() {
		this.commandName = "disconnect";
	}
	/** Blank closes the connection. Any other word does not. */
	static boolean isBare(Object o) {
		if (o == null) {
			return true;
		}
		return o.toString().trim().length() == 0;
	}

	public Object execute(Object o,Connection c) {
		if (!isBare(o)) {
			c.sendDataToWindow(getErrorMessage("Disconnect command usage:",
					".disconnect        — close this connection (no arguments)"));
			return null;
		}
		String msg = "\n" + Colorizer.getRedColor() + "Disconnected." + Colorizer.getWhiteColor() + "\n";
		c.sendDataToWindow(msg);
		c.disconnectByUser();
		return null;
	}
}
