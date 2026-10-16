package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WindowAvoidReflowTest {

	@Test
	public void firstHoleReflowsWhenTheWindowSwitchIsOff() {
		assertTrue(Window.shouldReflowAvoid(false, false, true));
	}

	@Test
	public void aHoleThatWentAwayStillReflows() {
		assertTrue(Window.shouldReflowAvoid(false, true, false));
	}

	@Test
	public void noHolesAndTheSwitchOffDoesNotReflow() {
		assertFalse(Window.shouldReflowAvoid(false, false, false));
	}

	@Test
	public void theWindowSwitchAlwaysReflows() {
		assertTrue(Window.shouldReflowAvoid(true, false, false));
	}
}
