package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Hysteresis for "is the keyboard up": the old 120dp hard floor toggled twice
 * on a dip during the IME slide. Hide-on-command is a latch, not a second floor.
 */
public class SoftKeyboardCoverageTest {

	@Test
	public void belowShowIsDown() {
		assertFalse(SoftKeyboardCoverage.isCovering(0, 1f, false));
		assertFalse(SoftKeyboardCoverage.isCovering(119, 1f, false));
	}

	@Test
	public void atShowIsUp() {
		assertTrue(SoftKeyboardCoverage.isCovering(120, 1f, false));
		assertTrue(SoftKeyboardCoverage.isCovering(400, 1f, false));
	}

	@Test
	public void aDipBelowShowStaysUpOnceCovering() {
		assertTrue(SoftKeyboardCoverage.isCovering(90, 1f, true));
		assertTrue(SoftKeyboardCoverage.isCovering(80, 1f, true));
	}

	@Test
	public void fallingThroughHideIsDown() {
		assertFalse(SoftKeyboardCoverage.isCovering(79, 1f, true));
		assertFalse(SoftKeyboardCoverage.isCovering(0, 1f, true));
	}

	@Test
	public void densityScalesTheFloors() {
		assertFalse(SoftKeyboardCoverage.isCovering(359, 3f, false));
		assertTrue(SoftKeyboardCoverage.isCovering(360, 3f, false));
		assertTrue(SoftKeyboardCoverage.isCovering(240, 3f, true));
		assertFalse(SoftKeyboardCoverage.isCovering(239, 3f, true));
	}

	@Test
	public void keyboardSlideThatDippedUsedToToggleTwice() {
		// density 1: show 120, hide 80. Sequence measured as "sometimes twice":
		// 0 → 200 (up) → 90 (old floor would drop) → 400 (up again).
		boolean covering = false;
		int[] lifts = { 0, 200, 90, 400 };
		int edges = 0;
		for (int i = 0; i < lifts.length; i++) {
			boolean next = SoftKeyboardCoverage.isCovering(lifts[i], 1f, covering);
			if (next != covering) {
				edges++;
				covering = next;
			}
		}
		assertTrue(covering);
		assertEquals(1, edges);
	}

	@Test
	public void settleConstantMatchesChromeInsetBurst() {
		assertEquals(180, SoftKeyboardCoverage.SETTLE_MS);
	}

	@Test
	public void settleOnlyWhenBecomingCovered() {
		assertEquals(SoftKeyboardCoverage.SETTLE_MS,
				SoftKeyboardCoverage.settleMs(true));
		assertEquals(0, SoftKeyboardCoverage.settleMs(false));
	}

	@Test
	public void hideRequestHoldsWhileLiftStillLooksLikeAKeyboard() {
		SoftKeyboardCoverage.HideRequest hide = new SoftKeyboardCoverage.HideRequest();
		hide.request();
		assertTrue(hide.tick(400, 1f));
		assertTrue(hide.tick(350, 1f));
		assertTrue(hide.isRequested());
	}

	@Test
	public void hideRequestClearsWhenLiftFallsThroughHideFloor() {
		SoftKeyboardCoverage.HideRequest hide = new SoftKeyboardCoverage.HideRequest();
		hide.request();
		assertFalse(hide.tick(0, 1f));
		assertFalse(hide.isRequested());
	}

	@Test
	public void hideRequestClearsWhenKeyboardRisesFromTheTrough() {
		SoftKeyboardCoverage.HideRequest hide = new SoftKeyboardCoverage.HideRequest();
		hide.request();
		assertTrue(hide.tick(400, 1f));
		assertTrue(hide.tick(300, 1f));
		assertTrue(hide.tick(310, 1f));
		assertTrue(hide.tick(323, 1f));
		assertFalse(hide.tick(324, 1f));
		assertFalse(hide.isRequested());
	}

	@Test
	public void hideRequestClearsWhenKeyboardRisesInSmallStepsAtHighDensity() {
		SoftKeyboardCoverage.HideRequest hide = new SoftKeyboardCoverage.HideRequest();
		hide.request();
		assertTrue(hide.tick(900, 3f));
		assertTrue(hide.tick(600, 3f));
		boolean still = true;
		for (int lift = 610; lift <= 680; lift += 10) {
			still = hide.tick(lift, 3f);
		}
		assertFalse(still);
		assertFalse(hide.isRequested());
	}

	@Test
	public void hideRequestDoesNotPlantWhenKeyboardAlreadyDown() {
		SoftKeyboardCoverage.HideRequest hide = new SoftKeyboardCoverage.HideRequest();
		hide.request();
		assertFalse(hide.tick(0, 1f));
		assertFalse(hide.isRequested());
	}

	@Test
	public void hideRequestIgnoresAOnePixelJitterOnTheWayDown() {
		SoftKeyboardCoverage.HideRequest hide = new SoftKeyboardCoverage.HideRequest();
		hide.request();
		assertTrue(hide.tick(300, 1f));
		assertTrue(hide.tick(301, 1f));
		assertTrue(hide.isRequested());
	}

	@Test
	public void visibilityDropWhileLiftIsHighIsTheSwipeDismissCase() {
		assertTrue(SoftKeyboardCoverage.isCovering(400, 1f, true));
		SoftKeyboardCoverage.HideRequest hide = new SoftKeyboardCoverage.HideRequest();
		hide.request();
		assertTrue(hide.tick(400, 1f));
	}
}
