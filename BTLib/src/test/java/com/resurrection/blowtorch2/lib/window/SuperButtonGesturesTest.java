package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.resurrection.blowtorch2.lib.window.SuperButtonGestures.BoundSwipes;

/**
 * Port of {@code BT_Free/src/test/lua/swipe_directions_test.lua} so the Java
 * classifiers stay pinned to the same answers as the grid's Lua suite.
 *
 * <p>Block 3 compares 5 button shapes × 81 × 81 dx/dy samples = 32805, matching
 * the Lua count ({@code dx,dy = -200..200 step 5}).
 */
public class SuperButtonGesturesTest {

	private static final float THRESHOLD = SuperButtonGestures.SWIPE_THRESHOLD_DP;

	/** What the handler did before diagonals: four-way only, if bound. */
	private static String resolveOld(BoundSwipes bound, float dx, float dy, float threshold) {
		String dir = SuperButtonGestures.classifySwipe4(dx, dy, threshold);
		if (dir == null) {
			return null;
		}
		return bound.isBound(dir) ? dir : null;
	}

	/** Preview and release both call this. */
	private static String resolveNew(BoundSwipes bound, float dx, float dy, float threshold) {
		return SuperButtonGestures.resolveSwipeDirection(bound, dx, dy, threshold);
	}

	@Test
	public void eightWaySectorsPureDirections() {
		Object[][] cases = {
			{ 100f, 0f, SuperButtonGestures.DIR_RIGHT },
			{ 100f, -100f, SuperButtonGestures.DIR_UP_RIGHT },
			{ 0f, -100f, SuperButtonGestures.DIR_UP },
			{ -100f, -100f, SuperButtonGestures.DIR_UP_LEFT },
			{ -100f, 0f, SuperButtonGestures.DIR_LEFT },
			{ -100f, 100f, SuperButtonGestures.DIR_DOWN_LEFT },
			{ 0f, 100f, SuperButtonGestures.DIR_DOWN },
			{ 100f, 100f, SuperButtonGestures.DIR_DOWN_RIGHT },
		};
		for (Object[] c : cases) {
			float dx = (Float) c[0];
			float dy = (Float) c[1];
			String want = (String) c[2];
			assertEquals("dx=" + dx + " dy=" + dy,
					want, SuperButtonGestures.classifySwipe8(dx, dy, THRESHOLD));
		}
	}

	@Test
	public void bothClassifiersAgreeOnDeadZone() {
		for (int dx = -40; dx <= 40; dx += 2) {
			for (int dy = -40; dy <= 40; dy += 2) {
				boolean fourNil = SuperButtonGestures.classifySwipe4(dx, dy, THRESHOLD) == null;
				boolean eightNil = SuperButtonGestures.classifySwipe8(dx, dy, THRESHOLD) == null;
				assertEquals("dead zone disagreement at dx=" + dx + " dy=" + dy,
						fourNil, eightNil);
			}
		}
	}

	@Test
	public void noRegressionButtonsWithoutDiagonals() {
		BoundSwipes[] configs = {
			upOnly(),
			leftOnly(),
			upAndDown(),
			allFour(),
			noSwipes(),
		};
		int compared = 0;
		for (BoundSwipes data : configs) {
			for (int dx = -200; dx <= 200; dx += 5) {
				for (int dy = -200; dy <= 200; dy += 5) {
					compared++;
					assertEquals("dx=" + dx + " dy=" + dy,
							resolveOld(data, dx, dy, THRESHOLD),
							resolveNew(data, dx, dy, THRESHOLD));
				}
			}
		}
		assertEquals("Lua suite compared 32805 dx/dy combinations", 32805, compared);
	}

	@Test
	public void diagonalsFireWhenConfigured() {
		BoundSwipes diagonal = new BoundSwipes();
		diagonal.up = true;
		diagonal.upRight = true;
		diagonal.downLeft = true;
		assertEquals(SuperButtonGestures.DIR_UP_RIGHT,
				resolveNew(diagonal, 100, -100, THRESHOLD));
		assertEquals(SuperButtonGestures.DIR_DOWN_LEFT,
				resolveNew(diagonal, -100, 100, THRESHOLD));
		assertEquals(SuperButtonGestures.DIR_UP,
				resolveNew(diagonal, 0, -100, THRESHOLD));
	}

