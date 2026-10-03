package com.resurrection.blowtorch2.lib.window;

/**
 * Finger travel → how many copy-widget cells to walk.
 *
 * Slow and ordinary dragging is 1:1 with leftover pixels kept. Gain only
 * starts on a real flick, so a quick nudge does not leap.
 */
public final class CopyLoupeDrag {

	/** Below this, gain is 1 (px/ms). Ordinary dragging stays here. */
	public static final float SLOW_PX_PER_MS = 1.20f;
	/** At or above this, gain is {@link #MAX_GAIN}. */
	public static final float FAST_PX_PER_MS = 3.50f;
	public static final float MAX_GAIN = 2.0f;
	/** One ACTION_MOVE will not skip the whole buffer. */
	public static final int MAX_CELLS = 24;
	/** Floor so a 1 ms / 2 px cluster is not treated as a flick. */
	public static final float MIN_DT_MS = 8f;

	private CopyLoupeDrag() {
	}

	public static float gain(final float pxPerMs) {
		if (pxPerMs <= SLOW_PX_PER_MS) {
			return 1f;
		}
		float span = FAST_PX_PER_MS - SLOW_PX_PER_MS;
		float t = (pxPerMs - SLOW_PX_PER_MS) / span;
		if (t > 1f) {
			t = 1f;
		}
		return 1f + t * (MAX_GAIN - 1f);
	}

	/**
	 * Add {@code deltaPx} into {@code rem[axis]} and return how many cells
	 * that is worth (signed, toward zero). Leftover stays in {@code rem[axis]}.
	 */
	public static int cells(final float[] rem, final int axis,
			final float deltaPx, final float cellPx, final float dtMs) {
		if (rem == null || axis < 0 || axis >= rem.length || cellPx <= 0f) {
			return 0;
		}
		float dt = dtMs < MIN_DT_MS ? MIN_DT_MS : dtMs;
		float speed = Math.abs(deltaPx) / dt;
		rem[axis] += deltaPx * gain(speed);
		int n = (int) (rem[axis] / cellPx);
		rem[axis] -= n * cellPx;
		if (n > MAX_CELLS) {
			n = MAX_CELLS;
			rem[axis] = 0f;
		} else if (n < -MAX_CELLS) {
			n = -MAX_CELLS;
			rem[axis] = 0f;
		}
		return n;
	}
}
