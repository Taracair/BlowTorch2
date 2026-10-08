package com.resurrection.blowtorch2.lib.mapper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TwoFingerTapTest {

	private static final int SLOP = 16;

	@Test
	public void bothFingersUpInsideTheWindowIsATap() {
		TwoFingerTap tap = armed();
		assertFalse(tap.lift(1, 100L));
		assertTrue(tap.lift(0, 100L + TwoFingerTap.WINDOW_MS));
	}

	@Test
	public void oneFingerNeverCounts() {
		TwoFingerTap tap = new TwoFingerTap();
		tap.primaryDown();
		assertFalse(tap.lift(0, 50L));
	}

	@Test
	public void travelPastTheSlopIsAPinch() {
		TwoFingerTap tap = armed();
		tap.move(0f, 0f, SLOP + 1f, 0f, SLOP);
		assertFalse(tap.lift(0, 180L));
	}

	@Test
	public void travelInsideTheSlopStillCounts() {
		TwoFingerTap tap = armed();
		tap.move(10f, 10f, 10f + SLOP, 10f, SLOP);
		assertTrue(tap.lift(0, 180L));
	}

	@Test
	public void aLingerIsNotATap() {
		TwoFingerTap tap = armed();
		assertFalse(tap.lift(0, 100L + TwoFingerTap.WINDOW_MS + 1L));
	}

	@Test
	public void travelBeforeTheSecondFingerIsNotATap() {
		TwoFingerTap tap = new TwoFingerTap();
		tap.primaryDown();
		tap.secondDown(100L, true);
		assertFalse(tap.lift(0, 180L));
	}

	@Test
	public void aThirdFingerCancels() {
		TwoFingerTap tap = armed();
		tap.extraFinger();
		assertFalse(tap.lift(0, 120L));
	}

	@Test
	public void cancelDropsTheGesture() {
		TwoFingerTap tap = armed();
		tap.cancel();
		assertFalse(tap.lift(0, 120L));
	}

	@Test
	public void aLaterGestureIsIndependent() {
		TwoFingerTap tap = armed();
		tap.move(0f, 0f, 40f, 0f, SLOP);
		assertFalse(tap.lift(0, 120L));
		tap.primaryDown();
		tap.secondDown(500L);
		assertTrue(tap.lift(0, 520L));
	}

	private static TwoFingerTap armed() {
		TwoFingerTap tap = new TwoFingerTap();
		tap.primaryDown();
		tap.secondDown(100L);
		return tap;
	}
}
