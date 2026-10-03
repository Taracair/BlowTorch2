package com.resurrection.blowtorch2.lib.window;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Per-world screen gestures on the main game text.
 *
 * One active mode. Classic leaves the window's existing one-finger scroll
 * and immediate two-finger copy alone. The other modes are optional.
 * Published from the UI process when settings load; the service keeps the
 * option values and does not read this static.
 */
public final class GlobalGestures {

	public static final int MODE_CLASSIC = 0;
	public static final int MODE_ONE = 1;
	public static final int MODE_TWO = 2;
	public static final int MODE_BOTH = 3;

	/** One-finger swipe is the gesture. Scrolling is a two-finger drag. */
	public static final int SCROLL_TWO = 0;
	/** A short move scrolls. A gesture starts after the finger has held still. */
	public static final int SCROLL_HOLD = 1;
	/** A one-finger swipe does not scroll. */
	public static final int SCROLL_OFF = 2;

	public static final int DEFAULT_HOLD_MS = 280;
	public static final int MIN_HOLD_MS = 80;
	public static final int MAX_HOLD_MS = 800;

	public static final String KEY_MODE = "global_gesture_mode";
	public static final String KEY_SCROLL = "global_gesture_scroll";
	public static final String KEY_HOLD_MS = "global_gesture_hold_ms";
	public static final String KEY_SHOW_MODE = "global_gesture_show_mode";
	public static final String KEY_SHOW_ARROW = "global_gesture_show_arrow";
	public static final String KEY_SHOW_COMMAND = "global_gesture_show_command";
	public static final String KEY_TWO_DIR = "global_gesture_two_dir";
	public static final String KEY_TWO_COPY = "global_gesture_two_copy";
	public static final String KEY_TWO_SCROLL = "global_gesture_two_scroll";
	public static final String KEY_BINDINGS = "global_gesture_bindings";

	/** Clockwise from north, matching the editor rows. */
	public static final String[] DIRS = {
		"n", "ne", "e", "se", "s", "sw", "w", "nw",
	};

	private static final String[] SECTORS = {
		"e", "se", "s", "sw", "w", "nw", "n", "ne",
	};

	private final int mode;
	private final int scroll;
	private final int holdMs;
	private final boolean showMode;
	private final boolean showArrow;
	private final boolean showCommand;
	private final boolean twoDir;
	private final boolean twoCopy;
	private final boolean twoScroll;
	private final Map<String, String> bindings;

	private static volatile GlobalGestures sCurrent = defaults();

	public GlobalGestures(final int mode, final int scroll, final int holdMs,
			final boolean showMode, final boolean showArrow, final boolean showCommand,
			final boolean twoDir, final boolean twoCopy, final boolean twoScroll,
			final String storedBindings) {
		this.mode = clampMode(mode);
		this.scroll = clampScroll(scroll);
		this.holdMs = clampHold(holdMs);
		this.showMode = showMode;
		this.showArrow = showArrow;
		this.showCommand = showCommand;
		this.twoDir = twoDir;
		this.twoCopy = twoCopy;
		this.twoScroll = twoScroll;
		this.bindings = Collections.unmodifiableMap(parseBindings(storedBindings));
	}

	public static GlobalGestures defaults() {
		return new GlobalGestures(MODE_CLASSIC, SCROLL_HOLD, DEFAULT_HOLD_MS,
				false, true, true, true, true, false, "");
	}

	public static void publish(final GlobalGestures next) {
		sCurrent = next != null ? next : defaults();
	}

	public static GlobalGestures current() {
		GlobalGestures g = sCurrent;
		return g != null ? g : defaults();
	}

	public int mode() {
		return mode;
	}

	public int scroll() {
		return scroll;
	}

	public int holdMs() {
		return holdMs;
	}

	public boolean showMode() {
		return showMode;
	}

	public boolean showArrow() {
		return showArrow;
	}

	public boolean showCommand() {
		return showCommand;
	}

	public boolean twoDir() {
		return twoDir;
	}

	public boolean twoCopy() {
		return twoCopy;
	}

