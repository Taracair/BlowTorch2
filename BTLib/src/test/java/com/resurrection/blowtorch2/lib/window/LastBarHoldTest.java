package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LastBarHoldTest {

	@Test
	public void aShortLiftSendsTheChipThatWentDown() {
		LastBarHold hold = new LastBarHold();
		hold.down(1, 10, 10);
		assertFalse(hold.armed());
		assertEquals(1, hold.up());
	}

	@Test
	public void aHoldThenLiftSendsThatChip() {
		LastBarHold hold = new LastBarHold();
		hold.down(0, 10, 10);
		assertTrue(hold.arm());
		assertEquals(0, hold.up());
	}

	@Test
	public void slidingOntoTheNextChipSendsThatOne() {
		LastBarHold hold = new LastBarHold();
		hold.down(0, 10, 10);
		assertTrue(hold.arm());
		hold.move(1, 80, 12, 8);
		assertEquals(1, hold.chip());
		assertEquals(1, hold.up());
	}

	@Test
	public void aGapKeepsTheChipAlreadyShowing() {
		LastBarHold hold = new LastBarHold();
		hold.down(0, 10, 10);
		hold.arm();
		hold.move(-1, 40, 12, 8);
		assertEquals(0, hold.chip());
		assertEquals(0, hold.up());
	}

	@Test
	public void aSlideBeforeTheHoldArmsDoesNotSend() {
		LastBarHold hold = new LastBarHold();
		hold.down(0, 10, 10);
		hold.move(2, 90, 10, 8);
		assertTrue(hold.dragged());
		assertFalse(hold.arm());
		assertEquals(-1, hold.up());
	}

	@Test
	public void aJitterInsideTheSlopStillSends() {
		LastBarHold hold = new LastBarHold();
		hold.down(0, 10, 10);
		hold.move(0, 14, 12, 8);
		assertFalse(hold.dragged());
		assertEquals(0, hold.up());
	}

	@Test
	public void aSecondFingerCancelsTheLift() {
		LastBarHold hold = new LastBarHold();
		hold.down(0, 10, 10);
		hold.arm();
		hold.secondFinger();
		assertFalse(hold.armed());
		assertEquals(-1, hold.chip());
		assertEquals(-1, hold.up());
	}

	@Test
	public void aSecondFingerBeforeTheHoldArmsSendsNothing() {
		LastBarHold hold = new LastBarHold();
		hold.down(0, 10, 10);
		hold.secondFinger();
		assertFalse(hold.arm());
		assertEquals(-1, hold.up());
	}

	@Test
	public void aSecondFingerWithNothingHeldIsIgnored() {
		LastBarHold hold = new LastBarHold();
		hold.secondFinger();
		hold.down(1, 0, 0);
		assertEquals(1, hold.up());
	}

	@Test
	public void aCancelSendsNothing() {
		LastBarHold hold = new LastBarHold();
		hold.down(0, 0, 0);
		hold.arm();
		hold.cancelTouch();
		assertFalse(hold.tracking());
		assertEquals(-1, hold.up());
	}

	@Test
	public void anEmptyPressSendsNothing() {
		LastBarHold hold = new LastBarHold();
		hold.down(-1, 0, 0);
		assertFalse(hold.arm());
		assertEquals(-1, hold.up());
	}

	@Test
	public void chipAtUsesTheChipBounds() {
		int[] left = new int[] { 0, 50 };
		int[] top = new int[] { 10, 10 };
		int[] right = new int[] { 40, 90 };
		int[] bottom = new int[] { 40, 40 };
		assertEquals(0, LastBarHold.chipAt(10, 20, left, top, right, bottom));
		assertEquals(1, LastBarHold.chipAt(60, 20, left, top, right, bottom));
		assertEquals(-1, LastBarHold.chipAt(45, 20, left, top, right, bottom));
		assertEquals(-1, LastBarHold.chipAt(10, 0, left, top, right, bottom));
		assertEquals(-1, LastBarHold.chipAt(10, 20, null, top, right, bottom));
		assertEquals(-1, LastBarHold.chipAt(10, 20, left, new int[] { 10 }, right, bottom));
	}
}
