package com.resurrection.blowtorch2.lib.service.sensor;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GestureGateTest {

	@Test public void theMasterSwitchSilencesTheWholeList() {
		assertFalse(GestureGate.allow("headphonesout", false, false, true, true));
		assertFalse(GestureGate.allow("shake", false, false, true, true));
		assertFalse(GestureGate.allow("screenoff", false, true, false, false));
	}

	@Test public void systemEventsIgnoreTheScreenAndTheBackground() {
		assertTrue(GestureGate.allow("headphonesout", true, false, false, false));
		assertFalse(GestureGate.allow("shake", true, false, false, true));
		assertFalse(GestureGate.allow("shake", true, false, true, false));
		assertTrue(GestureGate.allow("shake", true, false, true, true));
	}

	@Test public void myShakesQuietensDirectionsAndPatternsOnly() {
		assertFalse(GestureGate.allow("shakeright", true, true, true, true));
		assertFalse(GestureGate.allow("pat:lr", true, true, true, true));
		assertTrue(GestureGate.allow("shake", true, true, true, true));
		assertTrue(GestureGate.allow("u:slash", true, true, true, true));
		assertFalse(GestureGate.allow("u:slash", true, false, true, true));
		assertTrue(GestureGate.allow("shakeright", true, false, true, true));
	}

	@Test public void anUnknownNameIsNotAGesture() {
		assertFalse(GestureGate.allow("wobble", true, true, true, true));
		assertFalse(GestureGate.allow("u:wave", true, true, true, true));
	}
}
