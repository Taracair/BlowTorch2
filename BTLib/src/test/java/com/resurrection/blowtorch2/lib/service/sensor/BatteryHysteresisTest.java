package com.resurrection.blowtorch2.lib.service.sensor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * Crossing a battery line is a gesture; sitting on it is not. One threshold
 * would flap as charge oscillates around 20%, so the recover line sits higher.
 */
public class BatteryHysteresisTest {

	private static BatteryHysteresis atDefaults() {
		return new BatteryHysteresis(GestureTuning.DEFAULT_BATTERY_LOW,
				GestureTuning.DEFAULT_BATTERY_RECOVER);
	}

	@Test
	public void firstReadingAlreadyLowIsASeedNotAFire() {
		BatteryHysteresis h = atDefaults();
		assertNull(h.observe(15));
		assertEquals(BatteryHysteresis.LOW, h.getBand());
	}

	@Test
	public void firstReadingAlreadyOkIsASeedNotAFire() {
		BatteryHysteresis h = atDefaults();
		assertNull(h.observe(80));
		assertEquals(BatteryHysteresis.OK, h.getBand());
	}

	@Test
	public void crossingDownThroughLowFiresBatterylowOnce() {
		BatteryHysteresis h = atDefaults();
		assertNull(h.observe(80));
		assertEquals("batterylow", h.observe(20));
		assertEquals(BatteryHysteresis.LOW, h.getBand());
	}

	@Test
	public void sittingBelowLowDoesNotFireAgain() {
		BatteryHysteresis h = atDefaults();
		assertNull(h.observe(80));
		assertEquals("batterylow", h.observe(20));
		assertNull(h.observe(19));
		assertNull(h.observe(18));
		assertEquals(BatteryHysteresis.LOW, h.getBand());
	}

	@Test
	public void crossingUpThroughRecoverFiresBatteryokOnce() {
		BatteryHysteresis h = atDefaults();
		assertNull(h.observe(15));
		assertEquals("batteryok", h.observe(35));
		assertEquals(BatteryHysteresis.OK, h.getBand());
	}

	@Test
	public void stillLowAt34Then36IsBatteryokWhenRecoverIs35() {
		BatteryHysteresis h = atDefaults();
		assertNull(h.observe(15));
		assertNull(h.observe(34));
		assertEquals("batteryok", h.observe(36));
	}

	@Test
	public void oscillatingAroundLowDoesNotFlap() {
		BatteryHysteresis h = atDefaults();
		assertNull(h.observe(80));
		assertEquals("batterylow", h.observe(19));
		assertNull(h.observe(21));
		assertNull(h.observe(19));
		assertNull(h.observe(21));
		assertEquals(BatteryHysteresis.LOW, h.getBand());
	}

	@Test
	public void seedForcesTheBandAndNeverFires() {
		BatteryHysteresis h = atDefaults();
		h.seed(15);
		assertEquals(BatteryHysteresis.LOW, h.getBand());
		h.seed(80);
		assertEquals(BatteryHysteresis.OK, h.getBand());
		assertNull(h.observe(80));
	}
}
