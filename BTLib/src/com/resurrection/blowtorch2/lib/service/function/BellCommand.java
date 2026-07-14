package com.resurrection.blowtorch2.lib.service.function;

import com.resurrection.blowtorch2.lib.service.Connection;

public class BellCommand extends SpecialCommand {
	public BellCommand() {
		this.commandName = "dobell";
	}
	public Object execute(Object o,Connection c) {
		
		c.getHandler().sendEmptyMessage(Connection.MESSAGE_BELLINC);
		
		return null;
		
	}
}