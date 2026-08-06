package com.resurrection.blowtorch2.lib.service.function;

import java.util.Locale;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;

/**
 * {@code .complete on|off} — suggest words the game has just used while you
 * type.
 *
 * <p>Off by default, and while off the incoming text is not sent to the UI for
 * this at all, so it costs nothing.
 */
public class CompleteCommand extends SpecialCommand {

	public CompleteCommand() {
		this.commandName = "complete";
	}

	@Override
	public Object execute(Object o, Connection c) {
		String arg = o == null ? "" : ((String) o).trim().toLowerCase(Locale.US);
		if (arg.equals("on")) {
			c.setWordComplete(true);
			c.sendDataToWindow("\n" + Colorizer.getBrightCyanColor()
					+ "Word completion on. Type two letters of something the game"
					+ " said and it appears above the input bar; tap to use it."
					+ Colorizer.getWhiteColor() + "\n");
			return null;
		}
		if (arg.equals("off")) {
			c.setWordComplete(false);
			c.sendDataToWindow("\n" + Colorizer.getBrightCyanColor()
					+ "Word completion off." + Colorizer.getWhiteColor() + "\n");
			return null;
		}
		if (arg.length() == 0 || arg.equals("status")) {
			c.sendDataToWindow("\nWord completion is "
					+ (c.isWordComplete() ? "on" : "off")
					+ ". Use .complete on|off\n");
			return null;
		}
		c.sendDataToWindow(getErrorMessage("Word completion usage:",
				".complete on   — suggest words the game just used\n"
				+ ".complete off  — stop\n"
				+ ".complete      — say which it is\n\n"
				+ "This completes mob names, player names and item words the\n"
				+ "keyboard will never know, and would rather correct into\n"
				+ "English. Type \"k gri\" after a grizzled cave troll walks in.\n"));
		return null;
	}
}
