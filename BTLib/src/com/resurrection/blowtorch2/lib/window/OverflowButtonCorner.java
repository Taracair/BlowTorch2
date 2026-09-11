package com.resurrection.blowtorch2.lib.window;

/**
 * Options → Miscellaneous → Overflow button corner.
 *
 * <p>Values are {@code ListOption} indices and are what lands in the profile.
 * Do not reorder, insert in the middle, or renumber.
 *
 * <p>Gravity bits match {@code android.view.Gravity} so the UI can use
 * {@link #gravity(int)} as {@code FrameLayout.LayoutParams.gravity} and JVM
 * tests can pin the combination without Android.
 */
public final class OverflowButtonCorner {

	/** Bottom right. Default; omitted from the profile when 0. */
	public static final int BOTTOM_RIGHT = 0;
	public static final int BOTTOM_LEFT = 1;
	public static final int TOP_RIGHT = 2;
	public static final int TOP_LEFT = 3;
	public static final int DEFAULT = BOTTOM_RIGHT;

	/** Same bits as {@code android.view.Gravity.TOP}. */
	public static final int GRAVITY_TOP = 0x00000030;
	/** Same bits as {@code android.view.Gravity.BOTTOM}. */
	public static final int GRAVITY_BOTTOM = 0x00000050;
	/** Same bits as {@code android.view.Gravity.START}. */
	public static final int GRAVITY_START = 0x00800003;
	/** Same bits as {@code android.view.Gravity.END}. */
	public static final int GRAVITY_END = 0x00800005;

	private OverflowButtonCorner() {
	}

	public static int clamp(int index) {
		if (index < BOTTOM_RIGHT || index > TOP_LEFT) {
			return DEFAULT;
		}
		return index;
	}

	public static boolean isTop(int index) {
		int i = clamp(index);
		return i == TOP_RIGHT || i == TOP_LEFT;
	}

	public static boolean isStart(int index) {
		int i = clamp(index);
		return i == BOTTOM_LEFT || i == TOP_LEFT;
	}

	/** Bottom corners ride with the input bar; top corners must not. */
	public static boolean liftWithIme(int index) {
		return !isTop(index);
	}

	public static boolean popupOpensDown(int index) {
		return isTop(index);
	}

	public static int gravity(int index) {
		int v = isTop(index) ? GRAVITY_TOP : GRAVITY_BOTTOM;
		int h = isStart(index) ? GRAVITY_START : GRAVITY_END;
		return v | h;
	}

	public static boolean keepOutAtBottom(int index) {
		return liftWithIme(index);
	}

	public static boolean keepOutAtTop(int index) {
		return isTop(index);
	}

	/** True when {@code [overlayLeft, overlayRight)} overlaps the strip in X. */
	public static boolean overlapsX(int overlayLeft, int overlayRight,
			int stripLeft, int stripRight) {
		return overlayRight > stripLeft && overlayLeft < stripRight;
	}

	public static int clampBottomKeepOut(int index, int inputBarTop,
			boolean overlapsX, int stripTop) {
		if (!keepOutAtBottom(index) || !overlapsX) {
			return inputBarTop;
		}
		return stripTop < inputBarTop ? stripTop : inputBarTop;
	}

	public static int clampTopKeepOut(int index, int minTop,
			boolean overlapsX, int stripBottom) {
		if (!keepOutAtTop(index) || !overlapsX) {
			return minTop;
		}
		return stripBottom > minTop ? stripBottom : minTop;
	}

	/**
	 * Popup height, vertical offset, and whether to overlap the ⋮.
	 *
	 * @return {@code {height, verticalOffset, overlapAnchor}} with
	 *         {@code overlapAnchor} 1 or 0
	 */
	public static int[] popupVertical(int index, int anchorTop, int anchorHeight,
			int screenH, int margin, int minHeight) {
		return popupVertical(index, anchorTop, anchorHeight, screenH, margin,
				minHeight, 0);
	}

	/**
	 * @param imeCoveredPx keyboard overlap at the bottom of the window
	 *        ({@code adjustNothing} does not shrink {@code screenH})
	 */
	public static int[] popupVertical(int index, int anchorTop, int anchorHeight,
			int screenH, int margin, int minHeight, int imeCoveredPx) {
		int maxHeight = (int) (screenH * 0.85f);
		int covered = Math.max(0, imeCoveredPx);
		if (popupOpensDown(index)) {
			int available = screenH - anchorTop - anchorHeight - margin - covered;
			int height = Math.max(available, minHeight);
			height = Math.min(height, maxHeight);
			if (height < 1) {
				height = Math.max(1, minHeight);
			}
			return new int[] { height, 0, 0 };
		}
		int height = Math.max(anchorTop - margin, minHeight);
		height = Math.min(height, maxHeight);
		return new int[] { height, -height, 1 };
	}

	/**
	 * Unplaced suggestion chips share the overlay's bottom-start slot with
	 * bottom-left ⋮. Shift them right by the strip so ⋮ does not eat the grip.
	 */
	public static int unplacedChipLeft(int index, int baseLeft, int stripWidth,
			int gap) {
		if (clamp(index) != BOTTOM_LEFT) {
			return baseLeft;
		}
		int w = Math.max(0, stripWidth);
		int g = Math.max(0, gap);
		return baseLeft + w + g;
	}
}
