package com.resurrection.blowtorch2.lib.window;

/**
 * The form's "press a key" row. It keeps the latest key until {@link #disarm}.
 * A virtual key is recorded only while listening, so a text field can still type.
 */
public final class KeyboardShortcutCapture {

	public static final int NONE = -1;
	private static final int DOWN = 0;
	private static final int UP = 1;

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

	/** Stop listening. The key already shown stays. */
	public void disarm() {
		armed = false;
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
		if (keyCode < 0) {
			return armed ? "Press a key" : "nothing";
		}
		String chord = KeyboardShortcuts.chordLabel(keyCode, modifiers);
		if (armed) {
			return "Press a key: " + chord;
		}
		return chord;
	}

	/** @return true when the form should keep the event */
	public boolean onKey(final int action, final int code, final int meta, final int scan,
			final int deviceId) {
		if (!armed) {
			return false;
		}
		// 4 is KEYCODE_BACK. It closes the form instead of becoming the chord.
		if (code == 4) {
			return false;
		}
		if (isModifier(code)) {
			if (keyCode < 0 || isModifier(keyCode)) {
				keyCode = code;
				modifiers = 0;
			}
			return true;
		}
		if (action == DOWN) {
			keyCode = code;
			modifiers = KeyboardShortcuts.modifiers(meta);
			return true;
		}
		// Key-up counts only while the row still shows a modifier or nothing.
		if (action == UP && (keyCode < 0 || isModifier(keyCode))) {
			keyCode = code;
			modifiers = KeyboardShortcuts.modifiers(meta);
		}
		return true;
	}

	/** Ctrl+Up and Ctrl+Down. Their key-down does not reach the dialog. */
	public static boolean ctrlVerticalArrow(final int code, final int meta) {
		if (code != 19 && code != 20) {
			return false;
		}
		return (KeyboardShortcuts.modifiers(meta) & KeyboardShortcuts.MOD_CTRL) != 0;
	}

	private static boolean isModifier(final int code) {
		return code == 57 || code == 58 || code == 59 || code == 60
				|| code == 113 || code == 114 || code == 117 || code == 118;
	}
}
