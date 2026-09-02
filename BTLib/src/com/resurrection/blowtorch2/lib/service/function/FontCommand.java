package com.resurrection.blowtorch2.lib.service.function;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;

/**
 * Game font size from the input bar: {@code .font}, {@code .font 18},
 * {@code .font +2}, {@code .font -2}, {@code .font default}.
 *
 * <p>Same preference as Options → Window → Font size, so a button can carry it
 * and the player does not have to leave the game to make the text bigger.
 */
public class FontCommand extends SpecialCommand {

	public static final String OPTION_KEY = "font_size";

	/** Below this nothing is readable; 96 still fits a tablet without a typo becoming a wall. */
	public static final int MIN_SIZE = 6;
	public static final int MAX_SIZE = 96;
	public static final int DEFAULT_SIZE = 20;

	public FontCommand() {
		this.commandName = "font";
	}

	public Object execute(Object o, Connection c) {
		String arg = o == null ? "" : ((String) o).trim();
		int current = c.getMainWindowIntegerOption(OPTION_KEY, DEFAULT_SIZE);

		if (arg.length() == 0) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Font size is " + current + ".\n"
					+ "Usage: .font 18 | .font +2 | .font -2 | .font default\n"
					+ "Also: Options → Window → Font size.\n");
			return null;
		}

		Integer wanted = resolve(arg.toLowerCase().split("\\s+")[0], current);
		if (wanted == null) {
			c.sendDataToWindow(getErrorMessage("Font command usage:",
					".font 18 | .font +2 | .font -2 | .font default\n"
							+ "Size is a number between " + MIN_SIZE + " and " + MAX_SIZE + "."));
			return null;
		}

		int use = wanted.intValue();
		if (use == current) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Font size already " + use + ".\n");
			return null;
		}
		if (!c.updateMainWindowIntegerOption(OPTION_KEY, use)) {
			c.sendDataToWindow(getErrorMessage("Font command error",
					"There is no game window to change yet."));
			return null;
		}
		c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
				+ "Font size " + use + ".\n");
		return null;
	}

	/**
	 * Turns one argument into the size to use, clamped, or null when it is not a
	 * size at all. {@code +2} and {@code -2} step from {@code current}.
	 */
	static Integer resolve(final String arg, final int current) {
		if (arg == null || arg.length() == 0) {
			return null;
		}
		if ("default".equals(arg)) {
			return Integer.valueOf(DEFAULT_SIZE);
		}
		boolean relative = arg.charAt(0) == '+' || arg.charAt(0) == '-';
		try {
			int n = Integer.parseInt(relative ? arg.substring(1) : arg);
			if (relative && arg.charAt(0) == '-') {
				n = -n;
			}
			int target = relative ? current + n : n;
			return Integer.valueOf(clamp(target));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	public static int clamp(final int size) {
		return Math.max(MIN_SIZE, Math.min(MAX_SIZE, size));
	}
}
