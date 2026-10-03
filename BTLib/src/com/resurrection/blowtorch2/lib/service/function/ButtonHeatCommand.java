package com.resurrection.blowtorch2.lib.service.function;

import com.resurrection.blowtorch2.lib.service.Connection;

/** In-memory button press counts. The numbers live in the button window. */
public class ButtonHeatCommand extends SpecialCommand {
	public ButtonHeatCommand() {
		this.commandName = "heatmap";
	}

	public Object execute(Object o, Connection c) {
		String arg = o == null ? "" : ((String) o).trim().toLowerCase(java.util.Locale.US);
		c.getService().doButtonHeat(arg);
		return null;
	}
}
