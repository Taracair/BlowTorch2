package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.resurrection.blowtorch2.lib.service.Colorizer;

/**
 * Light-paper remap: dark theme must not change a pixel; light theme must
 * make default ink, whites and pale yellow readable on the warm paper.
 */
public class LightPaperTest {

	private static final int ANSI_YELLOW = 0xFFBBBB00;
	private static final int ANSI_RED = 0xFFBB0000;
	private static final int XTERM_16 = 0xFF000000;
	private static final int XTERM_231 = 0xFFEEEEEE;
	private static final int GREY_232 = 0xFF080808;
	private static final int GREY_255 = 0xFFEEEEEE;
	private static final int TRUE_WHITE = 0xFFFFFFFF;
	private static final int TRUE_BLACK = 0xFF000000;

	@Test
	public void darkThemeIsIdentityForDefaultInk() {
		assertEquals(LightPaper.DARK_INK,
				LightPaper.remapForeground(LightPaper.DARK_INK, false, true));
		assertEquals(0xFF000000,
				LightPaper.remapBackground(0x000000, false, true));
	}

	@Test
	public void darkThemeSkipMatchesTodaysUseBackground() {
		assertTrue(LightPaper.skipCellBackground(0xFF000000, false));
		assertTrue(LightPaper.skipCellBackground(LightPaper.DARK_PAPER, false));
		assertFalse(LightPaper.skipCellBackground(0xFFBBBB00, false));
	}

	@Test
	public void lightDefaultInkIsDarkAndReadable() {
		int ink = LightPaper.remapForeground(LightPaper.DARK_INK, true, true);
		assertEquals(LightPaper.LIGHT_INK, ink);
		assertTrue("default ink vs paper",
				LightPaper.contrastRatio(ink, LightPaper.LIGHT_PAPER)
						>= LightPaper.MIN_GREY_CONTRAST);
	}

	@Test
	public void lightDefaultBackgroundIsPaperAndSkipped() {
		int bg = LightPaper.remapBackground(0xFF000000, true, true);
		assertEquals(LightPaper.LIGHT_PAPER, bg);
		assertTrue(LightPaper.skipCellBackground(bg, true));
	}

	@Test
	public void xtermZeroPaintsABlackCellOnLightPaper() {
		int bg = LightPaper.remapBackground(XTERM_16, true, false);
		assertEquals(XTERM_16, bg);
		assertFalse("black cell must not skip on light paper",
				LightPaper.skipCellBackground(bg, true));
	}

	@Test
	public void ansiYellowForegroundDarkensOnLightPaper() {
		int out = LightPaper.remapForeground(ANSI_YELLOW, true, false);
		assertTrue("yellow vs paper after remap",
				LightPaper.contrastRatio(out, LightPaper.LIGHT_PAPER)
						>= LightPaper.MIN_CHROMA_CONTRAST);
		assertTrue("still yellowish, not grey",
				LightPaper.saturation(out) > 0.3);
	}

	@Test
	public void saturatedRedStaysRedOnLightPaper() {
		int out = LightPaper.remapForeground(ANSI_RED, true, false);
		assertEquals(ANSI_RED, out);
	}

	@Test
	public void xterm231WhiteInkDarkens() {
		int out = LightPaper.remapForeground(XTERM_231, true, false);
		assertTrue(LightPaper.contrastRatio(out, LightPaper.LIGHT_PAPER)
				>= LightPaper.MIN_GREY_CONTRAST);
		assertTrue(LightPaper.luminance(out) < LightPaper.luminance(XTERM_231));
	}

	@Test
	public void grey255BackgroundDoesNotVanish() {
		int wash = LightPaper.remapBackground(GREY_255, true, false);
		assertTrue(Math.abs(LightPaper.luminance(wash)
				- LightPaper.luminance(LightPaper.LIGHT_PAPER)) >= 0.12);
		assertFalse(LightPaper.skipCellBackground(wash, true));
	}

	@Test
	public void grey232StaysADarkWash() {
		int wash = LightPaper.remapBackground(GREY_232, true, false);
		assertEquals(GREY_232, wash);
		assertFalse(LightPaper.skipCellBackground(wash, true));
	}

	@Test
	public void truecolorWhiteAndBlack() {
		int white = LightPaper.remapForeground(TRUE_WHITE, true, false);
		assertTrue(LightPaper.contrastRatio(white, LightPaper.LIGHT_PAPER)
				>= LightPaper.MIN_GREY_CONTRAST);
		int blackFg = LightPaper.remapForeground(TRUE_BLACK, true, false);
		assertEquals(TRUE_BLACK, blackFg);
		int blackBg = LightPaper.remapBackground(TRUE_BLACK, true, false);
		assertEquals(TRUE_BLACK, blackBg);
		assertFalse(LightPaper.skipCellBackground(blackBg, true));
	}

