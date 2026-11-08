package com.resurrection.blowtorch2.lib.window;

/**
 * Polish programmer letters on right Alt. A key event that already carries
 * the letter is left alone. Ctrl+Alt is not this map.
 */
public final class AltGraph {

	/** Platform {@code KeyEvent.META_SHIFT_ON}. */
	private static final int SHIFT = 0x1;
	/** Platform {@code KeyEvent.META_ALT_RIGHT_ON}. */
	private static final int ALT_RIGHT = 0x20;
	/** Platform {@code KeyEvent.META_CTRL_ON}. */
	private static final int CTRL = 0x1000;
	/** Platform {@code KeyEvent.META_CAPS_LOCK_ON}. */
	private static final int CAPS = 0x100000;

	private static final int KEY_A = 29;
	private static final int KEY_C = 31;
	private static final int KEY_E = 33;
	private static final int KEY_L = 40;
	private static final int KEY_N = 42;
	private static final int KEY_O = 43;
	private static final int KEY_S = 47;
	private static final int KEY_X = 52;
	private static final int KEY_Z = 54;

	private AltGraph() {
	}

	/**
	 * The letter to put in the bar, or 0 when the platform already has one
	 * or this chord is not right Alt on a Polish letter.
	 */
	public static int character(final int meta, final int keyCode, final int plainUnicode,
			final int metaUnicode) {
		if ((meta & ALT_RIGHT) == 0 || (meta & CTRL) != 0) {
			return 0;
		}
		int mapped = map(keyCode, upper(meta));
		if (mapped == 0) {
			return 0;
		}
		if (metaUnicode == mapped) {
			return 0;
		}
		if (metaUnicode > 0 && metaUnicode != plainUnicode) {
			return 0;
		}
		return mapped;
	}

	private static boolean upper(final int meta) {
		return ((meta & SHIFT) != 0) ^ ((meta & CAPS) != 0);
	}

	private static int map(final int keyCode, final boolean upper) {
		switch (keyCode) {
		case KEY_A:
			return upper ? 0x0104 : 0x0105;
		case KEY_C:
			return upper ? 0x0106 : 0x0107;
		case KEY_E:
			return upper ? 0x0118 : 0x0119;
		case KEY_L:
			return upper ? 0x0141 : 0x0142;
		case KEY_N:
			return upper ? 0x0143 : 0x0144;
		case KEY_O:
			return upper ? 0x00D3 : 0x00F3;
		case KEY_S:
			return upper ? 0x015A : 0x015B;
		case KEY_X:
			return upper ? 0x0179 : 0x017A;
		case KEY_Z:
			return upper ? 0x017B : 0x017C;
		default:
			return 0;
		}
	}
}
