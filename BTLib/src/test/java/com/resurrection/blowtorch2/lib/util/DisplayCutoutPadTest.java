package com.resurrection.blowtorch2.lib.util;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public class DisplayCutoutPadTest {

	@Test
	public void landscapeAvoidUsesCutoutOnTheSideAndKeepsNavBottom() {
		int[] p = DisplayCutoutPad.containerPadding(
				0, 0, 48, 24,
				80, 0, 0,
				true, false, true, true);
		assertArrayEquals(new int[] { 80, 0, 48, 24 }, p);
	}

	@Test
	public void landscapeOffLeavesOnlyNavBars() {
		int[] p = DisplayCutoutPad.containerPadding(
				0, 0, 48, 24,
				80, 0, 0,
				true, false, true, false);
		assertArrayEquals(new int[] { 0, 0, 48, 24 }, p);
	}

	@Test
	public void portraitAvoidPadsOnlyTheHolePastTheStatusBar() {
		int[] p = DisplayCutoutPad.containerPadding(
				0, 90, 0, 24,
				0, 110, 0,
				false, false, true, true);
		assertArrayEquals(new int[] { 0, 20, 0, 24 }, p);
	}

	@Test
	public void portraitHoleInsideStatusBarNeedsNoContainerTop() {
		int[] p = DisplayCutoutPad.containerPadding(
				0, 90, 0, 24,
				0, 90, 0,
				false, false, true, true);
		assertArrayEquals(new int[] { 0, 0, 0, 24 }, p);
	}

	@Test
	public void fullscreenPortraitDoesNotKeepTheStatusBarBand() {
		int[] p = DisplayCutoutPad.containerPadding(
				0, 90, 0, 24,
				0, 40, 0,
				false, true, true, true);
		assertArrayEquals(new int[] { 0, 40, 0, 24 }, p);
	}

	@Test
	public void portraitOffKeepsTopAtZero() {
		int[] p = DisplayCutoutPad.containerPadding(
				0, 90, 0, 24,
				0, 110, 0,
				false, false, false, true);
		assertArrayEquals(new int[] { 0, 0, 0, 24 }, p);
	}
}
