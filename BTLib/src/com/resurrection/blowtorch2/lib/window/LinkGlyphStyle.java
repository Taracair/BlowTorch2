package com.resurrection.blowtorch2.lib.window;

import com.resurrection.blowtorch2.lib.settings.HyperSettings.LINK_MODE;

/**
 * Letter colour and underline for one link run. The plain row and a row
 * that sits on a button both ask this.
 */
public final class LinkGlyphStyle {

	private LinkGlyphStyle() {
	}

	/**
	 * @param selectedAnsi current foreground register. {@code 37} is the bland
	 *                     default, including when that register is an xterm
	 *                     index of 37.
	 */
	public static int color(final LINK_MODE mode, final int spanFg, final int themedLink,
			final int rawLink, final int selectedAnsi) {
		switch (mode) {
		case NONE:
		case HIGHLIGHT:
			return spanFg;
		case HIGHLIGHT_COLOR:
			return themedLink;
		case HIGHLIGHT_COLOR_ONLY_BLAND:
			return selectedAnsi == 37 ? themedLink : spanFg;
		case BACKGROUND:
			return 0xFF000000 | (rawLink ^ 0xFFFFFFFF);
		default:
			return themedLink;
		}
	}

	/** Underline from the link mode, before the span's own underline is applied. */
	public static boolean modeUnderline(final LINK_MODE mode) {
		switch (mode) {
		case HIGHLIGHT:
		case HIGHLIGHT_COLOR:
		case HIGHLIGHT_COLOR_ONLY_BLAND:
			return true;
		case NONE:
		case BACKGROUND:
		default:
			return false;
		}
	}
}
