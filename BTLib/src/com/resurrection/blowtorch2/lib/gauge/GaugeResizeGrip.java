/*
 * Copyright (C) BlowTorch contributors
 */
package com.resurrection.blowtorch2.lib.gauge;

/**
 * Bottom-right resize grip as a square of {@link #GRIP_DP}. Pure ints; the View
 * converts dp → px and draws.
 */
public final class GaugeResizeGrip {

	public static final int GRIP_DP = 20;

	private GaugeResizeGrip() {
	}

	public static int gripPx(final float density) {
		float d = density > 0f ? density : 1f;
		int px = Math.round(GRIP_DP * d);
		return px < 1 ? 1 : px;
	}

	/**
	 * Grip rectangle {@code left, top, right, bottom} (right/bottom exclusive),
	 * a square in the bottom-right. Side is {@code min(gripPx, viewW, viewH)}.
	 */
	public static int[] rect(final int viewW, final int viewH, final int gripPx) {
		if (viewW <= 0 || viewH <= 0) {
			return new int[] { 0, 0, 0, 0 };
		}
		int side = gripPx;
		if (side < 1) {
			side = 1;
		}
		if (side > viewW) {
			side = viewW;
		}
		if (side > viewH) {
			side = viewH;
		}
		return new int[] { viewW - side, viewH - side, viewW, viewH };
	}

	public static boolean contains(final float x, final float y, final int viewW,
			final int viewH, final int gripPx) {
		int[] r = rect(viewW, viewH, gripPx);
		return x >= r[0] && y >= r[1] && x < r[2] && y < r[3];
	}
}
