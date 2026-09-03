package com.resurrection.blowtorch2.lib.window;

/**
 * Swipe/hold timing matching {@code buttonwindow.lua}. On floating views, hold
 * fires on {@code ACTION_UP} in [{@link #HOLD_DELAY_MS}, {@link #MOVE_HOLD_MS});
 * at {@link #MOVE_HOLD_MS} enter move without firing hold. Travel past
 * {@link #HOLD_CANCEL_MOVE_DP} cancels hold. Swipe from {@link #resolveSwipeDirection}
 * wins over tap/flip.
 */
public final class SuperButtonGestures {

	/** Same as {@code SWIPE_THRESHOLD_DP} in buttonwindow.lua. */
	public static final int SWIPE_THRESHOLD_DP = 24;

	/** Same as {@code HOLD_DELAY_MS} in buttonwindow.lua. */
	public static final int HOLD_DELAY_MS = 450;

	/**
	 * Same as {@code HOLD_CANCEL_MOVE_DP} in buttonwindow.lua. Finger travel
	 * beyond {@code HOLD_CANCEL_MOVE_DP * density} cancels a pending hold.
	 */
	public static final int HOLD_CANCEL_MOVE_DP = 10;

	/**
	 * Very-long-press threshold for picking up a floating button to drag.
	 * Longer than {@link #HOLD_DELAY_MS} so hold and move are separable when
	 * hold is deferred to release (see class javadoc).
	 */
	public static final int MOVE_HOLD_MS = 2000;

	public static final String DIR_RIGHT = "right";
	public static final String DIR_UP_RIGHT = "upright";
	public static final String DIR_UP = "up";
	public static final String DIR_UP_LEFT = "upleft";
	public static final String DIR_LEFT = "left";
	public static final String DIR_DOWN_LEFT = "downleft";
	public static final String DIR_DOWN = "down";
	public static final String DIR_DOWN_RIGHT = "downright";

	/** Sector order starting at right, counter-clockwise, matching Lua. */
	private static final String[] SWIPE8_SECTORS = {
		DIR_RIGHT, DIR_UP_RIGHT, DIR_UP, DIR_UP_LEFT,
		DIR_LEFT, DIR_DOWN_LEFT, DIR_DOWN, DIR_DOWN_RIGHT,
	};

	private SuperButtonGestures() {
	}

	/**
	 * Four-way classifier ({@code classifySwipe} in Lua). Dead zone when
	 * {@code |dx|} and {@code |dy|} are both below threshold.
	 *
	 * @return one of up/down/left/right, or null in the dead zone
	 */
	public static String classifySwipe4(final float dx, final float dy, final float threshold) {
		if (Math.abs(dx) < threshold && Math.abs(dy) < threshold) {
			return null;
		}
		if (Math.abs(dx) >= Math.abs(dy)) {
			return dx > 0 ? DIR_RIGHT : DIR_LEFT;
		}
		return dy > 0 ? DIR_DOWN : DIR_UP;
	}

	/**
	 * Eight-way classifier ({@code classifySwipe8} in Lua). Same dead zone as
	 * {@link #classifySwipe4}. Angle uses {@code atan2(-dy, dx)} because screen
	 * y grows downward; {@code +22.5} centres cardinals in a sector.
	 *
	 * @return one of the eight direction names, or null in the dead zone
	 */
	public static String classifySwipe8(final float dx, final float dy, final float threshold) {
		if (Math.abs(dx) < threshold && Math.abs(dy) < threshold) {
			return null;
		}
		double angle = Math.toDegrees(Math.atan2(-dy, dx));
		if (angle < 0) {
			angle += 360;
		}
		int sector = (int) Math.floor((angle + 22.5) / 45.0) % 8;
		return SWIPE8_SECTORS[sector];
	}

