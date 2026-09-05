package com.resurrection.blowtorch2.lib.util;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public class ImeCutoutCoverTest {

	@Test
	public void landscapeImeAvoidOffCoversLeftHole() {
		int[] c = ImeCutoutCover.sideCover(true, true, 700, false, 80, 0);
		assertArrayEquals(new int[] { 80, 0, 700 }, c);
	}

	@Test
	public void avoidOnCoversNothing() {
		int[] c = ImeCutoutCover.sideCover(true, true, 700, true, 80, 0);
		assertArrayEquals(new int[] { 0, 0, 0 }, c);
	}

	@Test
	public void imeHiddenCoversNothing() {
		int[] c = ImeCutoutCover.sideCover(true, false, 700, false, 80, 0);
		assertArrayEquals(new int[] { 0, 0, 0 }, c);
	}

	@Test
	public void zeroImeHeightCoversNothing() {
		int[] c = ImeCutoutCover.sideCover(true, true, 0, false, 80, 0);
		assertArrayEquals(new int[] { 0, 0, 0 }, c);
	}

	@Test
	public void portraitCoversNothing() {
		int[] c = ImeCutoutCover.sideCover(false, true, 700, false, 0, 0);
		assertArrayEquals(new int[] { 0, 0, 0 }, c);
	}

	@Test
	public void bothSidesShareHeight() {
		int[] c = ImeCutoutCover.sideCover(true, true, 500, false, 40, 60);
		assertArrayEquals(new int[] { 40, 60, 500 }, c);
	}
}
