package com.resurrection.blowtorch2.lib.util;

/**
 * Padding for {@code window_container} so game chrome can stay out of a camera
 * cutout. Android-free so the orientation/option matrix is JVM-tested.
 *
 * <p>Bottom is always the nav-bar inset; the cutout's bottom is not added
 * (that is often the same band as the gesture pill).
 */
public final class DisplayCutoutPad {

	private DisplayCutoutPad() {
	}

	/**
	 * @return {@code {left, top, right, bottom}}
	 */
	public static int[] containerPadding(
			final int barsLeft, final int barsTop, final int barsRight,
			final int barsBottom,
			final int cutLeft, final int cutTop, final int cutRight,
			final boolean landscape, final boolean fullscreen,
			final boolean avoidPortrait, final boolean avoidLandscape) {
		boolean avoid = landscape ? avoidLandscape : avoidPortrait;
		int left = barsLeft;
		int right = barsRight;
		int bottom = barsBottom;
		int top = 0;
		if (avoid) {
			left = Math.max(left, cutLeft);
			right = Math.max(right, cutRight);
			int trustedTop = fullscreen ? 0 : barsTop;
			// Lua still adds GetStatusBarHeight() as statusoffset because the
			// container top used to be 0. Only the hole that sticks past that
			// band belongs here.
			top = Math.max(0, cutTop - trustedTop);
		}
		return new int[] { left, top, right, bottom };
	}
}
