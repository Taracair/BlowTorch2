package com.resurrection.blowtorch2.lib.window;

/**
 * Whether the IME inset should count as "keyboard is up" for floating buttons.
 *
 * <p>No Android. The chrome listener is the authority for lift in px; this only
 * turns that number into a boolean. A hard 120dp floor (the old test) toggled
 * twice when the keyboard animation dipped under the floor and climbed again —
 * attach, detach, attach, which is the double blink on Mode A overlay windows.
 *
 * <p>{@link #SETTLE_MS} matches the inset burst measured in
 * {@code ChromeController} (a retracted event lands within ~150ms). The game
 * window still follows every inset so the slide stays smooth; only the
 * add/remove of overlay windows waits.
 */
public final class SoftKeyboardCoverage {

	/** Lift must reach this to count as up. Same 120dp floor as before. */
	public static final int SHOW_DP = 120;

	/**
	 * Once up, stay up until lift falls below this. A dip to 90dp during the
	 * slide must not take the windows down.
	 */
	public static final int HIDE_DP = 80;

	/**
	 * Quiet time before adding or removing overlay windows. Same measurement as
	 * {@code ChromeController}'s inset settle (retracted 0/height pair in 150ms).
	 */
	public static final int SETTLE_MS = 180;

	private SoftKeyboardCoverage() {
	}

	/**
	 * @param liftPx IME lift in px (0 = down)
	 * @param density display density; {@code <= 0} treated as 1
	 * @param currentlyCovering last committed covering state (hysteresis)
	 */
	public static boolean isCovering(final int liftPx, final float density,
			final boolean currentlyCovering) {
		float d = density > 0f ? density : 1f;
		int lift = liftPx < 0 ? 0 : liftPx;
		int showPx = Math.round(SHOW_DP * d);
		int hidePx = Math.round(HIDE_DP * d);
		if (hidePx > showPx) {
			hidePx = showPx;
		}
		if (currentlyCovering) {
			return lift >= hidePx;
		}
		return lift >= showPx;
	}
}
