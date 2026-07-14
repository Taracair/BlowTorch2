package com.resurrection.blowtorch2.lib.service.function;

import java.io.UnsupportedEncodingException;

import android.os.RemoteException;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;

public class ReconnectCommand extends SpecialCommand {
	public ReconnectCommand() {
		this.commandName = "reconnect";
	}
	public Object execute(Object o,Connection c) {
		
		
		//myhandler.sendEmptyMessage(MESSAGE_RECONNECT);
		String msg = "\n" + Colorizer.getRedColor() + "Reconnecting . . ." + Colorizer.getWhiteColor() + "\n";
		c.sendDataToWindow(msg);
		return null;
	}
}
