package com.resurrection.blowtorch2.lib.service.function;

import java.io.UnsupportedEncodingException;

import android.os.RemoteException;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;

public class DisconnectCommand extends SpecialCommand {
	
	public DisconnectCommand() {
		this.commandName = "disconnect";
	}
	public Object execute(Object o,Connection c) {
		
		
		//myhandler.sendEmptyMessage(MESSAGE_DODISCONNECT);
		String msg = "\n" + Colorizer.getRedColor() + "Disconnected." + Colorizer.getWhiteColor() + "\n";
		c.sendDataToWindow(msg);
		return null;
	}
}