	@Test
	public void colorizerAnsi37MatchesDarkInk() {
		assertEquals(LightPaper.DARK_INK,
				0xFF000000 | Colorizer.getColorValue(0, 37, false));
	}

	@Test
	public void defaultRegisterHelpers() {
		assertTrue(LightPaper.isDefaultAnsiForeground(37, false, false));
		assertTrue(LightPaper.isDefaultAnsiForeground(39, false, false));
		assertFalse(LightPaper.isDefaultAnsiForeground(37, true, false));
		assertFalse(LightPaper.isDefaultAnsiForeground(31, false, false));
		assertTrue(LightPaper.isDefaultAnsiBackground(40, false, false));
		assertTrue(LightPaper.isDefaultAnsiBackground(49, false, false));
		assertTrue(LightPaper.isDefaultAnsiBackground(60, false, false));
		assertTrue(LightPaper.isDefaultAnsiBackground(null, false, false));
		assertFalse(LightPaper.isDefaultAnsiBackground(0, true, false));
		assertFalse(LightPaper.isDefaultAnsiBackground(40, false, true));
	}

	@Test
	public void dimTowardPaperOnLightMovesCloserToPaper() {
		int dim = LightPaper.dimTowardPaper(LightPaper.LIGHT_INK, 50, true);
		double ink = LightPaper.luminance(LightPaper.LIGHT_INK);
		double paper = LightPaper.luminance(LightPaper.LIGHT_PAPER);
		double got = LightPaper.luminance(dim);
		assertTrue("dimmed ink sits between ink and paper",
				got > ink && got < paper);
	}

	@Test
	public void dimTowardPaperOnDarkMatchesRepeatedLineDimmer() {
		int a = LightPaper.dimTowardPaper(LightPaper.DARK_INK, 50, false);
		int b = RepeatedLineDimmer.dimForeground(LightPaper.DARK_INK, 50);
		assertEquals(b, a);
	}

	@Test
	public void shadeTowardPaperOnDarkMatchesExistingMultiply() {
		int fg = 0xFFBBBBBB;
		int expectedR = (0xBB * 64) / 255;
		int expected = 0xFF000000 | (expectedR << 16) | (expectedR << 8) | expectedR;
		assertEquals(expected, LightPaper.shadeTowardPaper(fg, 64, false));
	}

	@Test
	public void shadeTowardPaperOnLightIsMixNotMultiplyToBlack() {
		int lightShade = LightPaper.shadeTowardPaper(LightPaper.LIGHT_INK, 64, true);
		assertTrue(LightPaper.luminance(lightShade)
				> LightPaper.luminance(LightPaper.LIGHT_INK));
		assertTrue(LightPaper.luminance(lightShade)
				< LightPaper.luminance(LightPaper.LIGHT_PAPER));
	}

	@Test
	public void paperGetter() {
		assertEquals(LightPaper.DARK_PAPER, LightPaper.paper(false));
		assertEquals(LightPaper.LIGHT_PAPER, LightPaper.paper(true));
		assertEquals(LightPaper.LIGHT_PAPER, LightPaper.paper(true, LightPaper.SHADE_DEFAULT));
		assertEquals(LightPaper.DARK_PAPER, LightPaper.paper(false, 5));
	}

	@Test
	public void shadeTwoInkMatchesOriginalConstant() {
		assertEquals(LightPaper.LIGHT_INK,
				LightPaper.inkFor(LightPaper.paper(true, 2)));
	}

	@Test
	public void eachShadeMeetsGreyContrast() {
		for (int s = LightPaper.SHADE_MIN; s <= LightPaper.SHADE_MAX; s++) {
			int paper = LightPaper.paper(true, s);
			int ink = LightPaper.inkFor(paper);
			assertTrue("shade " + s + " ink vs paper",
					LightPaper.contrastRatio(ink, paper) >= LightPaper.MIN_GREY_CONTRAST);
			int defFg = LightPaper.remapForeground(LightPaper.DARK_INK, true, true, s);
			assertEquals(ink, defFg);
			assertTrue(LightPaper.skipCellBackground(
					LightPaper.remapBackground(0, true, true, s), true, s));
		}
		assertTrue("later shades are lighter paper",
				LightPaper.luminance(LightPaper.paper(true, 5))
						> LightPaper.luminance(LightPaper.paper(true, 1)));
		assertTrue("near-white ink is not lighter than shade-2 ink",
				LightPaper.luminance(LightPaper.inkFor(LightPaper.paper(true, 5)))
						<= LightPaper.luminance(LightPaper.LIGHT_INK));
	}

	@Test
	public void clampShade() {
		assertEquals(1, LightPaper.clampShade(0));
		assertEquals(5, LightPaper.clampShade(9));
		assertEquals(3, LightPaper.clampShade(3));
	}
}
