package com.resurrection.blowtorch2.lib.window;

/**
 * Numpad 8's second event is DPAD_UP, scan 72, flags 0x408. A real arrow is not a fallback.
 * The twin key-down moves focus. Actions are KeyEvent values: down 0, up 1.
 */
public final class KeyboardShortcutFocus {

	public static final int KEYCODE_DPAD_UP = 19;
	public static final int KEYCODE_DPAD_DOWN = 20;
	public static final int KEYCODE_DPAD_LEFT = 21;
	public static final int KEYCODE_DPAD_RIGHT = 22;
	public static final int KEYCODE_DPAD_CENTER = 23;

	private boolean barHadFocus;
	private int downCode = -1;
	private boolean focusOnThisEvent;

	/** True when the activity consumes the event. A key-up stays unconsumed when the bar already had the cursor. */
	public boolean consume(final int action, final int keyCode, final int flags, final int scan,
			final int deviceId, final int repeat, final boolean barFocused) {
		focusOnThisEvent = false;
		if (!isTwin(keyCode, flags, scan, deviceId)) {
			return false;
		}
		if (action == KeyboardShortcutMatcher.ACTION_DOWN && repeat == 0) {
			barHadFocus = barFocused;
			downCode = keyCode;
			focusOnThisEvent = !barFocused;
			return true;
		}
		if (action == KeyboardShortcutMatcher.ACTION_UP) {
			boolean had = downCode == keyCode ? barHadFocus : barFocused;
			if (downCode == keyCode) {
				downCode = -1;
			}
			focusOnThisEvent = !had;
			return !had;
		}
		return true;
	}

	/** Valid after {@link #consume}. True when this event should put the cursor in the bar. */
	public boolean focusBar() {
		return focusOnThisEvent;
	}

	public static boolean isTwin(final int keyCode, final int flags, final int scan, final int deviceId) {
		if ((flags & KeyboardShortcutMatcher.FLAG_FALLBACK) == 0 || scan == 0
				|| deviceId == KeyboardShortcutMatcher.VIRTUAL_DEVICE_ID) {
			return false;
		}
		return keyCode >= KEYCODE_DPAD_UP && keyCode <= KEYCODE_DPAD_CENTER;
	}

	/**
	 * Real arrow up or down, key-down. The activity eats it so focus does not
	 * walk onto Send, Edit, or the overflow button. The key-up still reaches the bar.
	 */
	public static boolean blocksFocusWalk(final int action, final int keyCode, final int flags,
			final int scan, final int deviceId) {
		if (action != KeyboardShortcutMatcher.ACTION_DOWN) {
			return false;
		}
		if (keyCode != KEYCODE_DPAD_UP && keyCode != KEYCODE_DPAD_DOWN) {
			return false;
		}
		if (scan == 0 || deviceId == KeyboardShortcutMatcher.VIRTUAL_DEVICE_ID) {
			return false;
		}
		return (flags & KeyboardShortcutMatcher.FLAG_FALLBACK) == 0;
	}
}
