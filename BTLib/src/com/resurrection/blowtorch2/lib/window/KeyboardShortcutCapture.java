package com.resurrection.blowtorch2.lib.window;

/**
 * The form's "press a key" row. A virtual key is left for the text fields.
 * A modifier by itself is the chord when nothing else arrived.
 */
public final class KeyboardShortcutCapture {

	public static final int NONE = -1;
	private static final int DOWN = 0;
	private static final int UP = 1;
	private static final int VIRTUAL = -1;

	private boolean armed;
	private int keyCode = NONE;
	private int modifiers;

	public void set(final int keyCode, final int modifiers) {
		this.keyCode = keyCode;
		this.modifiers = KeyboardShortcuts.modifiers(modifiers);
		this.armed = false;
	}

	public void arm() {
		armed = true;
	}

	public boolean armed() {
		return armed;
	}

	public int keyCode() {
		return keyCode;
	}

	public int modifiers() {
		return modifiers;
	}

	public String label() {
		if (armed) {
			return "Press a key";
		}
		return KeyboardShortcuts.chordLabel(keyCode, modifiers);
	}

	/** @return true when the form should keep the event */
	public boolean onKey(final int action, final int code, final int meta, final int scan,
			final int deviceId) {
		if (!armed) {
			return false;
		}
		// 4 is KEYCODE_BACK. It closes the form instead of becoming the chord.
		if (code == 4 || deviceId == VIRTUAL || scan == 0) {
			return false;
		}
		if (isModifier(code)) {
			if (action == UP) {
				keyCode = code;
				modifiers = 0;
				armed = false;
			}
			return true;
		}
		if (action != DOWN) {
			return true;
		}
		keyCode = code;
		modifiers = KeyboardShortcuts.modifiers(meta);
		armed = false;
		return true;
	}

	private static boolean isModifier(final int code) {
		return code == 57 || code == 58 || code == 59 || code == 60
				|| code == 113 || code == 114 || code == 117 || code == 118;
	}
}
