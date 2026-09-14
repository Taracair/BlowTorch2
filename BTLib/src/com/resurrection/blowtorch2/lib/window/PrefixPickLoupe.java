package com.resurrection.blowtorch2.lib.window;

/**
 * Size and zoom for the {@code .pick} magnifier. Percents: 100 size is the
 * original circle; 200 zoom is 2×. Caption prefers the gap above the circle.
 */
public final class PrefixPickLoupe {

	public static final int DEFAULT_SIZE = 118;
	public static final int MIN_SIZE = 50;
	public static final int MAX_SIZE = 200;
	public static final int DEFAULT_ZOOM = 200;
	public static final int MIN_ZOOM = 150;
	public static final int MAX_ZOOM = 350;
	/** Phone-sized circle at size 100, in dp. */
	public static final float BASE_RADIUS_DP = 50f;

	private PrefixPickLoupe() {
	}

	public static int clampSize(final int percent) {
		if (percent < MIN_SIZE) {
			return MIN_SIZE;
		}
		if (percent > MAX_SIZE) {
			return MAX_SIZE;
		}
		return percent;
	}

	public static int clampZoom(final int percent) {
		if (percent < MIN_ZOOM) {
			return MIN_ZOOM;
		}
		if (percent > MAX_ZOOM) {
			return MAX_ZOOM;
		}
		return percent;
	}

	public static float radiusPx(final float density, final int sizePercent) {
		float d = density <= 0f ? 1f : density;
		return BASE_RADIUS_DP * d * (clampSize(sizePercent) / 100f);
	}

	public static float scale(final int zoomPercent) {
		return clampZoom(zoomPercent) / 100f;
	}

	/**
	 * Left edge of column {@code col} in the window's cell grid (same space
	 * as {@code Window}'s highlight box and per-glyph loupe text).
	 */
	public static float cellLeft(final int col, final float cellPx,
			final float scrollX) {
		return col * cellPx - scrollX;
	}

	/**
	 * Caption panel. Prefers above the circle; falls below when that would
	 * clip the top. {@code out} is left, top, right, bottom (length ≥ 4).
	 */
	public static void captionBox(final float cx, final float cy, final float r,
			final float panelW, final float panelH, final float gap,
			final float viewW, final float viewH, final float[] out) {
		float px = cx - panelW / 2f;
		if (px < 4f) {
			px = 4f;
		}
		if (px + panelW > viewW - 4f) {
			px = viewW - panelW - 4f;
		}
		float py = cy - r - gap - panelH;
		if (py < 4f) {
			py = cy + r + gap;
		}
		if (py + panelH > viewH - 4f) {
			py = viewH - panelH - 4f;
		}
		out[0] = px;
		out[1] = py;
		out[2] = px + panelW;
		out[3] = py + panelH;
	}
}
