package com.resurrection.blowtorch2.lib.launcher;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PinLaunchTest {

	@Test
	public void emptyPinDisplayIsNone() {
		assertEquals(PinLaunch.Action.NONE, PinLaunch.decide("", "world-a", true));
	}

	@Test
	public void nullPinDisplayIsNone() {
		assertEquals(PinLaunch.Action.NONE, PinLaunch.decide(null, "world-a", false));
	}

	@Test
	public void openPinMatchingClutchIsResume() {
		assertEquals(PinLaunch.Action.RESUME,
				PinLaunch.decide("world-a", "world-a", true));
	}

	@Test
	public void openPinDifferentClutchIsSwitchExisting() {
		assertEquals(PinLaunch.Action.SWITCH_EXISTING,
				PinLaunch.decide("world-b", "world-a", true));
	}

	@Test
	public void openPinEmptyClutchIsSwitchExisting() {
		assertEquals(PinLaunch.Action.SWITCH_EXISTING,
				PinLaunch.decide("world-b", "", true));
		assertEquals(PinLaunch.Action.SWITCH_EXISTING,
				PinLaunch.decide("world-b", null, true));
	}

	@Test
	public void closedPinDifferentClutchIsOpenNew() {
		assertEquals(PinLaunch.Action.OPEN_NEW,
				PinLaunch.decide("world-b", "world-a", false));
	}

	@Test
	public void closedPinMatchingStaleClutchIsOpenNew() {
		assertEquals(PinLaunch.Action.OPEN_NEW,
				PinLaunch.decide("world-a", "world-a", false));
	}

	@Test
	public void closedPinEmptyClutchIsOpenNew() {
		assertEquals(PinLaunch.Action.OPEN_NEW,
				PinLaunch.decide("world-b", "", false));
	}
}
