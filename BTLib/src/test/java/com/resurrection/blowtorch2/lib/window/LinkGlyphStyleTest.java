package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.resurrection.blowtorch2.lib.settings.HyperSettings;

/**
 * Pins the link-glyph choice the main draw loop already made. A row that
 * shares a button's height has to ask the same function.
 */
public class LinkGlyphStyleTest {

	private static final int SPAN = 0xFFFF0000;
	private static final int THEMED = 0xFF66CCFF;
	private static final int RAW = 0xFF66CCFF;

	@Test
	public void colouredSpanKeepsItsLettersInTheDefaultMode() {
		assertEquals(SPAN, LinkGlyphStyle.color(
				HyperSettings.LINK_MODE.HIGHLIGHT_COLOR_ONLY_BLAND, SPAN, THEMED, RAW, 31));
		assertTrue(LinkGlyphStyle.modeUnderline(
				HyperSettings.LINK_MODE.HIGHLIGHT_COLOR_ONLY_BLAND));
	}

	@Test
	public void blandDefaultUsesTheHyperlinkColour() {
		assertEquals(THEMED, LinkGlyphStyle.color(
				HyperSettings.LINK_MODE.HIGHLIGHT_COLOR_ONLY_BLAND, SPAN, THEMED, RAW, 37));
	}

	@Test
	public void highlightColourAlwaysUsesTheHyperlinkColour() {
		assertEquals(THEMED, LinkGlyphStyle.color(
				HyperSettings.LINK_MODE.HIGHLIGHT_COLOR, SPAN, THEMED, RAW, 31));
		assertTrue(LinkGlyphStyle.modeUnderline(HyperSettings.LINK_MODE.HIGHLIGHT_COLOR));
	}

	@Test
	public void highlightKeepsTheSpanColourAndUnderlines() {
		assertEquals(SPAN, LinkGlyphStyle.color(
				HyperSettings.LINK_MODE.HIGHLIGHT, SPAN, THEMED, RAW, 31));
		assertTrue(LinkGlyphStyle.modeUnderline(HyperSettings.LINK_MODE.HIGHLIGHT));
	}

	@Test
	public void noneKeepsTheSpanColourWithoutAnUnderline() {
		assertEquals(SPAN, LinkGlyphStyle.color(
				HyperSettings.LINK_MODE.NONE, SPAN, THEMED, RAW, 31));
		assertFalse(LinkGlyphStyle.modeUnderline(HyperSettings.LINK_MODE.NONE));
	}

	@Test
	public void backgroundModeInvertsTheRawLinkColour() {
		assertEquals(0xFF993300, LinkGlyphStyle.color(
				HyperSettings.LINK_MODE.BACKGROUND, SPAN, THEMED, RAW, 31));
		assertFalse(LinkGlyphStyle.modeUnderline(HyperSettings.LINK_MODE.BACKGROUND));
	}
}
