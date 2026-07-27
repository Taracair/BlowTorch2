package com.resurrection.blowtorch2.lib.service.function;

import java.util.Locale;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;

/**
 * Settings file housekeeping from the input bar.
 *
 * <pre>
 * .settings          what is on disk, and when it was last kept
 * .settings backup   save now, which also refreshes the kept copy
 * .settings restore  put the kept copy back and reload
 * </pre>
 *
 * <p>A backup nobody can reach is not a backup. One copy of the previous settings has
 * been kept on every save for a while, but recovering it meant a USB cable and adb,
 * which is no use to somebody who has just watched their aliases disappear on a bus.
 */
public class SettingsCommand extends SpecialCommand {

	public SettingsCommand() {
		this.commandName = "settings";
	}

	@Override
	public Object execute(final Object o, final Connection c) {
		String arg = o == null ? "" : o.toString().trim().toLowerCase(Locale.US);

		if (arg.length() == 0 || "status".equals(arg) || "help".equals(arg)) {
			c.sendDataToWindow("\n" + Colorizer.getBrightCyanColor()
					+ c.describeSettingsBackup() + "\n"
					+ ".settings backup   save now and refresh the kept copy\n"
					+ ".settings restore  put the kept copy back and reload\n"
					+ Colorizer.getWhiteColor() + "\n");
			return null;
		}

		if ("backup".equals(arg)) {
			c.saveMainSettings();
			c.sendDataToWindow("\n" + Colorizer.getBrightCyanColor()
					+ "Settings saved. " + c.describeSettingsBackup()
					+ Colorizer.getWhiteColor() + "\n");
			return null;
		}

		if ("restore".equals(arg)) {
			String result = c.restoreSettingsBackup();
			c.sendDataToWindow("\n" + Colorizer.getBrightCyanColor() + result
					+ Colorizer.getWhiteColor() + "\n");
			return null;
		}

		c.sendDataToWindow(getErrorMessage("Unknown .settings argument: " + arg,
				"Try .settings, .settings backup, or .settings restore."));
		return null;
	}
}
