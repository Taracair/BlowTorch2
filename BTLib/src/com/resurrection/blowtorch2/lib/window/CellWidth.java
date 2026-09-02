/*
 * Copyright (C) Dan Block 2013
 */
package com.resurrection.blowtorch2.lib.window;

/**
 * Terminal display width in cells. Used when <em>painting</em> a glyph so a
 * wide character is not clipped to a one-cell sliver.
 *
 * <p>Wrapping, NAWS and {@code Text.charcount} still count one column per
 * code point. A line of only emoji can therefore still overflow the canvas.
 * Do not feed this into wrap until that is an intentional change.
 *
 * <p>ASCII (including space) is 1. Controls and combining marks are 0.
 * East-Asian Wide/Fullwidth (Markus Kuhn ranges) and U+1F000–U+1FAFF
 * (emoji/game tiles) are 2. Stop at U+1FAFF so U+1FB00 sextants stay 1 for
 * ANSI maps. Braille and Block Elements are 1. Everything else (Ambiguous)
 * is 1 — never measure the Paint.
 */
public final class CellWidth {

	private CellWidth() {
	}

	public static int cells(final CharSequence s) {
		if (s == null || s.length() == 0) {
			return 0;
		}
		int n = 0;
		final int len = s.length();
		int i = 0;
		while (i < len) {
			final int cp = Character.codePointAt(s, i);
			n += cells(cp);
			i += Character.charCount(cp);
		}
		return n;
	}

	/** Display cells of {@code s[start, end)} in UTF-16 indices (paint overlays). */
	public static int cells(final CharSequence s, final int start, final int end) {
		if (s == null || start >= end) {
			return 0;
		}
		final int from = start < 0 ? 0 : start;
		final int to = end > s.length() ? s.length() : end;
		int n = 0;
		int i = from;
		while (i < to) {
			final int cp = Character.codePointAt(s, i);
			n += cells(cp);
			i += Character.charCount(cp);
		}
		return n;
	}

	public static int cells(final int cp) {
		if (cp < 0) {
			return 1;
		}
		if (cp <= 0x1F || cp == 0x7F) {
			return 0;
		}
		if (cp <= 0x7E) {
			return 1;
		}
		switch (Character.getType(cp)) {
		case Character.NON_SPACING_MARK:
		case Character.ENCLOSING_MARK:
		case Character.COMBINING_SPACING_MARK:
		case Character.FORMAT:
		case Character.CONTROL:
		case Character.SURROGATE:
			return 0;
		default:
			break;
		}
		if (isWide(cp)) {
			return 2;
		}
		return 1;
	}

	/**
	 * Kuhn wcwidth Wide/Fullwidth, plus the emoji block that baudtest showed
	 * as half a glyph. 0x303F is the halfwidth exception inside the CJK span.
	 */
	private static boolean isWide(final int cp) {
		if (cp >= 0x1100 && cp <= 0x115F) {
			return true;
		}
		if (cp == 0x2329 || cp == 0x232A) {
			return true;
		}
		if (cp >= 0x2E80 && cp <= 0xA4CF && cp != 0x303F) {
			return true;
		}
		if (cp >= 0xA960 && cp <= 0xA97C) {
			return true;
		}
		if (cp >= 0xAC00 && cp <= 0xD7A3) {
			return true;
		}
		if (cp >= 0xF900 && cp <= 0xFAFF) {
			return true;
		}
		if (cp >= 0xFE10 && cp <= 0xFE19) {
			return true;
		}
		if (cp >= 0xFE30 && cp <= 0xFE6F) {
			return true;
		}
		if (cp >= 0xFF00 && cp <= 0xFF60) {
			return true;
		}
		if (cp >= 0xFFE0 && cp <= 0xFFE6) {
			return true;
		}
		if (cp >= 0x1F000 && cp <= 0x1FAFF) {
			return true;
		}
		if (cp >= 0x20000 && cp <= 0x2FFFD) {
			return true;
		}
		if (cp >= 0x30000 && cp <= 0x3FFFD) {
			return true;
		}
		return false;
	}
}