	public boolean twoScroll() {
		return twoScroll;
	}

	public boolean oneFingerGestures() {
		return mode == MODE_ONE || mode == MODE_BOTH;
	}

	public boolean twoFingerMode() {
		return mode == MODE_TWO || mode == MODE_BOTH;
	}

	/** Command for this finger count and direction, or null when the slot is empty. */
	public String binding(final int fingers, final String dir) {
		if (dir == null) {
			return null;
		}
		String v = bindings.get(fingers + "." + dir);
		if (v == null || v.length() == 0) {
			return null;
		}
		return v;
	}

	public String formatBindings() {
		StringBuilder sb = new StringBuilder();
		for (int fingers = 1; fingers <= 2; fingers++) {
			for (int i = 0; i < DIRS.length; i++) {
				String cmd = binding(fingers, DIRS[i]);
				if (cmd == null) {
					continue;
				}
				sb.append(fingers).append('.').append(DIRS[i]).append('=').append(cmd).append('\n');
			}
		}
		return sb.toString();
	}

	public GlobalGestures withMode(final int next) {
		return copy(next, scroll, holdMs, showMode, showArrow, showCommand,
				twoDir, twoCopy, twoScroll, formatBindings());
	}

	public GlobalGestures withScroll(final int next) {
		return copy(mode, next, holdMs, showMode, showArrow, showCommand,
				twoDir, twoCopy, twoScroll, formatBindings());
	}

	public GlobalGestures withBindings(final String stored) {
		return copy(mode, scroll, holdMs, showMode, showArrow, showCommand,
				twoDir, twoCopy, twoScroll, stored);
	}

	public GlobalGestures withShow(final boolean show) {
		return copy(mode, scroll, holdMs, show, showArrow, showCommand,
				twoDir, twoCopy, twoScroll, formatBindings());
	}

	public GlobalGestures withPreview(final boolean on) {
		return copy(mode, scroll, holdMs, showMode, on, on,
				twoDir, twoCopy, twoScroll, formatBindings());
	}

	/** Nearest of the eight directions, or null when the finger has not travelled enough.
	 * Screen coordinates: +x right, +y down. Sectors are equal 45° slices. */
	/** Index in the eight screen sectors, or -1. 0 is east, then clockwise. */
	public static int sectorIndex(final String dir) {
		if (dir == null) {
			return -1;
		}
		for (int i = 0; i < SECTORS.length; i++) {
			if (SECTORS[i].equals(dir)) {
				return i;
			}
		}
		return -1;
	}

	/** Options row that does nothing for this mode and one-finger scroll policy. */
	public static boolean optionUnused(final String key, final int mode, final int scroll) {
		if (key == null || KEY_MODE.equals(key) || KEY_SHOW_MODE.equals(key)
				|| KEY_BINDINGS.equals(key)) {
			return false;
		}
		int m = clampMode(mode);
		int s = clampScroll(scroll);
		boolean one = m == MODE_ONE || m == MODE_BOTH;
		boolean two = m == MODE_TWO || m == MODE_BOTH;
		boolean twoFromOne = m == MODE_ONE && s == SCROLL_TWO;
		if (KEY_SCROLL.equals(key)) {
			return !one;
		}
		if (KEY_HOLD_MS.equals(key)) {
			return !(one && s == SCROLL_HOLD);
		}
		if (KEY_SHOW_ARROW.equals(key) || KEY_SHOW_COMMAND.equals(key)) {
			return m == MODE_CLASSIC;
		}
		if (KEY_TWO_DIR.equals(key)) {
			if (m == MODE_BOTH && s == SCROLL_TWO) {
				return true;
			}
			return !two;
		}
		if (KEY_TWO_COPY.equals(key)) {
			return !(two || twoFromOne);
		}
		// With two fingers already scrolls in One finger and in Both.
		// The switch is the extra scroll while Both is Hold or Off.
		if (KEY_TWO_SCROLL.equals(key)) {
			return m != MODE_BOTH || s == SCROLL_TWO;
		}
		return false;
	}

