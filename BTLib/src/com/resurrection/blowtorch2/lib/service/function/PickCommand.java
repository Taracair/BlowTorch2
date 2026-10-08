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
 * add {@code insert} ({@code .pick hold insert}) and the line goes in the bar
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
	/** Packed into the mode int. The binder method stays one int. */
	public static final int INSERT_FLAG = 0x100;

	public static final class Args {
		public final int mode;
		public final boolean insert;

		Args(final int mode, final boolean insert) {
			this.mode = mode;
			this.insert = insert;
		}

		public int packed() {
			if (mode < 0) {
				return -1;
			}
			return insert ? (mode | INSERT_FLAG) : mode;
		}
	}

	public static int modeOf(final int packed) {
		return packed & 0xff;
	}

	public static boolean inserts(final int packed) {
		return packed >= 0 && (packed & INSERT_FLAG) != 0;
	}

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

	/**
	 * One mode word, plus optional {@code insert} in either order.
	 * {@code .pick off insert} and two mode words are invalid ({@code mode}
	 * is {@code -1}).
	 */
	public static Args parseArgs(final String raw) {
		String trimmed = raw == null ? "" : raw.trim();
		if (trimmed.length() == 0) {
			return new Args(MODE_ONCE, false);
		}
		String[] tok = trimmed.toLowerCase(Locale.US).split("\\s+");
		java.util.ArrayList<String> words = new java.util.ArrayList<String>();
		for (int i = 0; i < tok.length; i++) {
			String t = tok[i].replace('_', '-');
			if ((t.equals("button") || t.equals("btn")) && i + 1 < tok.length
					&& tok[i + 1].replace('_', '-').equals("double")) {
				words.add("button-double");
				i++;
				continue;
			}
			words.add(t);
		}
		int mode = Integer.MIN_VALUE;
		boolean insert = false;
		for (int i = 0; i < words.size(); i++) {
			String w = words.get(i);
			if (w.equals("insert")) {
				insert = true;
				continue;
			}
			int m = parseMode(w);
			if (m < 0 || mode != Integer.MIN_VALUE) {
				return new Args(-1, false);
			}
			mode = m;
		}
		if (mode == Integer.MIN_VALUE) {
			mode = MODE_ONCE;
		}
		if (mode == MODE_OFF && insert) {
			return new Args(-1, false);
		}
		return new Args(mode, insert);
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
		Args args = parseArgs(raw);
		int mode = args.mode;
		if (mode < 0) {
			c.sendDataToWindow(getErrorMessage(
					"Pick — send a prefix plus a word from the screen.",
					".pick / .pick once     — bar prefix, one word, then off\n"
							+ ".pick hold / .pick on   — until .pick off\n"
							+ ".pick tap              — same as once\n"
							+ ".pick button           — swipe a tile, keep holding, slide onto a word\n"
							+ ".pick button-double    — hold a tile, tap a word with the other finger\n"
							+ "Add insert to any of those and the line goes in the bar instead.\n"
							+ ".pick insert           — once, into the bar\n"
							+ ".pick hold insert      — each next word is added in the bar\n"
							+ ".pick button insert\n"
							+ ".pick button-double insert\n"
							+ ".pick loupe            — print size and zoom\n"
							+ ".pick loupe size N     — magnifier size 50–200 (118 default)\n"
							+ ".pick loupe zoom N     — magnifier zoom 150–350 (200 = 2×)\n"
							+ ".pick loupe default\n"
							+ ".pick off\n"
							+ "Bar: type .pick, then put fix  in the bar, then tap a word.\n"
							+ "Or put fix $1 helmet in the bar and pick iron → fix iron helmet.\n"
							+ "$1, $0 and $word are the picked word. No slot: the word is appended.\n"
							+ "Or put .pick hold on a button and leave fix  in the bar.\n"
							+ "During hold, a second finger cancels that pick so you can scroll.\n"
							+ "insert sends nothing. You send the line yourself."));
			return null;
		}
		c.getService().doExecutePrefixPick(args.packed());
		String intoBar = args.insert
				? " The line goes in the bar. Nothing is sent."
				: "";
		String msg;
		if (mode == MODE_OFF) {
			msg = Colorizer.getBrightCyanColor() + "Pick off."
					+ Colorizer.getWhiteColor() + "\n";
		} else if (mode == MODE_HOLD) {
			msg = Colorizer.getBrightCyanColor()
					+ "Pick on (hold). Prefix is the input bar "
					+ "(fix  or fix $1 helmet). .pick off to stop. "
					+ "A second finger cancels that pick so you can scroll."
					+ intoBar
					+ Colorizer.getWhiteColor() + "\n";
		} else if (mode == MODE_BUTTON) {
			msg = Colorizer.getBrightCyanColor()
					+ "Pick button: swipe a tile, keep holding, slide onto a word. .pick off to stop."
					+ intoBar
					+ Colorizer.getWhiteColor() + "\n";
		} else if (mode == MODE_BUTTON_DOUBLE) {
			msg = Colorizer.getBrightCyanColor()
					+ "Pick button-double: hold a tile, tap a word with the other finger. .pick off to stop."
					+ intoBar
					+ Colorizer.getWhiteColor() + "\n";
		} else {
			msg = Colorizer.getBrightCyanColor()
					+ "Pick once: prefix in the bar (fix  or fix $1 helmet), "
					+ "tap a word, then it turns off."
					+ intoBar
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
