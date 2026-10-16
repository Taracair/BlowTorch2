package com.resurrection.blowtorch2.lib.button;

/**
 * Label origin inside a button rect. Center uses the same baseline as
 * {@code FloatingButtonView}: {@code mid - (ascent + descent) / 2}.
 * {@code pad} applies to the edges only.
 */
public final class ButtonLabelAnchor {

	private ButtonLabelAnchor() {
	}

	public static float left(final float left, final float right, final float textWidth,
			final String alignH, final float pad) {
		if ("left".equals(alignH)) {
			return left + pad;
		}
		if ("right".equals(alignH)) {
			return right - pad - textWidth;
		}
		return (left + right) * 0.5f - textWidth * 0.5f;
	}

	public static float baseline(final float top, final float bottom, final float ascent,
			final float descent, final String alignV, final float pad) {
		if ("top".equals(alignV)) {
			return top + pad - ascent;
		}
		if ("bottom".equals(alignV)) {
			return bottom - pad - descent;
		}
		float mid = (top + bottom) * 0.5f;
		return mid - (ascent + descent) * 0.5f;
	}

	public static float blockTop(final float top, final float bottom, final float blockHeight,
			final String alignV, final float pad) {
		if ("top".equals(alignV)) {
			return top + pad;
		}
		if ("bottom".equals(alignV)) {
			return bottom - pad - blockHeight;
		}
		return (top + bottom) * 0.5f - blockHeight * 0.5f;
	}
}