	@Test
	public void unboundDiagonalFallsBackToStraight() {
		BoundSwipes upOnly = upOnly();
		assertEquals(SuperButtonGestures.DIR_UP,
				resolveNew(upOnly, 58, -100, THRESHOLD));

		BoundSwipes withDiagonal = upOnly();
		withDiagonal.upRight = true;
		assertEquals(SuperButtonGestures.DIR_UP_RIGHT,
				resolveNew(withDiagonal, 58, -100, THRESHOLD));
	}

	@Test
	public void previewMatchesReleaseAcrossSweep() {
		BoundSwipes all = allFour();
		all.upLeft = true;
		all.upRight = true;
		all.downLeft = true;
		all.downRight = true;
		for (int dx = -100; dx <= 100; dx += 10) {
			for (int dy = -100; dy <= 100; dy += 10) {
				String preview = SuperButtonGestures.resolveSwipeDirection(all, dx, dy, THRESHOLD);
				String release = SuperButtonGestures.resolveSwipeDirection(all, dx, dy, THRESHOLD);
				assertEquals("preview≡release at dx=" + dx + " dy=" + dy, preview, release);
			}
		}
	}

	@Test
	public void constantsMatchLua() {
		assertEquals(24, SuperButtonGestures.SWIPE_THRESHOLD_DP);
		assertEquals(450, SuperButtonGestures.HOLD_DELAY_MS);
		assertEquals(2000, SuperButtonGestures.MOVE_HOLD_MS);
	}

	@Test
	public void holdDeferredUntilReleaseBeforeMoveArm() {
		assertFalse(SuperButtonGestures.shouldEnterMoveMode(HOLD_DELAY_MS()));
		assertFalse(SuperButtonGestures.shouldEnterMoveMode(1999));
		assertTrue(SuperButtonGestures.shouldEnterMoveMode(2000));

		assertFalse(SuperButtonGestures.shouldFireHoldOnRelease(449, false, false));
		assertTrue(SuperButtonGestures.shouldFireHoldOnRelease(450, false, false));
		assertTrue(SuperButtonGestures.shouldFireHoldOnRelease(1999, false, false));
		assertFalse(SuperButtonGestures.shouldFireHoldOnRelease(2000, false, false));
		assertFalse(SuperButtonGestures.shouldFireHoldOnRelease(1500, true, false));
		assertFalse(SuperButtonGestures.shouldFireHoldOnRelease(1500, false, true));
	}

	@Test
	public void holdCancelMoveMatchesLuaThreshold() {
		assertEquals(10, SuperButtonGestures.HOLD_CANCEL_MOVE_DP);
		assertFalse(SuperButtonGestures.shouldCancelHoldForMove(10f, 1f));
		assertTrue(SuperButtonGestures.shouldCancelHoldForMove(10.1f, 1f));
		assertTrue(SuperButtonGestures.shouldCancelHoldForMove(31f, 3f));
		assertFalse(SuperButtonGestures.shouldCancelHoldForMove(30f, 3f));
	}

	@Test
	public void deadZoneIsTapRegion() {
		assertNull(SuperButtonGestures.classifySwipe4(0, 0, THRESHOLD));
		assertNull(SuperButtonGestures.classifySwipe8(0, 0, THRESHOLD));
		assertNull(SuperButtonGestures.resolveSwipeDirection(allFour(), 10, 10, THRESHOLD));
		assertNull(SuperButtonGestures.resolveSwipeDirection(allFour(), 23, 23, THRESHOLD));
		assertEquals(SuperButtonGestures.DIR_RIGHT,
				SuperButtonGestures.resolveSwipeDirection(allFour(), 24, 0, THRESHOLD));
	}

	private static int HOLD_DELAY_MS() {
		return SuperButtonGestures.HOLD_DELAY_MS;
	}

	private static BoundSwipes upOnly() {
		BoundSwipes b = new BoundSwipes();
		b.up = true;
		return b;
	}

	private static BoundSwipes leftOnly() {
		BoundSwipes b = new BoundSwipes();
		b.left = true;
		return b;
	}

	private static BoundSwipes upAndDown() {
		BoundSwipes b = new BoundSwipes();
		b.up = true;
		b.down = true;
		return b;
	}

	private static BoundSwipes allFour() {
		BoundSwipes b = new BoundSwipes();
		b.up = true;
		b.down = true;
		b.left = true;
		b.right = true;
		return b;
	}

	private static BoundSwipes noSwipes() {
		return new BoundSwipes();
	}
}
