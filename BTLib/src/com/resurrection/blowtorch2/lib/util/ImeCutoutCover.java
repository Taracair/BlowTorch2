package com.resurrection.blowtorch2.lib.util;

/**
 * Side strips that hide live game in the camera hole while the IME is up.
 * The keyboard itself is a separate window and still does not paint there.
 * Android-free so the matrix is JVM-tested.
 */
public final class ImeCutoutCover {

	private ImeCutoutCover() {
	}

	/**
	 * @return {@code {leftWidth, rightWidth, height}} in px, or zeros when
	 *         nothing should be covered
	 */
	public static int[] sideCover(final boolean landscape, final boolean imeVisible,
			final int imeBottom, final boolean avoidLandscape, final int cutLeft,
			final int cutRight) {
		if (!landscape || !imeVisible || imeBottom <= 0 || avoidLandscape) {
			return new int[] { 0, 0, 0 };
		}
		int left = Math.max(0, cutLeft);
		int right = Math.max(0, cutRight);
		if (left == 0 && right == 0) {
			return new int[] { 0, 0, 0 };
		}
		return new int[] { left, right, imeBottom };
	}
}
