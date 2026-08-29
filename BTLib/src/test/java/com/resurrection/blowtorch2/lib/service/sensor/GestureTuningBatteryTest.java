package com.resurrection.blowtorch2.lib.service.sensor;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Invalid battery pairs are clamped, not stored. A recover sitting on low would
 * flap as charge hovers on the line; the five-point gap is that band.
 */
public class GestureTuningBatteryTest {

	@Test
	public void recoverCloserThanFivePointsIsPushedUp() {
		int[] t = GestureTuning.clampBatteryThresholds(20, 22);
		assertEquals(20, t[0]);
		assertEquals(25, t[1]);
	}

	@Test
	public void recoverBelowLowIsPushedToLowPlusFive() {
		int[] t = GestureTuning.clampBatteryThresholds(20, 10);
		assertEquals(20, t[0]);
		assertEquals(25, t[1]);
	}

	@Test
	public void lowIsClampedToOneThroughNinety() {
		int[] floor = GestureTuning.clampBatteryThresholds(0, 10);
		assertEquals(1, floor[0]);
		assertEquals(10, floor[1]);
		int[] tight = GestureTuning.clampBatteryThresholds(0, 3);
		assertEquals(1, tight[0]);
		assertEquals(6, tight[1]);
		int[] high = GestureTuning.clampBatteryThresholds(99, 100);
		assertEquals(90, high[0]);
		assertEquals(100, high[1]);
	}

	@Test
	public void recoverIsClampedToLowPlusFiveThroughOneHundred() {
		int[] t = GestureTuning.clampBatteryThresholds(15, 200);
		assertEquals(15, t[0]);
		assertEquals(100, t[1]);
	}

	@Test
	public void aValidPairIsLeftAlone() {
		int[] t = GestureTuning.clampBatteryThresholds(15, 40);
		assertEquals(15, t[0]);
		assertEquals(40, t[1]);
	}

	@Test
	public void shippedDefaultsSatisfyTheGap() {
		assertEquals(20, GestureTuning.DEFAULT_BATTERY_LOW);
		assertEquals(35, GestureTuning.DEFAULT_BATTERY_RECOVER);
		int[] t = GestureTuning.clampBatteryThresholds(
				GestureTuning.DEFAULT_BATTERY_LOW,
				GestureTuning.DEFAULT_BATTERY_RECOVER);
		assertEquals(20, t[0]);
		assertEquals(35, t[1]);
	}
}
