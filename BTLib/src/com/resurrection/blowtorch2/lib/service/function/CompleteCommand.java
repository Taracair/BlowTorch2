package com.resurrection.blowtorch2.lib.service.function;

import java.util.Locale;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BaseOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BooleanOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.IntegerOption;

/**
 * {@code .complete on|off|lines N} — suggest words the game has just used while
 * you type.
 *
 * <p>Off by default, and while off the incoming text is not sent to the UI for
 * this at all, so it costs nothing.
 *
 * <p>Both settings live in the profile (Options → Input), so the command writes
 * the option rather than a runtime flag — otherwise the menu and the command
 * would disagree, and neither would survive a restart.
 */
public class CompleteCommand extends SpecialCommand {

	public static final String OPTION_KEY = "word_complete";
	public static final String LINES_KEY = "word_complete_lines";

	/** Above this a window is no longer a window; below zero is meaningless. */
	public static final int MAX_LINES = 5000;

	public CompleteCommand() {
		this.commandName = "complete";
	}

	@Override
	public Object execute(Object o, Connection c) {
		String arg = o == null ? "" : ((String) o).trim().toLowerCase(Locale.US);
		if (arg.equals("on") || arg.equals("off")) {
			boolean on = arg.equals("on");
			c.updateBooleanSetting(OPTION_KEY, on);
			c.sendDataToWindow("\n" + Colorizer.getBrightCyanColor()
					+ (on
						? "Word completion on. Type two letters of something the game"
							+ " said and it appears above the input bar; tap to use it."
						: "Word completion off.")
					+ Colorizer.getWhiteColor() + "\n");
			return null;
		}
		if (arg.startsWith("lines")) {
			return setLines(arg.substring("lines".length()).trim(), c);
		}
		if (arg.length() == 0 || arg.equals("status")) {
			c.sendDataToWindow("\nWord completion is "
					+ (isOn(c) ? "on" : "off")
					+ ", remembering the last " + describeLines(lines(c))
					+ ".\nUse .complete on|off, .complete lines N\n");
			return null;
		}
		c.sendDataToWindow(getErrorMessage("Word completion usage:",
				".complete on      — suggest words the game just used\n"
				+ ".complete off     — stop\n"
				+ ".complete lines N — how far back counts as recent (0 = all session)\n"
				+ ".complete         — say which it is\n\n"
				+ "This completes mob names, player names and item words the\n"
				+ "keyboard will never know, and would rather correct into\n"
				+ "English. Type \"k gri\" after a grizzled cave troll walks in.\n\n"
				+ "Also under Options → Input.\n"));
		return null;
	}

	private Object setLines(String arg, Connection c) {
		if (arg.length() == 0) {
			c.sendDataToWindow("\nCompletion remembers the last "
					+ describeLines(lines(c)) + ".\nUse .complete lines N (0-"
					+ MAX_LINES + ", 0 = the whole session)\n");
			return null;
		}
		int n;
		try {
			n = Integer.parseInt(arg.split("\\s+")[0]);
		} catch (NumberFormatException e) {
			c.sendDataToWindow(getErrorMessage("Word completion usage:",
					".complete lines N — a number from 0 to " + MAX_LINES + ".\n"
					+ "0 means keep everything this session said.\n"));
			return null;
		}
		if (n < 0 || n > MAX_LINES) {
			c.sendDataToWindow(getErrorMessage("Word completion usage:",
					"Lines must be between 0 and " + MAX_LINES + ".\n"));
			return null;
		}
		c.updateIntegerSetting(LINES_KEY, n);
		c.sendDataToWindow("\n" + Colorizer.getBrightCyanColor()
				+ "Completion now remembers the last " + describeLines(n) + "."
				+ Colorizer.getWhiteColor() + "\n");
		return null;
	}

	private static String describeLines(int n) {
		return n <= 0 ? "whole session" : (n + " lines");
	}

	private static boolean isOn(Connection c) {
		BaseOption o = findOption(c, OPTION_KEY);
		if (o instanceof BooleanOption && o.getValue() instanceof Boolean) {
			return ((Boolean) o.getValue()).booleanValue();
		}
		return false;
	}

	private static int lines(Connection c) {
		BaseOption o = findOption(c, LINES_KEY);
		if (o instanceof IntegerOption && o.getValue() instanceof Integer) {
			return ((Integer) o.getValue()).intValue();
		}
		return 0;
	}

	private static BaseOption findOption(Connection c, String key) {
		if (c == null || c.getSettings() == null) {
			return null;
		}
		Object o = c.getSettings().findOptionByKey(key);
		return o instanceof BaseOption ? (BaseOption) o : null;
	}
}
