package com.resurrection.blowtorch2.lib.mapper;

/**
 * A stationary two-finger tap. Travel past the slop, a third finger, or a
 * linger past {@link #WINDOW_MS} is a pinch or a hold.
 */
public final class TwoFingerTap {

	public static final long WINDOW_MS = 400L;

	private boolean tracking;
	private boolean moved;
	private long secondAt;

	public void primaryDown() {
		tracking = false;
		moved = false;
	}

	public void secondDown(final long nowMs) {
		secondDown(nowMs, false);
	}

	/**
	 * @param priorPastSlop the first finger already travelled past the slop
	 *        before this one landed, so the gesture is a pan, not a tap
	 */
	public void secondDown(final long nowMs, final boolean priorPastSlop) {
		tracking = true;
		moved = priorPastSlop;
		secondAt = nowMs;
	}

	public void extraFinger() {
		tracking = false;
	}

	public void move(final float fromX, final float fromY, final float x, final float y,
			final int slopPx) {
		if (!tracking || moved) {
			return;
		}
		if (Math.abs(x - fromX) > slopPx || Math.abs(y - fromY) > slopPx) {
			moved = true;
		}
	}

	/**
	 * @param fingersLeft pointers still down after this lift
	 * @return true once, when both fingers have lifted inside the window
	 *         without travelling past the slop
	 */
	public boolean lift(final int fingersLeft, final long nowMs) {
		if (!tracking || fingersLeft > 0) {
			return false;
		}
		boolean tap = !moved && nowMs >= secondAt && nowMs - secondAt <= WINDOW_MS;
		tracking = false;
		return tap;
	}

	public void cancel() {
		tracking = false;
	}
}