	/**
	 * Which swipe this movement will fire, or null for none.
	 *
	 * <p>Prefer the eight-way sector when that direction is bound; otherwise fall
	 * back to the four-way direction. Same function for live preview and
	 * finger-up dispatch.
	 *
	 * @param bound which directions have a non-empty command
	 * @param dx finger delta x (same units as threshold)
	 * @param dy finger delta y
	 * @param threshold swipe threshold (typically {@code SWIPE_THRESHOLD_DP * density})
	 */
	public static String resolveSwipeDirection(final BoundSwipes bound,
			final float dx, final float dy, final float threshold) {
		if (classifySwipe4(dx, dy, threshold) == null) {
			return null;
		}
		String diagonal = classifySwipe8(dx, dy, threshold);
		if (bound != null && bound.isBound(diagonal)) {
			return diagonal;
		}
		String straight = classifySwipe4(dx, dy, threshold);
		if (bound != null && bound.isBound(straight)) {
			return straight;
		}
		return null;
	}

	/**
	 * Whether a press of this length should arm move mode (no hold fires).
	 */
	public static boolean shouldEnterMoveMode(final long pressDurationMs) {
		return pressDurationMs >= MOVE_HOLD_MS;
	}

	/**
	 * Whether movement of this length (same units as threshold, typically px)
	 * cancels a pending hold — grid rule
	 * {@code moveDist > HOLD_CANCEL_MOVE_DP * density}.
	 */
	public static boolean shouldCancelHoldForMove(final float moveDistPx, final float density) {
		return moveDistPx > HOLD_CANCEL_MOVE_DP * density;
	}

	/**
	 * Whether hold should fire on {@code ACTION_UP} for a floating button that
	 * deferred hold: duration in [{@link #HOLD_DELAY_MS}, {@link #MOVE_HOLD_MS}),
	 * never entered move. Does not account for hold-cancel-by-move; prefer
	 * {@link #shouldFireHoldOnRelease(long, boolean, boolean)}.
	 */
	public static boolean shouldFireHoldOnRelease(final long pressDurationMs,
			final boolean enteredMoveMode) {
		return shouldFireHoldOnRelease(pressDurationMs, enteredMoveMode, false);
	}

	/**
	 * Whether hold should fire on {@code ACTION_UP} for a floating button that
	 * deferred hold: duration in [{@link #HOLD_DELAY_MS}, {@link #MOVE_HOLD_MS}),
	 * never entered move, and hold was not cancelled by movement.
	 *
	 * @param holdCancelledByMove true when {@link #shouldCancelHoldForMove} fired
	 *        during the press (or equivalent)
	 */
	public static boolean shouldFireHoldOnRelease(final long pressDurationMs,
			final boolean enteredMoveMode, final boolean holdCancelledByMove) {
		if (enteredMoveMode || holdCancelledByMove) {
			return false;
		}
		return pressDurationMs >= HOLD_DELAY_MS && pressDurationMs < MOVE_HOLD_MS;
	}

	/**
	 * Which of the eight swipe directions have a non-empty command bound.
	 * Callers set flags from button data; this class never stores commands.
	 */
	public static final class BoundSwipes {
		public boolean up;
		public boolean down;
		public boolean left;
		public boolean right;
		public boolean upLeft;
		public boolean upRight;
		public boolean downLeft;
		public boolean downRight;

		public boolean isBound(final String direction) {
			if (direction == null) {
				return false;
			}
			if (DIR_UP.equals(direction)) {
				return up;
			}
			if (DIR_DOWN.equals(direction)) {
				return down;
			}
			if (DIR_LEFT.equals(direction)) {
				return left;
			}
			if (DIR_RIGHT.equals(direction)) {
				return right;
			}
			if (DIR_UP_LEFT.equals(direction)) {
				return upLeft;
			}
			if (DIR_UP_RIGHT.equals(direction)) {
				return upRight;
			}
			if (DIR_DOWN_LEFT.equals(direction)) {
				return downLeft;
			}
			if (DIR_DOWN_RIGHT.equals(direction)) {
				return downRight;
			}
			return false;
		}
	}
}
