package com.resurrection.blowtorch2.lib.service.function;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;

public class DisconnectCommand extends SpecialCommand {
	
	public DisconnectCommand() {
		this.commandName = "disconnect";
	}
	public Object execute(Object o,Connection c) {
		String msg = "\n" + Colorizer.getRedColor() + "Disconnected." + Colorizer.getWhiteColor() + "\n";
		c.sendDataToWindow(msg);
		c.disconnectByUser();
		return null;
	}
}
