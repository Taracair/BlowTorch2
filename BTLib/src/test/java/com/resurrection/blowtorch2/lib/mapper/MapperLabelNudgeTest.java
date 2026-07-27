package com.resurrection.blowtorch2.lib.mapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Where a link label goes when the spot it wants is taken. Two directions of
 * one link share a midpoint exactly, so without this they draw on top of each
 * other.
 */
public class MapperLabelNudgeTest {

	private static final float STEP_X = 10f;
	private static final float STEP_Y = 0f;

	@Test
	public void theFirstAttemptDoesNotMove() {
		float[] n = MapperView.labelNudge(0, STEP_X, STEP_Y);
		assertEquals(0f, n[0], 0.001f);
		assertEquals(0f, n[1], 0.001f);
	}

	@Test
	public void itAlternatesSidesBeforeWidening() {
		assertEquals(10f, MapperView.labelNudge(1, STEP_X, STEP_Y)[0], 0.001f);
		assertEquals(-10f, MapperView.labelNudge(2, STEP_X, STEP_Y)[0], 0.001f);
		assertEquals(20f, MapperView.labelNudge(3, STEP_X, STEP_Y)[0], 0.001f);
		assertEquals(-20f, MapperView.labelNudge(4, STEP_X, STEP_Y)[0], 0.001f);
	}

	/** Symmetrical about the link, so labels do not all drift one way. */
	@Test
	public void oppositeAttemptsCancel() {
		for (int ring = 1; ring <= 3; ring++) {
			float plus = MapperView.labelNudge(ring * 2 - 1, STEP_X, STEP_Y)[0];
			float minus = MapperView.labelNudge(ring * 2, STEP_X, STEP_Y)[0];
			assertEquals(0f, plus + minus, 0.001f);
		}
	}

	/** Every attempt lands somewhere new, or the search would spin. */
	@Test
	public void noTwoAttemptsShareAPlace() {
		for (int a = 0; a < 7; a++) {
			for (int b = a + 1; b < 7; b++) {
				float[] first = MapperView.labelNudge(a, STEP_X, STEP_Y);
				float[] second = MapperView.labelNudge(b, STEP_X, STEP_Y);
				assertTrue("attempts " + a + " and " + b + " collide",
						Math.abs(first[0] - second[0]) > 0.001f
								|| Math.abs(first[1] - second[1]) > 0.001f);
			}
		}
	}

	/** A vertical link steps its labels sideways; a horizontal one steps them up. */
	@Test
	public void theStepFollowsWhicheverAxisIsGiven() {
		float[] sideways = MapperView.labelNudge(1, 12f, 0f);
		assertEquals(12f, sideways[0], 0.001f);
		assertEquals(0f, sideways[1], 0.001f);

		float[] upDown = MapperView.labelNudge(1, 0f, 9f);
		assertEquals(0f, upDown[0], 0.001f);
		assertEquals(9f, upDown[1], 0.001f);
	}
}
