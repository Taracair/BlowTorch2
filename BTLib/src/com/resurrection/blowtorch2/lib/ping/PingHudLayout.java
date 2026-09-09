package com.resurrection.blowtorch2.lib.ping;

/**
 * Overlay geometry for {@code .ping}: percents, opacity, type size. Android-free.
 */
public final class PingHudLayout {

	public static final int OPACITY_MIN = 15;
	public static final int OPACITY_MAX = 100;
	public static final int DEFAULT_OPACITY = 85;

	public static final int SIZE_MIN = 12;
	public static final int SIZE_MAX = 36;
	public static final int DEFAULT_SIZE = 18;

	public static final int PERCENT_MIN = 0;
	public static final int PERCENT_MAX = 100;
	/** Right edge, a little down from the top — not under ⋮ (⋮ is bottom-right). */
	public static final int DEFAULT_X = 100;
	public static final int DEFAULT_Y = 4;

	private PingHudLayout() {
	}

	public static int clampOpacity(final int percent) {
		if (percent < OPACITY_MIN) {
			return OPACITY_MIN;
		}
		if (percent > OPACITY_MAX) {
			return OPACITY_MAX;
		}
		return percent;
	}

	public static int clampSize(final int sp) {
		if (sp < SIZE_MIN) {
			return SIZE_MIN;
		}
		if (sp > SIZE_MAX) {
			return SIZE_MAX;
		}
		return sp;
	}

	public static int clampPercent(final int percent) {
		if (percent < PERCENT_MIN) {
			return PERCENT_MIN;
		}
		if (percent > PERCENT_MAX) {
			return PERCENT_MAX;
		}
		return percent;
	}

	/**
	 * Left/top margin so {@code percent} 0 is flush start and 100 is flush end
	 * of {@code inner} inside {@code outer}.
	 */
	public static int pxFromPercent(final int percent, final int outer, final int inner) {
		int span = outer - inner;
		if (span < 0) {
			span = 0;
		}
		return (int) Math.round(span * (clampPercent(percent) / 100.0));
	}

	public static int percentFromPx(final int px, final int outer, final int inner) {
		int span = outer - inner;
		if (span <= 0) {
			return 0;
		}
		int p = (int) Math.round(100.0 * px / span);
		return clampPercent(p);
	}

	public static float alphaFromOpacity(final int percent) {
		return clampOpacity(percent) / 100f;
	}
}
