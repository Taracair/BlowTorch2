package com.resurrection.blowtorch2.lib.window;

/**
 * Which recent-command row a finger is on. Android-free so a drag that
 * changes rows can be pinned without a window.
 */
public final class LastListRows {

	public static final int FONT_MIN = 10;
	public static final int FONT_MAX = 32;
	public static final int FONT_DEFAULT = 14;

	private LastListRows() {
	}

	public static int fontSp(final int value) {
		return Math.max(FONT_MIN, Math.min(FONT_MAX, value));
	}

	/**
	 * @param y finger position in the same coordinates as the row bounds
	 * @param top top of each row, same length as {@code bottom}
	 * @param bottom bottom of each row
	 * @return the row index, or -1 when the finger is between rows or outside
	 */
	public static int rowAt(final int y, final int[] top, final int[] bottom) {
		if (top == null || bottom == null || top.length != bottom.length) {
			return -1;
		}
		for (int i = 0; i < top.length; i++) {
			if (y >= top[i] && y < bottom[i]) {
				return i;
			}
		}
		return -1;
	}
}
