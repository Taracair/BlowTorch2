package com.resurrection.blowtorch2.lib.service.function;

import java.util.Locale;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;

/**
 * {@code .lupa} — inspect the SGR recipe under a finger and copy layers into
 * a trigger or the clipboard.
 *
 * <pre>
 * .lupa          — once: stays until the first copy, then off
 * .lupa once
 * .lupa hold     — stays until .lupa off (.lupa on is the same)
 * .lupa tap      — one gesture: list becomes tappable on finger-up, then off
 * .lupa off
 * </pre>
 */
public class LupaCommand extends SpecialCommand {

	public static final int MODE_OFF = 0;
	public static final int MODE_ONCE = 1;
	public static final int MODE_HOLD = 2;
	public static final int MODE_TAP = 3;

	public LupaCommand() {
		this.commandName = "lupa";
	}

	public static int parseMode(final String raw) {
		String arg = raw == null ? "" : raw.trim().toLowerCase(Locale.US);
		if (arg.length() == 0 || arg.equals("once")) {
			return MODE_ONCE;
		}
		if (arg.equals("off") || arg.equals("close") || arg.equals("stop")) {
			return MODE_OFF;
		}
		if (arg.equals("hold") || arg.equals("on") || arg.equals("persist")) {
			return MODE_HOLD;
		}
		if (arg.equals("tap") || arg.equals("gesture") || arg.equals("one")) {
			return MODE_TAP;
		}
		return -1;
	}

	@Override
	public Object execute(Object o, Connection c) {
		int mode = parseMode(o == null ? "" : o.toString());
		if (mode < 0) {
			c.sendDataToWindow(getErrorMessage(
					"Lupa — inspect colour and style under your finger.",
					".lupa / .lupa once   — until the first copy\n"
							+ ".lupa hold / .lupa on — until .lupa off\n"
							+ ".lupa tap            — one gesture, then off\n"
							+ ".lupa off\n"
							+ "Drag: live list. Release: tap layers. Copy clipboard, or New trigger."));
			return null;
		}
		c.getService().doExecuteLupa(mode);
		String msg;
		if (mode == MODE_OFF) {
			msg = Colorizer.getBrightCyanColor() + "Lupa off."
					+ Colorizer.getWhiteColor() + "\n";
		} else if (mode == MODE_HOLD) {
			msg = Colorizer.getBrightCyanColor()
					+ "Lupa on (hold). Drag a glyph, release, tap layers, copy. .lupa off to stop."
					+ Colorizer.getWhiteColor() + "\n";
		} else if (mode == MODE_TAP) {
			msg = Colorizer.getBrightCyanColor()
					+ "Lupa tap: one drag, then the list is tappable. Copy or tap outside ends it."
					+ Colorizer.getWhiteColor() + "\n";
		} else {
			msg = Colorizer.getBrightCyanColor()
					+ "Lupa once: drag, copy, then it turns off."
					+ Colorizer.getWhiteColor() + "\n";
		}
		c.sendDataToWindow("\n" + msg);
		return null;
	}
}
