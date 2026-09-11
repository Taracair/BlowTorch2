package com.resurrection.blowtorch2.lib.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BellVibrateRouteTest {

	@Test
	public void noCallbacksGoesToServiceWhileWindowShowing() {
		assertTrue(BellVibrateRoute.inService(true, 0));
	}

	@Test
	public void noCallbacksGoesToServiceWhileWindowHidden() {
		assertTrue(BellVibrateRoute.inService(false, 0));
	}

	@Test
	public void registeredCallbackGoesToUiWhileWindowShowing() {
		assertFalse(BellVibrateRoute.inService(true, 1));
	}

	@Test
	public void registeredCallbackGoesToServiceWhileWindowHidden() {
		assertTrue(BellVibrateRoute.inService(false, 1));
	}

	@Test
	public void extraCallbacksFollowTheWindow() {
		assertFalse(BellVibrateRoute.inService(true, 2));
		assertTrue(BellVibrateRoute.inService(false, 2));
	}
}
