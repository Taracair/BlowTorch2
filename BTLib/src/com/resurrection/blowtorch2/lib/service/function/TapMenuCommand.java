package com.resurrection.blowtorch2.lib.service.function;

import java.util.Locale;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BaseOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.IntegerOption;
import com.resurrection.blowtorch2.lib.window.MainWindow;

/**
 * {@code .tapmenu} — the little menu a tapped word opens.
 *
 * <p>It opens on top of the text it is about, which is the whole reason it can
 * want to be see-through. Only the backing fades; the commands stay fully
 * readable at every setting, the same rule the suggestion chips follow.
 */
public class TapMenuCommand extends SpecialCommand {

	public static final String OPACITY_KEY = "tap_menu_opacity";

	public TapMenuCommand() {
		this.commandName = "tapmenu";
	}

	@Override
	public Object execute(Object o, Connection c) {
		String arg = o == null ? "" : ((String) o).trim().toLowerCase(Locale.US);
		if (arg.startsWith("opacity")) {
			return setOpacity(arg.substring("opacity".length()).trim(), c);
		}
		if (arg.length() == 0 || arg.equals("status")) {
			c.sendDataToWindow("\nThe menu a tapped word opens is "
					+ opacity(c) + "% solid."
					+ "\nUse .tapmenu opacity N, from "
					+ MainWindow.MIN_TAP_MENU_OPACITY + " to 100.\n");
			return null;
		}
		c.sendDataToWindow(getErrorMessage("Tap menu usage:",
				".tapmenu opacity N — how solid the menu is, "
				+ MainWindow.MIN_TAP_MENU_OPACITY + " to 100.\n"
				+ ".tapmenu           — what it is set to now\n"));
		return null;
	}

	private Object setOpacity(String arg, Connection c) {
		int n;
		try {
			n = Integer.parseInt(arg);
		} catch (NumberFormatException e) {
			c.sendDataToWindow(getErrorMessage("Tap menu usage:",
					".tapmenu opacity N — a number from "
					+ MainWindow.MIN_TAP_MENU_OPACITY + " to 100.\n"));
			return null;
		}
		// Clamped rather than refused: the range is a readability floor, not a
		// thing the player has to get right to be understood.
		int clamped = MainWindow.clampOpacity(n);
		c.updateIntegerSetting(OPACITY_KEY, clamped);
		c.sendDataToWindow("\n" + Colorizer.getBrightCyanColor()
				+ "The tapped-word menu is now " + clamped + "% solid."
				+ (clamped != n ? " (" + n + " is outside "
					+ MainWindow.MIN_TAP_MENU_OPACITY + "-100.)" : "")
				+ Colorizer.getWhiteColor() + "\n");
		return null;
	}

	private static int opacity(Connection c) {
		if (c == null || c.getSettings() == null) {
			return MainWindow.DEFAULT_TAP_MENU_OPACITY;
		}
		Object o = c.getSettings().findOptionByKey(OPACITY_KEY);
		if (o instanceof IntegerOption
				&& ((BaseOption) o).getValue() instanceof Integer) {
			return ((Integer) ((BaseOption) o).getValue()).intValue();
		}
		return MainWindow.DEFAULT_TAP_MENU_OPACITY;
	}
}
