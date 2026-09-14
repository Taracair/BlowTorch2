package com.resurrection.blowtorch2.lib.service.function;

import java.util.Locale;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.service.WindowToken;
import com.resurrection.blowtorch2.lib.window.PrefixPickLoupe;

/**
 * {@code .pick} — send a prefix plus a word from the game text.
 *
 * <pre>
 * .pick / .pick once — bar prefix, one word, then off
 * .pick hold / .pick on — bar prefix until .pick off
 * .pick tap — same as once
 * .pick button — swipe (or tap) on a pad tile, keep holding, slide onto a word
 * .pick button-double — hold a tile, tap a word with the other finger
 * .pick loupe / .pick loupe size N / .pick loupe zoom N
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

	public static boolean isLoupeCommand(final String raw) {
		if (raw == null) {
			return false;
		}
		String a = raw.trim().toLowerCase(Locale.US);
		if (a.length() == 0) {
			return false;
		}
		String first = a.split("\\s+")[0];
		return first.equals("loupe") || first.equals("size") || first.equals("zoom");
	}

	@Override
	public Object execute(Object o, Connection c) {
		String raw = o == null ? "" : o.toString().trim();
		if (isLoupeCommand(raw)) {
			return executeLoupe(raw, c);
		}
		int mode = parseMode(raw);
		if (mode < 0) {
			c.sendDataToWindow(getErrorMessage(
					"Pick — send a prefix plus a word from the screen.",
					".pick / .pick once     — bar prefix, one word, then off\n"
							+ ".pick hold / .pick on   — until .pick off\n"
							+ ".pick tap              — same as once\n"
							+ ".pick button           — swipe a tile, keep holding, slide onto a word\n"
							+ ".pick button-double    — hold a tile, tap a word with the other finger\n"
							+ ".pick loupe            — print size and zoom\n"
							+ ".pick loupe size N     — magnifier size 50–200 (118 default)\n"
							+ ".pick loupe zoom N     — magnifier zoom 150–350 (200 = 2×)\n"
							+ ".pick loupe default\n"
							+ ".pick off\n"
							+ "Bar: type .pick, then put fix  in the bar, then tap a word.\n"
							+ "Or put .pick hold on a button and leave fix  in the bar.\n"
							+ "During hold, a second finger cancels that pick so you can scroll."));
			return null;
		}
		c.getService().doExecutePrefixPick(mode);
		String msg;
		if (mode == MODE_OFF) {
			msg = Colorizer.getBrightCyanColor() + "Pick off."
					+ Colorizer.getWhiteColor() + "\n";
		} else if (mode == MODE_HOLD) {
			msg = Colorizer.getBrightCyanColor()
					+ "Pick on (hold). Prefix is the input bar. .pick off to stop. "
					+ "A second finger cancels that pick so you can scroll."
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

	private Object executeLoupe(final String raw, final Connection c) {
		String[] tok = raw.toLowerCase(Locale.US).trim().split("\\s+");
		int size = c.getMainWindowIntegerOption("pick_loupe_size",
				WindowToken.DEFAULT_PICK_LOUPE_SIZE);
		int zoom = c.getMainWindowIntegerOption("pick_loupe_zoom",
				WindowToken.DEFAULT_PICK_LOUPE_ZOOM);
		size = PrefixPickLoupe.clampSize(size);
		zoom = PrefixPickLoupe.clampZoom(zoom);
		String verb = tok[0];
		int i = 0;
		if (verb.equals("loupe")) {
			i = 1;
		}
		if (i >= tok.length || tok[i].equals("status")) {
			return loupeStatus(c, size, zoom);
		}
		if (tok[i].equals("default") || tok[i].equals("reset")) {
			if (!c.updateMainWindowIntegerOption("pick_loupe_size",
					PrefixPickLoupe.DEFAULT_SIZE)
					|| !c.updateMainWindowIntegerOption("pick_loupe_zoom",
							PrefixPickLoupe.DEFAULT_ZOOM)) {
				c.sendDataToWindow(getErrorMessage("Pick loupe error",
						"There is no game window to change yet."));
				return null;
			}
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Pick loupe size " + PrefixPickLoupe.DEFAULT_SIZE
					+ ", zoom " + PrefixPickLoupe.DEFAULT_ZOOM + ".\n");
			return null;
		}
		boolean wantSize = tok[i].equals("size");
		boolean wantZoom = tok[i].equals("zoom");
		if (!wantSize && !wantZoom) {
			c.sendDataToWindow(getErrorMessage("Pick loupe usage:",
					".pick loupe | .pick loupe size 118 | .pick loupe zoom 200 | .pick loupe default\n"
							+ "Size 50–200. Zoom 150–350 (200 = 2×). Also Options → Window."));
			return null;
		}
		if (i + 1 >= tok.length) {
			return loupeStatus(c, size, zoom);
		}
		Integer n = parsePercent(tok[i + 1]);
		if (n == null) {
			c.sendDataToWindow(getErrorMessage("Pick loupe usage:",
					".pick loupe size 118 | .pick loupe zoom 200\n"
							+ "Size 50–200. Zoom 150–350 (200 = 2×)."));
			return null;
		}
		int use = wantSize ? PrefixPickLoupe.clampSize(n.intValue())
				: PrefixPickLoupe.clampZoom(n.intValue());
		String key = wantSize ? "pick_loupe_size" : "pick_loupe_zoom";
		if (!c.updateMainWindowIntegerOption(key, use)) {
			c.sendDataToWindow(getErrorMessage("Pick loupe error",
					"There is no game window to change yet."));
			return null;
		}
		c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
				+ (wantSize ? "Pick loupe size " : "Pick loupe zoom ")
				+ use + ".\n");
		return null;
	}

	private static Object loupeStatus(final Connection c, final int size,
			final int zoom) {
		c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
				+ "Pick loupe size " + size + ", zoom " + zoom + ".\n"
				+ ".pick loupe size N | .pick loupe zoom N | .pick loupe default\n"
				+ "Also: Options → Window → Pick loupe size / zoom.\n");
		return null;
	}

	static Integer parsePercent(final String raw) {
		if (raw == null || raw.length() == 0) {
			return null;
		}
		try {
			return Integer.valueOf(Integer.parseInt(raw));
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
