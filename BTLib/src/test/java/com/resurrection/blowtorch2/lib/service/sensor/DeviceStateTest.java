package com.resurrection.blowtorch2.lib.service.sensor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The values a player's conditions will be written against. A wrong one here is
 * a trigger that fires in a pocket or stays silent in a fight.
 */
public class DeviceStateTest {

	@Test
	public void whatThePhoneCannotTellIsAbsentRatherThanNo() {
		// The asymmetry that makes a profile safe to carry between phones: a
		// device with no proximity sensor never sets device.covered, so
		// "covered == no" is false there rather than quietly true.
		DeviceState s = new DeviceState();
		s.setHeadphones(true);
		assertNull(s.get(DeviceState.KEY_COVERED));
		assertEquals(DeviceState.YES, s.get(DeviceState.KEY_HEADPHONES));
	}

	@Test
	public void onlyAChangeReportsAChange() {
		// Every battery broadcast would otherwise push the whole map into every
		// live world, several times a minute, for nothing.
		DeviceState s = new DeviceState();
		assertTrue(s.setCharging(true));
		assertFalse(s.setCharging(true));
		assertTrue(s.setCharging(false));
	}

	@Test
	public void batteryIsAWholePercentOfWhateverScaleTheDeviceUses() {
		DeviceState s = new DeviceState();
		assertTrue(s.setBatteryPercent(50, 100));
		assertEquals("50", s.get(DeviceState.KEY_BATTERY));
		// Some devices report a scale of 1000, and one of 0 while booting.
		assertTrue(s.setBatteryPercent(255, 1000));
		assertEquals("26", s.get(DeviceState.KEY_BATTERY));
		assertFalse(s.setBatteryPercent(50, 0));
		assertEquals("26", s.get(DeviceState.KEY_BATTERY));
	}

	@Test
	public void coveredIsMeasuredAgainstTheSensorsOwnMaximum() {
		// Most proximity sensors report two values, 0 and their maximum. A
		// threshold in centimetres would be a number invented at a desk.
		assertTrue(DeviceState.isCovered(0f, 5f));
		assertFalse(DeviceState.isCovered(5f, 5f));
		assertTrue(DeviceState.isCovered(4.9f, 5f));
		// A sensor claiming no range at all cannot say anything.
		assertFalse(DeviceState.isCovered(0f, 0f));
	}

	@Test
	public void screenAndHeadphonesReadAsWordsAPlayerWouldType() {
		DeviceState s = new DeviceState();
		s.setScreenOn(false);
		s.setHeadphones(false);
		assertEquals("off", s.get(DeviceState.KEY_SCREEN));
		assertEquals("no", s.get(DeviceState.KEY_HEADPHONES));
		String report = s.report();
		assertTrue(report.contains("= off"));
		assertTrue(report.contains("= no"));
	}

	@Test
	public void anEmptyStateSaysHowToTurnItOn() {
		assertTrue(new DeviceState().report().contains("Nothing is being watched"));
	}

	@Test
	public void theReportListsEveryNameEvenTheOnesNotSet() {
		// A bare list of current values is the most confusing thing here: a name
		// that is missing means "this phone cannot tell", and a condition on it
		// is false. So the report is the catalogue, with what each can hold.
		DeviceState s = new DeviceState();
		s.setScreenOn(true);
		String report = s.report();
		assertTrue(report.contains("device.facing"));
		assertTrue(report.contains("up | down | unknown"));
		assertTrue(report.contains("device.battery"));
		assertTrue(report.contains("0 to 100"));
		assertTrue(report.contains("not set"));
		assertTrue(report.contains("device.screen"));
	}
}
