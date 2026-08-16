package com.resurrection.blowtorch2.lib.window;

import java.util.ArrayDeque;

/**
 * Line-level memory for "dim repeated room text". Remembers the last
 * {@link #DEFAULT_WINDOW} long stripped lines (FIFO, including duplicates)
 * and says whether the one just offered is already in that window.
 *
 * <p>Pure Java: the caller strips ANSI and passes plain text. Short lines
 * (prompts, "Ok.", "You sit.") are neither remembered nor dimmed.
 *
 * <p>FIFO with duplicates so combat and other rooms flush an old look after
 * about a screen of other long lines, rather than keeping sixty unique rooms.
 */
public final class RepeatedLineDimmer {

	public static final int DEFAULT_WINDOW = 12;
	public static final int MIN_WINDOW = 1;
	public static final int MAX_WINDOW = 80;
	public static final int DEFAULT_STRENGTH = 50;
	public static final int MIN_STRENGTH = 10;
	public static final int MAX_STRENGTH = 90;
	public static final int MIN_CHARS = 24;

	private int windowSize;
	private final ArrayDeque<String> recent = new ArrayDeque<String>();

	public RepeatedLineDimmer() {
		this(DEFAULT_WINDOW);
	}

	public RepeatedLineDimmer(final int windowSize) {
		this.windowSize = clampWindow(windowSize);
	}

	public void setWindowSize(final int n) {
		windowSize = clampWindow(n);
		while (recent.size() > windowSize) {
			recent.removeFirst();
		}
	}

	public int getWindowSize() {
		return windowSize;
	}

	/**
	 * @param stripped plain text of one finished line (no ANSI). Whitespace is
	 *        collapsed here.
	 * @return true when this long line was already in the window
	 */
	public boolean rememberAndShouldDim(final String stripped) {
		if (stripped == null) {
			return false;
		}
		final String key = collapseWhitespace(stripped);
		if (key.length() == 0) {
			return false;
		}
		if (key.length() < MIN_CHARS) {
			return false;
		}
		final boolean dim = recent.contains(key);
		recent.addLast(key);
		while (recent.size() > windowSize) {
			recent.removeFirst();
		}
		return dim;
	}

	public static int clampWindow(final int n) {
		if (n < MIN_WINDOW) {
			return MIN_WINDOW;
		}
		if (n > MAX_WINDOW) {
			return MAX_WINDOW;
		}
		return n;
	}

	public static int clampStrength(final int n) {
		if (n < MIN_STRENGTH) {
			return MIN_STRENGTH;
		}
		if (n > MAX_STRENGTH) {
			return MAX_STRENGTH;
		}
		return n;
	}

	/**
	 * Remaining brightness for a given dim strength. Strength 50 keeps half
	 * the colour (the original draw); higher is darker.
	 */
	public static float keepFactor(final int strengthPercent) {
		return (100 - clampStrength(strengthPercent)) / 100f;
	}

	/** Scale RGB toward black; alpha is unchanged. */
	public static int dimForeground(final int color, final int strengthPercent) {
		final float keep = keepFactor(strengthPercent);
		final int r = (int) (((color >> 16) & 0xFF) * keep);
		final int g = (int) (((color >> 8) & 0xFF) * keep);
		final int b = (int) ((color & 0xFF) * keep);
		return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
	}

	/** Trim, then collapse any run of whitespace to a single space. */
	static String collapseWhitespace(final String s) {
		final int n = s.length();
		final StringBuilder out = new StringBuilder(n);
		boolean pendingSpace = false;
		boolean any = false;
		for (int i = 0; i < n; i++) {
			final char c = s.charAt(i);
			if (c <= ' ' || Character.isWhitespace(c)) {
				if (any) {
					pendingSpace = true;
				}
			} else {
				if (pendingSpace) {
					out.append(' ');
					pendingSpace = false;
				}
				out.append(c);
				any = true;
			}
		}
		return out.toString();
	}
}
