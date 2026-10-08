package com.resurrection.blowtorch2.lib.service.function;

import java.util.Locale;

import com.resurrection.blowtorch2.lib.service.Connection;

/** In-memory button press counts. The numbers live in the button window. */
public class ButtonHeatCommand extends SpecialCommand {

	static final int SHOW = 0;
	static final int OFF = 1;
	static final int RESET = 2;
	static final int BAD = 3;

	public ButtonHeatCommand() {
		this.commandName = "heatmap";
	}

	/** Blank shows the overlay. {@code off} and {@code reset} stay. Any other word does not. */
	static int classify(String raw) {
		String arg = raw == null ? "" : raw.trim().toLowerCase(Locale.US);
		if (arg.length() == 0) {
			return SHOW;
		}
		if (arg.equals("off")) {
			return OFF;
		}
		if (arg.equals("reset")) {
			return RESET;
		}
		return BAD;
	}

	public Object execute(Object o, Connection c) {
		String arg = o == null ? "" : ((String) o).trim().toLowerCase(Locale.US);
		int kind = classify(arg);
		if (kind == BAD) {
			c.sendDataToWindow(getErrorMessage("Heatmap command usage:",
					".heatmap           — show the heatmap and list the counts\n"
							+ ".heatmap off       — hide it; counting continues\n"
							+ ".heatmap reset     — forget this world's counts"));
			return null;
		}
		c.getService().doButtonHeat(arg);
		return null;
	}
}
