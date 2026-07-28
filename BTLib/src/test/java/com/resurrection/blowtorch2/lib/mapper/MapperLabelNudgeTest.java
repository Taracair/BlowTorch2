package com.resurrection.blowtorch2.lib.mapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Where a link label goes when the spot it wants is taken. Two directions of
 * one link share a midpoint exactly, so without this they draw on top of each
 * other.
 *
 * <p>The rule the earlier version got wrong: a label may be moved, but it must
 * still read as belonging to its own link. Stepping away from the line by two
 * or three label heights satisfies "not overlapping" and fails that, which is
 * how the "w" and "e" between Beehives and Herb Garden ended up floating above
 * the tiles instead of sitting on their arrows.
 */
public class MapperLabelNudgeTest {

	/** A horizontal link: perpendicular is vertical, along is horizontal. */
	private static final float PERP_X = 0f;
	private static final float PERP_Y = 12f;
	private static final float ALONG_X = 10f;
	private static final float ALONG_Y = 0f;

	private static float[] at(int attempt) {
		return MapperView.labelNudge(attempt, PERP_X, PERP_Y, ALONG_X, ALONG_Y);
	}

	@Test
	public void theFirstAttemptDoesNotMove() {
		assertEquals(0f, at(0)[0], 0.001f);
		assertEquals(0f, at(0)[1], 0.001f);
	}

	/** Sliding along the link keeps the label on the line it names. */
	@Test
	public void itSlidesAlongTheLinkBeforeSteppingAside() {
		assertEquals("first move should be along the link", ALONG_X, at(1)[0], 0.001f);
		assertEquals(0f, at(1)[1], 0.001f);
		assertEquals(-ALONG_X, at(2)[0], 0.001f);

		assertEquals("only then step aside", PERP_Y, at(3)[1], 0.001f);
		assertEquals(-PERP_Y, at(4)[1], 0.001f);
	}

	/** Symmetrical, so a fan of labels does not all drift one way. */
	@Test
	public void oppositeAttemptsCancel() {
		for (int pair = 1; pair <= 3; pair++) {
			float[] plus = at(pair * 2 - 1);
			float[] minus = at(pair * 2);
			assertEquals(0f, plus[0] + minus[0], 0.001f);
			assertEquals(0f, plus[1] + minus[1], 0.001f);
		}
	}

	/**
	 * The regression this file exists for: no attempt may put a label further
	 * than one step aside and one step along, however crowded the map is.
	 */
	@Test
	public void noAttemptEverDetachesFromTheLink() {
		for (int attempt = 0; attempt < MapperView.LABEL_ATTEMPTS; attempt++) {
			float[] n = at(attempt);
			assertTrue("attempt " + attempt + " slid too far along: " + n[0],
					Math.abs(n[0]) <= Math.abs(ALONG_X) + Math.abs(PERP_X) + 0.001f);
			assertTrue("attempt " + attempt + " stepped too far aside: " + n[1],
					Math.abs(n[1]) <= Math.abs(PERP_Y) + Math.abs(ALONG_Y) + 0.001f);
		}
	}

	/** Every attempt lands somewhere new, or the search would spin. */
	@Test
	public void noTwoAttemptsShareAPlace() {
		for (int a = 0; a < MapperView.LABEL_ATTEMPTS; a++) {
			for (int b = a + 1; b < MapperView.LABEL_ATTEMPTS; b++) {
				float[] first = at(a);
				float[] second = at(b);
				assertTrue("attempts " + a + " and " + b + " collide",
						Math.abs(first[0] - second[0]) > 0.001f
								|| Math.abs(first[1] - second[1]) > 0.001f);
			}
		}
	}

	/** A vertical link slides its labels up and down, and steps them sideways. */
	@Test
	public void theStepFollowsWhicheverAxisIsGiven() {
		float[] alongVertical = MapperView.labelNudge(1, 12f, 0f, 0f, 9f);
		assertEquals(0f, alongVertical[0], 0.001f);
		assertEquals(9f, alongVertical[1], 0.001f);

		float[] asideVertical = MapperView.labelNudge(3, 12f, 0f, 0f, 9f);
		assertEquals(12f, asideVertical[0], 0.001f);
		assertEquals(0f, asideVertical[1], 0.001f);
	}
}
