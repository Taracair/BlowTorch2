package com.resurrection.blowtorch2.lib.service.sensor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The rules that decide whether "put the phone face down" fires. Both the
 * service detector and the Sensors probe read them from here, so a wrong one is
 * either a trigger that fires in a pocket or a probe that lies about it.
 */
public class FacingDetectorTest {

	private static final float UP = 9.8f;
	private static final float DOWN = -9.8f;
	private static final float EDGE = 0f;

	@Test
	public void aSideHasToHoldBeforeItIsBelieved() {
		FacingDetector d = new FacingDetector();
		settle(d, UP, 0L);

		// Turning the phone over passes through face down long before it is
		// there. Firing on the first reading would fire on the way.
		assertFalse(d.accept(DOWN, 1000L));
		assertFalse(d.accept(DOWN, 1000L + FacingDetector.SETTLE_MILLIS - 1));
		assertTrue(d.accept(DOWN, 1000L + FacingDetector.SETTLE_MILLIS));
		assertEquals("facedown", d.getGesture());
		assertEquals(DeviceState.DOWN, d.getFacing());
	}

	@Test
	public void whereThePhoneAlreadyLayIsNotSomethingAnybodyDid() {
		// The sensor is picked up the moment a trigger wants it, and a phone
		// lying face down at that moment would otherwise send its command with
		// nobody having touched it.
		FacingDetector d = new FacingDetector();
		assertFalse(d.accept(DOWN, 0L));
		assertTrue(d.accept(DOWN, FacingDetector.SETTLE_MILLIS));
		assertEquals(DeviceState.DOWN, d.getFacing());
		assertNull(d.getGesture());
	}

	@Test
	public void aPhoneOnEdgeIsNeitherSideAndFiresNothing() {
		FacingDetector d = new FacingDetector();
		settle(d, UP, 0L);
		assertFalse(d.accept(EDGE, 1000L));
		assertTrue(d.accept(EDGE, 1000L + FacingDetector.SETTLE_MILLIS));
		assertEquals(DeviceState.UNKNOWN, d.getFacing());
		assertNull(d.getGesture());
	}

	@Test
	public void aWobbleOnTheWayStartsTheClockAgain() {
		FacingDetector d = new FacingDetector();
		settle(d, UP, 0L);
		d.accept(DOWN, 1000L);
		// Halfway there, the hand slips and it is on edge again.
		d.accept(EDGE, 1200L);
		d.accept(DOWN, 1300L);
		assertFalse(d.accept(DOWN, 1300L + FacingDetector.SETTLE_MILLIS - 1));
		assertTrue(d.accept(DOWN, 1300L + FacingDetector.SETTLE_MILLIS));
		assertEquals("facedown", d.getGesture());
	}

	@Test
	public void lyingStillOnOneSideFiresOnceAndNotAgain() {
		FacingDetector d = new FacingDetector();
		settle(d, UP, 0L);
		settle(d, DOWN, 2000L);
		assertEquals("facedown", d.getGesture());
		assertFalse(d.accept(DOWN, 9000L));
		assertNull(d.getGesture());
	}

	/** Hold a side long enough for it to be believed. */
	private static void settle(final FacingDetector d, final float z, final long at) {
		d.accept(z, at);
		d.accept(z, at + FacingDetector.SETTLE_MILLIS);
	}
}
