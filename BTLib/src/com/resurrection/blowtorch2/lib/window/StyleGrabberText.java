package com.resurrection.blowtorch2.lib.window;

/**
 * Grabber labels that would stretch the panel (the Text layer is a whole SGR
 * run) cut with {@code ...} instead.
 */
public final class StyleGrabberText {

	public static final String DOTS = "...";

	public interface Widths {
		float measure(String s);
	}

	private StyleGrabberText() {
	}

	public static String ellipsize(final String s, final float maxWidth,
			final Widths widths) {
		if (s == null || s.length() == 0) {
			return s == null ? "" : s;
		}
		if (widths == null || maxWidth == Float.POSITIVE_INFINITY) {
			return s;
		}
		if (widths.measure(s) <= maxWidth) {
			return s;
		}
		float dotsW = widths.measure(DOTS);
		if (maxWidth <= dotsW) {
			return DOTS;
		}
		int lo = 0;
		int hi = s.length();
		while (lo < hi) {
			int mid = (lo + hi + 1) >>> 1;
			if (widths.measure(s.substring(0, mid) + DOTS) <= maxWidth) {
				lo = mid;
			} else {
				hi = mid - 1;
			}
		}
		if (lo <= 0) {
			return DOTS;
		}
		if (Character.isHighSurrogate(s.charAt(lo - 1))) {
			lo--;
			if (lo <= 0) {
				return DOTS;
			}
		}
		return s.substring(0, lo) + DOTS;
	}
}
