package com.resurrection.blowtorch2.lib.service.function;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;

public class ReconnectCommand extends SpecialCommand {
	public ReconnectCommand() {
		this.commandName = "reconnect";
	}
	public Object execute(Object o,Connection c) {
		String msg = "\n" + Colorizer.getRedColor() + "Reconnecting . . ." + Colorizer.getWhiteColor() + "\n";
		c.sendDataToWindow(msg);
		c.startReconnect();
		return null;
	}
}
