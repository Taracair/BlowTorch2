package com.resurrection.blowtorch2.lib.service.function;

import android.os.RemoteException;

import com.resurrection.blowtorch2.lib.service.Connection;

public class DirtyExitCommand extends SpecialCommand {
	public DirtyExitCommand() {
		this.commandName = "closewindow";
	}
	public Object execute(Object o,Connection c) {
		
		c.getService().doDirtyExit();
		return null;
	}
}
