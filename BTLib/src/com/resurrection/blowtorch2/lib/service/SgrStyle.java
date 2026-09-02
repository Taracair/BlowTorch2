/*
 * Copyright (C) Dan Block 2013
 */
package com.resurrection.blowtorch2.lib.service;

/**
 * SGR attributes that are not a colour index. One bitset, painted by
 * {@code Window}: italic, underline, strike, reverse, faint.
 *
 * <p>SGR 1 (bold/bright), 2 (faint vs truecolor), 5 ({@code 38;5;n} vs blink)
 * and 22 (normal intensity) stay {@link Colorizer.COLOR_TYPE} so the xterm
 * payload is still consumed before this class sees a number. Call
 * {@link #setFaint(boolean)} from the DIM / 22 path, not {@link #apply(int)}.
 *
 * <p>SGR 21 is underline on (ECMA-48 double underline). It is not xterm
 * bold-off; that is 22.
 */
public final class SgrStyle {

	public static final int ITALIC = 1;
	public static final int UNDERLINE = 2;
	public static final int STRIKE = 4;
	public static final int REVERSE = 8;
	public static final int FAINT = 16;

	/** Mix FG this far toward the paper. Same 50 as the default repeated-line dim. */
	public static final int FAINT_DIM_PERCENT = 50;

	/** Italic paint skew. Not {@code Typeface.ITALIC} — that would miss the grid cache. */
	public static final float ITALIC_SKEW = -0.25f;

	private int bits;

	public int bits() {
		return bits;
	}

	public void setBits(final int value) {
		bits = value;
	}

	public void clear() {
		bits = 0;
	}

	public boolean italic() {
		return (bits & ITALIC) != 0;
	}

	public boolean underline() {
		return (bits & UNDERLINE) != 0;
	}

	public boolean strike() {
		return (bits & STRIKE) != 0;
	}

	public boolean reverse() {
		return (bits & REVERSE) != 0;
	}

	public boolean faint() {
		return (bits & FAINT) != 0;
	}

	public void setFaint(final boolean on) {
		if (on) {
			bits |= FAINT;
		} else {
			bits &= ~FAINT;
		}
	}

	public void clearFaint() {
		bits &= ~FAINT;
	}

	/**
	 * True for the codes this bitset owns. Not 1, 2, 5 or 22 — those must
	 * stay their existing {@link Colorizer.COLOR_TYPE} so {@code 38;5;3} is
	 * colour index 3, not italic.
	 */
	public static boolean isCode(final int value) {
		switch (value) {
		case 3:
		case 4:
		case 7:
		case 9:
		case 21:
		case 23:
		case 24:
		case 27:
		case 29:
			return true;
		default:
			return false;
		}
	}

	public void apply(final int code) {
		switch (code) {
		case 3:
			bits |= ITALIC;
			break;
		case 23:
			bits &= ~ITALIC;
			break;
		case 4:
		case 21:
			bits |= UNDERLINE;
			break;
		case 24:
			bits &= ~UNDERLINE;
			break;
		case 9:
			bits |= STRIKE;
			break;
		case 29:
			bits &= ~STRIKE;
			break;
		case 7:
			bits |= REVERSE;
			break;
		case 27:
			bits &= ~REVERSE;
			break;
		default:
			break;
		}
	}
}
