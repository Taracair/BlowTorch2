package com.resurrection.blowtorch2.lib.service.function;

import java.util.Locale;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;

/**
 * {@code .pick} — send a prefix plus a word from the game text.
 *
 * <pre>
 * .pick / .pick once — bar prefix, one word, then off
 * .pick hold / .pick on — bar prefix until .pick off
 * .pick tap — same as once
 * .pick button — swipe (or tap) on a pad tile, keep holding, slide onto a word
 * .pick button-double — hold a tile, tap a word with the other finger
 * .pick off
 * </pre>
 */
public class PickCommand extends SpecialCommand {

	public static final int MODE_OFF = 0;
	public static final int MODE_ONCE = 1;
	public static final int MODE_HOLD = 2;
	public static final int MODE_TAP = 3;
	public static final int MODE_BUTTON = 4;
	public static final int MODE_BUTTON_DOUBLE = 5;

	public PickCommand() {
		this.commandName = "pick";
	}

	public static int parseMode(final String raw) {
		String arg = raw == null ? "" : raw.trim().toLowerCase(Locale.US);
		arg = arg.replace('_', '-').replace(' ', '-');
		if (arg.length() == 0 || arg.equals("once") || arg.equals("one")) {
			return MODE_ONCE;
		}
		if (arg.equals("off") || arg.equals("close") || arg.equals("stop")) {
			return MODE_OFF;
		}
		if (arg.equals("hold") || arg.equals("on") || arg.equals("persist")
				|| arg.equals("sticky")) {
			return MODE_HOLD;
		}
		if (arg.equals("tap") || arg.equals("gesture")) {
			return MODE_TAP;
		}
		if (arg.equals("button-double") || arg.equals("buttondouble")
				|| arg.equals("2finger") || arg.equals("hold-button")) {
			return MODE_BUTTON_DOUBLE;
		}
		if (arg.equals("button") || arg.equals("btn") || arg.equals("slide")) {
			return MODE_BUTTON;
		}
		return -1;
	}

	@Override
	public Object execute(Object o, Connection c) {
		int mode = parseMode(o == null ? "" : o.toString());
		if (mode < 0) {
			c.sendDataToWindow(getErrorMessage(
					"Pick — send a prefix plus a word from the screen.",
					".pick / .pick once     — bar prefix, one word, then off\n"
							+ ".pick hold / .pick on   — until .pick off\n"
							+ ".pick tap              — same as once\n"
							+ ".pick button           — swipe a tile, keep holding, slide onto a word\n"
							+ ".pick button-double    — hold a tile, tap a word with the other finger\n"
							+ ".pick off\n"
							+ "Bar: type .pick, then put fix  in the bar, then tap a word.\n"
							+ "Or put .pick hold on a button and leave fix  in the bar."));
			return null;
		}
		c.getService().doExecutePrefixPick(mode);
		String msg;
		if (mode == MODE_OFF) {
			msg = Colorizer.getBrightCyanColor() + "Pick off."
					+ Colorizer.getWhiteColor() + "\n";
		} else if (mode == MODE_HOLD) {
			msg = Colorizer.getBrightCyanColor()
					+ "Pick on (hold). Prefix is the input bar. .pick off to stop."
					+ Colorizer.getWhiteColor() + "\n";
		} else if (mode == MODE_BUTTON) {
			msg = Colorizer.getBrightCyanColor()
					+ "Pick button: swipe a tile, keep holding, slide onto a word. .pick off to stop."
					+ Colorizer.getWhiteColor() + "\n";
		} else if (mode == MODE_BUTTON_DOUBLE) {
			msg = Colorizer.getBrightCyanColor()
					+ "Pick button-double: hold a tile, tap a word with the other finger. .pick off to stop."
					+ Colorizer.getWhiteColor() + "\n";
		} else {
			msg = Colorizer.getBrightCyanColor()
					+ "Pick once: prefix in the bar, tap a word, then it turns off."
					+ Colorizer.getWhiteColor() + "\n";
		}
		c.sendDataToWindow("\n" + msg);
		return null;
	}
}
