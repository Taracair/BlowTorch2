package com.resurrection.blowtorch2.lib.window;

/**
 * Whether the IME inset should count as "keyboard is up" for floating buttons.
 *
 * <p>No Android. The chrome listener is the authority for lift in px and for
 * {@code isVisible(ime)}; this only turns those readings into a boolean. A hard
 * 120dp floor (the old test) toggled twice when the keyboard animation dipped
 * under the floor and climbed again — attach, detach, attach, which is the
 * double blink on Mode A overlay windows.
 *
 * <p>{@link #SETTLE_MS} matches the inset burst measured in
 * {@code ChromeController} (a retracted event lands within ~150ms). The game
 * window still follows every inset so the slide stays smooth. Overlay windows
 * wait that long only when <em>appearing</em>; taking them down is immediate,
 * otherwise they hang in the air for the whole hide animation plus the settle.
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
	 * Quiet time before adding overlay windows. Same measurement as
	 * {@code ChromeController}'s inset settle (retracted 0/height pair in 150ms).
	 * Not used when removing them.
	 */
	public static final int SETTLE_MS = 180;

	/**
	 * How far lift must rise, once a hide was requested, before that hide is
	 * treated as cancelled (keyboard coming back during the hide animation).
	 */
	public static final int REOPEN_DP = 24;

	private SoftKeyboardCoverage() {
	}

	/**
	 * Quiet time before committing a covering change. Show waits so a dip
	 * during the open slide does not attach twice; hide is 0 so Mode A windows
	 * leave when covering becomes false, not after the animation plus settle.
	 */
	public static int settleMs(boolean becomingCovered) {
		return becomingCovered ? SETTLE_MS : 0;
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

	/**
	 * A hide was issued (or IME visibility dropped) while lift is still a
	 * keyboard's height. Hysteresis would keep covering true until lift falls
	 * under {@link #HIDE_DP}, which is the end of the hide animation.
	 *
	 * <p>Reopen is a net rise from the lowest lift seen while latched, not a
	 * jump in one inset callback. The keyboard slide is many small insets.
	 */
	public static final class HideRequest {
		private boolean requested;
		private int troughPx = Integer.MAX_VALUE;

		public void request() {
			requested = true;
			troughPx = Integer.MAX_VALUE;
		}

		public void clear() {
			requested = false;
			troughPx = Integer.MAX_VALUE;
		}

		public boolean isRequested() {
			return requested;
		}

		/**
		 * @return true if Mode A must stay hidden regardless of hysteresis
		 */
		public boolean tick(int liftPx, float density) {
			if (!requested) {
				return false;
			}
			float d = density > 0f ? density : 1f;
			int hidePx = Math.round(HIDE_DP * d);
			int reopenPx = Math.round(REOPEN_DP * d);
			int lift = liftPx < 0 ? 0 : liftPx;
			if (lift < troughPx) {
				troughPx = lift;
			}
			if (lift < hidePx) {
				clear();
				return false;
			}
			if (lift >= troughPx + reopenPx) {
				clear();
				return false;
			}
			return true;
		}
	}
}
