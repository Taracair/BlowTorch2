package com.resurrection.blowtorch2.lib.window;

/**
 * Pure placement maths for floating-layer children: clamp to a keep-out box and
 * resolve the unplaced sentinel ({@link #UNPLACED}) to a default above the
 * input bar. No Android — same extract → test pattern as
 * {@link SuperButtonGestures}.
 */
public final class FloatingLayerGeometry {

	/** Stored {@code floatX}/{@code floatY} meaning “never placed yet”. */
	public static final int UNPLACED = -1;

	/**
	 * Default inset when unplaced, in the same units as stored {@code floatX}/
	 * {@code floatY} (dp, matching ExtraText overlay positions). Callers that
	 * lay out in pixels must scale by density first.
	 */
	public static final int DEFAULT_MARGIN_DP = 24;

	private FloatingLayerGeometry() {
	}

	/**
	 * Resolve a stored X: unplaced becomes a left margin; otherwise the value
	 * as stored (caller still clamps). Units are whatever the layer stores
	 * (dp recommended).
	 */
	public static int resolveX(final int floatX) {
		if (floatX == UNPLACED) {
			return DEFAULT_MARGIN_DP;
		}
		return floatX;
	}

	/**
	 * Resolve a stored Y: unplaced sits just above {@code maxBottom} (input-bar
	 * top / chrome keep-out), minus height and a margin; otherwise as stored.
	 *
	 * @param floatY stored Y, or {@link #UNPLACED}
	 * @param childHeight height of the floating view in the same units
	 * @param maxBottom exclusive bottom of the free area (e.g. input-bar top)
	 */
	public static int resolveY(final int floatY, final int childHeight, final int maxBottom) {
		if (floatY == UNPLACED) {
			return Math.max(0, maxBottom - childHeight - DEFAULT_MARGIN_DP);
		}
		return floatY;
	}

	/** Clamp left so the child stays in {@code [0, parentWidth - childWidth]}. */
	public static int clampX(final int x, final int childWidth, final int parentWidth) {
		int maxLeft = Math.max(0, parentWidth - childWidth);
		if (x < 0) {
			return 0;
		}
		if (x > maxLeft) {
			return maxLeft;
		}
		return x;
	}

	/**
	 * Clamp top so the child stays in {@code [0, maxBottom - childHeight]}.
	 * {@code maxBottom} is the exclusive bottom of the free area (input bar /
	 * ⋮ keep-out already folded in by the caller).
	 */
	public static int clampY(final int y, final int childHeight, final int maxBottom) {
		int maxTop = Math.max(0, maxBottom - childHeight);
		if (y < 0) {
			return 0;
		}
		if (y > maxTop) {
			return maxTop;
		}
		return y;
	}

	/**
	 * Resolve unplaced defaults then clamp. One call for layout restore.
	 *
	 * @return {@code int[]{x, y}}
	 */
	public static int[] place(final int floatX, final int floatY,
			final int childWidth, final int childHeight,
			final int parentWidth, final int maxBottom) {
		int x = clampX(resolveX(floatX), childWidth, parentWidth);
		int y = clampY(resolveY(floatY, childHeight, maxBottom), childHeight, maxBottom);
		return new int[] { x, y };
	}
}
