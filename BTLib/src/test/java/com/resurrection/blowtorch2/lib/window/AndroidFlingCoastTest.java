package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AndroidFlingCoastTest {

	@Test
	public void velocityIsFingerSpeedNotSensitivityGain() {
		assertEquals(1000, AndroidFlingCoast.yVelocity(1000f, false));
		assertEquals(-1000, AndroidFlingCoast.yVelocity(1000f, true));
		assertEquals(-400, AndroidFlingCoast.yVelocity(-400f, false));
	}

	@Test
	public void belowMinDoesNotFling() {
		assertFalse(AndroidFlingCoast.shouldFling(10f, 50f));
		assertTrue(AndroidFlingCoast.shouldFling(50f, 50f));
		assertTrue(AndroidFlingCoast.shouldFling(800f, 50f));
	}
}