	public static String direction(final float dx, final float dy, final float minTravel) {
		if (Math.hypot(dx, dy) < minTravel) {
			return null;
		}
		double deg = Math.toDegrees(Math.atan2(dy, dx));
		if (deg < 0) {
			deg += 360.0;
		}
		int sector = (int) Math.floor((deg + 22.5) / 45.0) % 8;
		if (sector < 0) {
			sector = 0;
		}
		return SECTORS[sector];
	}

	public static int clampMode(final int mode) {
		if (mode < MODE_CLASSIC || mode > MODE_BOTH) {
			return MODE_CLASSIC;
		}
		return mode;
	}

	public static int clampScroll(final int scroll) {
		if (scroll < SCROLL_TWO || scroll > SCROLL_OFF) {
			return SCROLL_HOLD;
		}
		return scroll;
	}

	public static int clampHold(final int ms) {
		if (ms < MIN_HOLD_MS) {
			return MIN_HOLD_MS;
		}
		if (ms > MAX_HOLD_MS) {
			return MAX_HOLD_MS;
		}
		return ms;
	}

	public static String modeLabel(final int mode) {
		switch (clampMode(mode)) {
		case MODE_ONE:
			return "1 finger";
		case MODE_TWO:
			return "2 fingers";
		case MODE_BOTH:
			return "1+2";
		default:
			return "Classic";
		}
	}

	/** Same words as the Scrolling row and the Gesture mode dialog. Index is the stored value. */
	public static final String[] SCROLL_CHOICES = {
		"With two fingers",
		"Hold, then gesture",
		"Off",
	};

	public static String scrollLabel(final int scroll) {
		return SCROLL_CHOICES[clampScroll(scroll)];
	}

	/** Pill text: the mode, plus the scrolling choice when that row applies. */
	public String chromeLabel() {
		String modeText = modeLabel(mode);
		if (optionUnused(KEY_SCROLL, mode, scroll)) {
			return modeText;
		}
		return modeText + " · " + scrollLabel(scroll);
	}

	/** {@code .gesture scroll two|hold|off}, or null. */
	public static Integer scrollIndex(final String token) {
		if (token == null) {
			return null;
		}
		String t = token.toLowerCase();
		if ("two".equals(t) || "with".equals(t)) {
			return Integer.valueOf(SCROLL_TWO);
		}
		if ("hold".equals(t)) {
			return Integer.valueOf(SCROLL_HOLD);
		}
		if ("off".equals(t)) {
			return Integer.valueOf(SCROLL_OFF);
		}
		return null;
	}

	private GlobalGestures copy(final int mode, final int scroll, final int holdMs,
			final boolean showMode, final boolean showArrow, final boolean showCommand,
			final boolean twoDir, final boolean twoCopy, final boolean twoScroll,
			final String stored) {
		return new GlobalGestures(mode, scroll, holdMs, showMode, showArrow, showCommand,
				twoDir, twoCopy, twoScroll, stored);
	}

	static Map<String, String> parseBindings(final String stored) {
		Map<String, String> out = new HashMap<String, String>();
		if (stored == null || stored.length() == 0) {
			return out;
		}
		String[] lines = stored.split("\n");
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i].trim();
			if (line.length() == 0) {
				continue;
			}
			int eq = line.indexOf('=');
			if (eq <= 0 || eq == line.length() - 1) {
				continue;
			}
			String key = line.substring(0, eq).trim();
			String command = line.substring(eq + 1).trim().replace("\n", " ");
			if (command.length() == 0 || !validKey(key)) {
				continue;
			}
			out.put(key, command);
		}
		return out;
	}

	private static boolean validKey(final String key) {
		int dot = key.indexOf('.');
		if (dot != 1) {
			return false;
		}
		char fingers = key.charAt(0);
		if (fingers != '1' && fingers != '2') {
			return false;
		}
		String dir = key.substring(2);
		for (int i = 0; i < DIRS.length; i++) {
			if (DIRS[i].equals(dir)) {
				return true;
			}
		}
		return false;
	}
}
