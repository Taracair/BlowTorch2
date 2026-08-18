/*
 * Copyright (C) BlowTorch contributors
 */
package com.resurrection.blowtorch2.lib.gauge;

/**
 * Pure overlay geometry. No {@code android.graphics} — callers pass ints and a
 * ratio in 0..1 (values outside that range are clamped).
 */
public final class GaugeGeometry {

	private GaugeGeometry() {
	}

	/**
	 * Left-to-right fill rectangle as {@code left, top, right, bottom}
	 * (Android {@code Rect} convention: right/bottom exclusive).
	 */
	public static int[] hbarFill(final int x, final int y, final int w, final int h,
			final double ratio) {
		int fillW = scaledLength(w, ratio);
		return new int[] { x, y, x + fillW, y + h };
	}

	/**
	 * Bottom-to-top fill rectangle as {@code left, top, right, bottom}.
	 */
	public static int[] vbarFill(final int x, final int y, final int w, final int h,
			final double ratio) {
		int fillH = scaledLength(h, ratio);
		int top = y + h - fillH;
		return new int[] { x, top, x + w, y + h };
	}

	/**
	 * Sweep angle in degrees, 0..360, clockwise from 12 o'clock.
	 * <p>
	 * Android {@code Canvas.drawArc} uses 0° at 3 o'clock. The View must pass
	 * {@code startAngle = -90} so this sweep starts at 12 o'clock. {@code TIMER}
	 * shape uses the same sweep (remaining time as a Zelda-style ring).
	 */
	public static float ringSweepDegrees(final double ratio) {
		return (float) (clampRatio(ratio) * 360.0);
	}

	/**
	 * Clamp width/height into {@code [minPx, maxW]} / {@code [minPx, maxH]}.
	 * Min is applied first, then max (so a min above max yields the max).
	 *
	 * @return {@code int[2]} of width, height
	 */
	public static int[] clampSize(final int w, final int h, final int minPx, final int maxW,
			final int maxH) {
		int cw = w;
		int ch = h;
		if (cw < minPx) {
			cw = minPx;
		}
		if (ch < minPx) {
			ch = minPx;
		}
		if (cw > maxW) {
			cw = maxW;
		}
		if (ch > maxH) {
			ch = maxH;
		}
		return new int[] { cw, ch };
	}

	static double clampRatio(final double ratio) {
		if (Double.isNaN(ratio) || ratio <= 0.0) {
			return 0.0;
		}
		if (ratio >= 1.0) {
			return 1.0;
		}
		return ratio;
	}

	private static int scaledLength(final int length, final double ratio) {
		if (length <= 0) {
			return 0;
		}
		double r = clampRatio(ratio);
		if (r <= 0.0) {
			return 0;
		}
		if (r >= 1.0) {
			return length;
		}
		int n = (int) Math.round(length * r);
		if (n < 0) {
			return 0;
		}
		if (n > length) {
			return length;
		}
		return n;
	}
}
