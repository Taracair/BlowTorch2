package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CopyLoupeDragTest {

	@Test
	public void slowTravelIsOneToOneAndKeepsRemainder() {
		float[] rem = new float[1];
		// 5 px / 24 ms stays under SLOW_PX_PER_MS, so gain is 1.
		int n = CopyLoupeDrag.cells(rem, 0, 5f, 12f, 24f);
		assertEquals(0, n);
		assertEquals(5f, rem[0], 0.01f);
		n = CopyLoupeDrag.cells(rem, 0, 8f, 12f, 24f);
		assertEquals(1, n);
		assertEquals(1f, rem[0], 0.01f);
	}

	@Test
	public void slowTravelDoesNotUseGain() {
		assertEquals(1f, CopyLoupeDrag.gain(0.2f), 0.001f);
		assertEquals(1f, CopyLoupeDrag.gain(CopyLoupeDrag.SLOW_PX_PER_MS), 0.001f);
	}

	@Test
	public void ordinaryDragStaysOneToOne() {
		assertEquals(1f, CopyLoupeDrag.gain(1.0f), 0.001f);
		float[] rem = new float[1];
		int n = CopyLoupeDrag.cells(rem, 0, 16f, 12f, 16f);
		assertEquals(1, n);
		assertEquals(4f, rem[0], 0.01f);
	}

	@Test
	public void fastTravelUsesMaxGain() {
		assertEquals(CopyLoupeDrag.MAX_GAIN,
				CopyLoupeDrag.gain(CopyLoupeDrag.FAST_PX_PER_MS), 0.001f);
		assertEquals(CopyLoupeDrag.MAX_GAIN,
				CopyLoupeDrag.gain(CopyLoupeDrag.FAST_PX_PER_MS + 4f), 0.001f);
	}

	@Test
	public void oneMoveConsumesEveryFullCellNotJustOne() {
		float[] rem = new float[1];
		int n = CopyLoupeDrag.cells(rem, 0, 36f, 12f, 48f);
		assertTrue("a long MOVE still walks every full cell, got " + n, n >= 3);
		assertEquals(3, n);
		assertEquals(0f, rem[0], 0.01f);
	}

	@Test
	public void aFlickDoesNotLeapTheWayTheOldGainDid() {
		float[] rem = new float[1];
		int n = CopyLoupeDrag.cells(rem, 0, 36f, 12f, 16f);
		assertTrue(n >= 3);
		assertTrue("flick must stay modest, got " + n, n <= 5);
	}

	@Test
	public void leftoverIsNotDroppedOnASlowStep() {
		float[] rem = new float[1];
		CopyLoupeDrag.cells(rem, 0, 20f, 12f, 16f);
		assertTrue("remainder must survive so the next nudge can finish a cell",
				Math.abs(rem[0]) > 0.01f);
		assertTrue(Math.abs(rem[0]) < 12f);
	}

	@Test
	public void negativeTravelWalksLeft() {
		float[] rem = new float[1];
		int n = CopyLoupeDrag.cells(rem, 0, -5f, 12f, 24f);
		assertEquals(0, n);
		n = CopyLoupeDrag.cells(rem, 0, -8f, 12f, 24f);
		assertEquals(-1, n);
	}

	@Test
	public void capsAWildFlick() {
		float[] rem = new float[1];
		int n = CopyLoupeDrag.cells(rem, 0, 8000f, 12f, 16f);
		assertEquals(CopyLoupeDrag.MAX_CELLS, n);
		assertEquals(0f, rem[0], 0.01f);
	}

	@Test
	public void clusteredEventsAreNotTreatedAsAFlick() {
		assertEquals(1f, CopyLoupeDrag.gain(2f / CopyLoupeDrag.MIN_DT_MS), 0.001f);
	}
}
