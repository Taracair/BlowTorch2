package com.resurrection.blowtorch2.lib.service.function;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;

/**
 * Text canvas width from the input bar: {@code .width}, {@code .width 150},
 * {@code .width +25}, {@code .width toggle}, {@code .width off}.
 *
 * <p>Same preference as Options → Window → Text width (% of screen): over 100
 * the text is drawn wider than the screen and dragged sideways with one finger.
 *
 * <p>{@code toggle} is the point of the command. Wide is right for an ASCII map
 * and wrong for reading, and switching it in the options menu mid-fight is not
 * something anyone does — so toggle drops straight back to 100 and remembers
 * the width to come back to.
 */
public class CanvasWidthCommand extends SpecialCommand {

	public static final String OPTION_KEY = "text_canvas_width";

	public static final int MIN_PERCENT = 100;
	public static final int MAX_PERCENT = 200;
	/** Where toggle goes when there is nothing remembered yet. */
	public static final int DEFAULT_WIDE = 150;

	/**
	 * The width toggle returns to. Lives in the service process next to the
	 * connection, so it survives the window being rebuilt (rotation) but not a
	 * restart — a remembered width is a convenience, not a setting.
	 */
	private int remembered = DEFAULT_WIDE;

	public CanvasWidthCommand() {
		this.commandName = "width";
	}

	public Object execute(Object o, Connection c) {
		String arg = o == null ? "" : ((String) o).trim();
		int current = c.getMainWindowIntegerOption(OPTION_KEY, MIN_PERCENT);

		if (arg.length() == 0) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Text width is " + current + "% of the screen"
					+ (current > MIN_PERCENT ? " (drag sideways with one finger)." : ".") + "\n"
					+ "Usage: .width 150 | .width +25 | .width toggle | .width off\n"
					+ "Toggle flips between 100% and " + remembered + "%.\n"
					+ "Also: Options → Window → Text width (% of screen).\n");
			return null;
		}

		String first = arg.toLowerCase().split("\\s+")[0];
		Integer wanted;
		if ("toggle".equals(first)) {
			wanted = Integer.valueOf(current > MIN_PERCENT ? MIN_PERCENT : remembered);
		} else if ("off".equals(first)) {
			wanted = Integer.valueOf(MIN_PERCENT);
		} else {
			wanted = resolve(first, current);
		}

		if (wanted == null) {
			c.sendDataToWindow(getErrorMessage("Width command usage:",
					".width 150 | .width +25 | .width toggle | .width off\n"
							+ "Width is a percent between " + MIN_PERCENT + " and "
							+ MAX_PERCENT + "; 100 = fits the screen, as before."));
			return null;
		}

		int use = wanted.intValue();
		// Remember the wide setting before leaving it, so toggle can come back.
		if (current > MIN_PERCENT) {
			remembered = current;
		}

		if (use == current) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Text width already " + use + "%.\n");
			return null;
		}
		if (!c.updateMainWindowIntegerOption(OPTION_KEY, use)) {
			c.sendDataToWindow(getErrorMessage("Width command error",
					"There is no game window to change yet."));
			return null;
		}
		c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
				+ "Text width " + use + "%"
				+ (use > MIN_PERCENT ? " — drag sideways with one finger." : ".") + "\n");
		return null;
	}

	/** A percent, or {@code +25}/{@code -25} from the current one; null if neither. */
	static Integer resolve(final String arg, final int current) {
		if (arg == null || arg.length() == 0) {
			return null;
		}
		boolean relative = arg.charAt(0) == '+' || arg.charAt(0) == '-';
		try {
			int n = Integer.parseInt(relative ? arg.substring(1) : arg);
			if (relative && arg.charAt(0) == '-') {
				n = -n;
			}
			return Integer.valueOf(clamp(relative ? current + n : n));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	static int clamp(final int percent) {
		return Math.max(MIN_PERCENT, Math.min(MAX_PERCENT, percent));
	}

	/** What {@code .width toggle} will go back to. */
	public int getRemembered() {
		return remembered;
	}
}
