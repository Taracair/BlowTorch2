package com.resurrection.blowtorch2.lib.service.function;

import android.os.RemoteException;

import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BaseOption;

public class FullScreenCommand extends SpecialCommand {
	public FullScreenCommand() {
		this.commandName = "togglefullscreen";
	}
	
	public Object execute(Object o,Connection c) {
		Boolean current = (Boolean)((BaseOption)c.getSettings().findOptionByKey("fullscreen")).getValue();
		c.getSettings().setOption("fullscreen", ((Boolean)!current).toString());
		//c.service.doExecuteFullscreen();
		return null;
	}
}

