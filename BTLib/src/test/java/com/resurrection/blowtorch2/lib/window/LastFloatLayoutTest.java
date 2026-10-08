package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LastFloatLayoutTest {

	@Test
	public void hiddenSuggestionsDoNotMoveTheChips() {
		assertEquals(108, LastFloatLayout.unplacedBottom(false, 400, 36, 36, 100, 8));
		assertEquals(12, LastFloatLayout.unplacedLeft(false, 200, 12));
	}

	@Test
	public void visibleSuggestionsStackAbove() {
		assertEquals(444, LastFloatLayout.unplacedBottom(true, 400, 36, 36, 100, 8));
		assertEquals(200, LastFloatLayout.unplacedLeft(true, 200, 12));
	}

	@Test
	public void unmeasuredSuggestionUsesTheFallbackHeight() {
		assertEquals(444, LastFloatLayout.unplacedBottom(true, 400, 0, 36, 100, 8));
	}

	@Test
	public void clampKeepsTheStripOnScreen() {
		assertEquals(0, LastFloatLayout.clamp(-4, 100));
		assertEquals(100, LastFloatLayout.clamp(140, 100));
		assertEquals(40, LastFloatLayout.clamp(40, 100));
		assertEquals(0, LastFloatLayout.clamp(4, -1));
	}

	@Test
	public void nearDefaultIsASnapBack() {
		assertTrue(LastFloatLayout.near(10, 110, 12, 108, 8));
		assertFalse(LastFloatLayout.near(40, 110, 12, 108, 8));
	}

	@Test
	public void liftFoldsIntoTheMargin() {
		assertEquals(180, LastFloatLayout.withLift(100, 80));
	}

	@Test
	public void snapUsesTheBarLiftOnlyWhileTheStripFollowsTheBar() {
		assertEquals(180, LastFloatLayout.snapBottom(100, true, 80));
		assertEquals(100, LastFloatLayout.snapBottom(100, false, 80));
	}
}
