package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * ⋮ follows the input bar. Nav padding slides the bar up without changing
 * its height; a size-only check leaves the overlay where it was.
 */
public class ChromeControllerFabFollowTest {

	@Test
	public void sameSizeSlideUpStillRepositions() {
		assertTrue(ChromeController.inputBarShiftRepositionsFab(
				0, 1800, 1080, 1920,
				0, 1700, 1080, 1820));
	}

	@Test
	public void heightChangeRepositions() {
		assertTrue(ChromeController.inputBarShiftRepositionsFab(
				0, 1800, 1080, 1920,
				0, 1800, 1080, 2000));
	}

	@Test
	public void unchangedBoundsDoNot() {
		assertFalse(ChromeController.inputBarShiftRepositionsFab(
				0, 1800, 1080, 1920,
				0, 1800, 1080, 1920));
	}

	@Test
	public void slideLeftStillRepositions() {
		assertTrue(ChromeController.inputBarShiftRepositionsFab(
				0, 1800, 1080, 1920,
				80, 1800, 1160, 1920));
	}
}
