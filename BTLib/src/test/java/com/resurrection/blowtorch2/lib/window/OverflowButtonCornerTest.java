package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Corner index → gravity / IME / popup / keep-out. Indices are what land in
 * the profile; 0 is bottom-right so existing worlds stay put.
 */
public class OverflowButtonCornerTest {

	@Test
	public void defaultIsBottomRightAndOutOfRangeFallsBack() {
		assertEquals(0, OverflowButtonCorner.DEFAULT);
		assertEquals(OverflowButtonCorner.BOTTOM_RIGHT, OverflowButtonCorner.clamp(-1));
		assertEquals(OverflowButtonCorner.BOTTOM_RIGHT, OverflowButtonCorner.clamp(4));
		assertEquals(OverflowButtonCorner.TOP_LEFT, OverflowButtonCorner.clamp(3));
	}

	@Test
	public void gravityMatchesAndroidStartEndTopBottomBits() {
		assertEquals(OverflowButtonCorner.GRAVITY_BOTTOM | OverflowButtonCorner.GRAVITY_END,
				OverflowButtonCorner.gravity(OverflowButtonCorner.BOTTOM_RIGHT));
		assertEquals(OverflowButtonCorner.GRAVITY_BOTTOM | OverflowButtonCorner.GRAVITY_START,
				OverflowButtonCorner.gravity(OverflowButtonCorner.BOTTOM_LEFT));
		assertEquals(OverflowButtonCorner.GRAVITY_TOP | OverflowButtonCorner.GRAVITY_END,
				OverflowButtonCorner.gravity(OverflowButtonCorner.TOP_RIGHT));
		assertEquals(OverflowButtonCorner.GRAVITY_TOP | OverflowButtonCorner.GRAVITY_START,
				OverflowButtonCorner.gravity(OverflowButtonCorner.TOP_LEFT));
	}

	@Test
	public void bottomCornersLiftWithImeAndKeepOutBelow() {
		assertTrue(OverflowButtonCorner.liftWithIme(OverflowButtonCorner.BOTTOM_RIGHT));
		assertTrue(OverflowButtonCorner.liftWithIme(OverflowButtonCorner.BOTTOM_LEFT));
		assertTrue(OverflowButtonCorner.keepOutAtBottom(OverflowButtonCorner.BOTTOM_RIGHT));
		assertFalse(OverflowButtonCorner.keepOutAtTop(OverflowButtonCorner.BOTTOM_LEFT));
		assertFalse(OverflowButtonCorner.popupOpensDown(OverflowButtonCorner.BOTTOM_RIGHT));
	}

	@Test
	public void topCornersDoNotLiftWithImeAndKeepOutAbove() {
		assertFalse(OverflowButtonCorner.liftWithIme(OverflowButtonCorner.TOP_RIGHT));
		assertFalse(OverflowButtonCorner.liftWithIme(OverflowButtonCorner.TOP_LEFT));
		assertTrue(OverflowButtonCorner.keepOutAtTop(OverflowButtonCorner.TOP_LEFT));
		assertFalse(OverflowButtonCorner.keepOutAtBottom(OverflowButtonCorner.TOP_RIGHT));
		assertTrue(OverflowButtonCorner.popupOpensDown(OverflowButtonCorner.TOP_RIGHT));
	}

	@Test
	public void startIsLeftCorners() {
		assertTrue(OverflowButtonCorner.isStart(OverflowButtonCorner.BOTTOM_LEFT));
		assertTrue(OverflowButtonCorner.isStart(OverflowButtonCorner.TOP_LEFT));
		assertFalse(OverflowButtonCorner.isStart(OverflowButtonCorner.BOTTOM_RIGHT));
		assertFalse(OverflowButtonCorner.isStart(OverflowButtonCorner.TOP_RIGHT));
	}

	@Test
	public void bottomKeepOutOnlyWhenStripIsBottomAndOverlapsX() {
		int inputBarTop = 1800;
		int stripTop = 1700;
		assertEquals(stripTop, OverflowButtonCorner.clampBottomKeepOut(
				OverflowButtonCorner.BOTTOM_RIGHT, inputBarTop, true, stripTop));
		assertEquals(inputBarTop, OverflowButtonCorner.clampBottomKeepOut(
				OverflowButtonCorner.BOTTOM_RIGHT, inputBarTop, false, stripTop));
		assertEquals(inputBarTop, OverflowButtonCorner.clampBottomKeepOut(
				OverflowButtonCorner.TOP_RIGHT, inputBarTop, true, 80));
	}

	@Test
	public void topKeepOutOnlyWhenStripIsTopAndOverlapsX() {
		int stripBottom = 140;
		assertEquals(stripBottom, OverflowButtonCorner.clampTopKeepOut(
				OverflowButtonCorner.TOP_LEFT, 0, true, stripBottom));
		assertEquals(0, OverflowButtonCorner.clampTopKeepOut(
				OverflowButtonCorner.TOP_LEFT, 0, false, stripBottom));
		assertEquals(0, OverflowButtonCorner.clampTopKeepOut(
				OverflowButtonCorner.BOTTOM_RIGHT, 0, true, stripBottom));
	}

	@Test
	public void overlapsXIsHalfOpenIntervals() {
		assertTrue(OverflowButtonCorner.overlapsX(100, 200, 150, 250));
		assertFalse(OverflowButtonCorner.overlapsX(100, 150, 150, 250));
		assertFalse(OverflowButtonCorner.overlapsX(250, 300, 150, 250));
	}

	@Test
	public void bottomPopupOpensUpOverlappingTheAnchor() {
		int[] p = OverflowButtonCorner.popupVertical(
				OverflowButtonCorner.BOTTOM_RIGHT, 500, 48, 800, 4, 160);
		assertEquals(496, p[0]);
		assertEquals(-496, p[1]);
		assertEquals(1, p[2]);
	}

	@Test
	public void topPopupOpensDownWithoutOverlappingTheAnchor() {
		int[] p = OverflowButtonCorner.popupVertical(
				OverflowButtonCorner.TOP_LEFT, 80, 48, 800, 4, 160);
		assertEquals(668, p[0]);
		assertEquals(0, p[1]);
		assertEquals(0, p[2]);
	}

	@Test
	public void topPopupSubtractsImeCoverFromAvailableHeight() {
		int[] p = OverflowButtonCorner.popupVertical(
				OverflowButtonCorner.TOP_LEFT, 80, 48, 800, 4, 160, 300);
		assertEquals(368, p[0]);
		assertEquals(0, p[1]);
		assertEquals(0, p[2]);
	}

	@Test
	public void unplacedChipsMoveRightOfBottomLeftOverflow() {
		assertEquals(12, OverflowButtonCorner.unplacedChipLeft(
				OverflowButtonCorner.BOTTOM_RIGHT, 12, 48, 4));
		assertEquals(64, OverflowButtonCorner.unplacedChipLeft(
				OverflowButtonCorner.BOTTOM_LEFT, 12, 48, 4));
		assertEquals(12, OverflowButtonCorner.unplacedChipLeft(
				OverflowButtonCorner.TOP_LEFT, 12, 48, 4));
	}

	@Test
	public void popupHeightIsCappedAtEightyFivePercentOfTheScreen() {
		int[] p = OverflowButtonCorner.popupVertical(
				OverflowButtonCorner.BOTTOM_RIGHT, 5000, 48, 2000, 4, 160);
		assertEquals((int) (2000 * 0.85f), p[0]);
		assertArrayEquals(new int[] { p[0], -p[0], 1 }, p);
	}
}
