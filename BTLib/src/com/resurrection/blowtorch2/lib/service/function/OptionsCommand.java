package com.resurrection.blowtorch2.lib.service.function;

import java.util.Locale;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;

/**
 * Open the Options screen from the input bar, so it can go on a button.
 *
 * <p>The dialog lives in the UI process and this command runs in the service
 * one, so it asks rather than opens: the request goes out over the one-way
 * callback and the window builds the dialog on its own thread. If no window is
 * listening nothing happens, which is the same as the ⋮ menu not being on
 * screen to press.
 */
public class OptionsCommand extends SpecialCommand {

	public OptionsCommand() {
		this.commandName = "options";
	}

	@Override
	public Object execute(final Object o, final Connection c) {
		String arg = o == null ? "" : o.toString().trim().toLowerCase(Locale.US);

		if (arg.length() > 0 && !"help".equals(arg) && !"?".equals(arg)) {
			c.sendDataToWindow(getErrorMessage("Unknown .options argument: " + arg,
					"Usage: .options — it takes none."));
			return null;
		}
		if ("help".equals(arg) || "?".equals(arg)) {
			c.sendDataToWindow("\n" + Colorizer.getBrightCyanColor()
					+ "Usage: .options\n" + Colorizer.getWhiteColor()
					+ "Opens the Options screen, the same one the \u22ee menu opens.\n"
					+ "Put it on a button to reach settings without the menu.\n"
					+ "For the settings file itself, see .settings.\n");
			return null;
		}
		if (c.getService() == null) {
			c.sendDataToWindow(getErrorMessage("No window to open Options in.",
					"The game window is not running."));
			return null;
		}
		c.getService().doOpenOptions();
		return null;
	}
}
