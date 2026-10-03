package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CopyLoupeLayoutTest {

	@Test
	public void buttonsSitOutsideTheDisc() {
		float[] out = new float[6];
		CopyLoupeLayout.placeButtons(200f, 400f, 50f, 20f, 8f, 400f, 800f, out);
		assertOutside(200f, 400f, 50f, 20f, out[0], out[1]);
		assertOutside(200f, 400f, 50f, 20f, out[2], out[3]);
		assertOutside(200f, 400f, 50f, 20f, out[4], out[5]);
	}

	@Test
	public void roomOnAllSidesPutsCopyRightSwapLeftCloseBelow() {
		float[] out = new float[6];
		CopyLoupeLayout.placeButtons(200f, 400f, 50f, 20f, 8f, 400f, 800f, out);
		assertTrue("copy should sit to the right", out[0] > 200f);
		assertEquals(400f, out[1], 0.01f);
		assertTrue("swap should sit to the left", out[2] < 200f);
		assertEquals(400f, out[3], 0.01f);
		assertEquals(200f, out[4], 0.01f);
		assertTrue("close should sit below", out[5] > 400f);
	}

	@Test
	public void copyFlipsOffTheRightEdge() {
		float[] out = new float[6];
		CopyLoupeLayout.placeButtons(380f, 400f, 50f, 20f, 8f, 400f, 800f, out);
		assertTrue("copy would clip on the right, so it must not stay there",
				out[0] < 380f);
	}

	@Test
	public void closeFlipsOffTheBottomEdge() {
		float[] out = new float[6];
		// Left/right still fit at this Y; only the below slot clips.
		CopyLoupeLayout.placeButtons(200f, 701f, 50f, 20f, 8f, 400f, 800f, out);
		assertTrue("close would clip below, so it must not stay there",
				out[5] < 701f);
	}

	@Test
	public void hitPrefersAButtonOverTheDisc() {
		float[] out = new float[6];
		CopyLoupeLayout.placeButtons(200f, 400f, 50f, 20f, 8f, 400f, 800f, out);
		assertEquals(CopyLoupeLayout.HIT_COPY,
				CopyLoupeLayout.hit(out[0], out[1], 200f, 400f, 50f, out, 20f));
		assertEquals(CopyLoupeLayout.HIT_SWAP,
				CopyLoupeLayout.hit(out[2], out[3], 200f, 400f, 50f, out, 20f));
		assertEquals(CopyLoupeLayout.HIT_EXIT,
				CopyLoupeLayout.hit(out[4], out[5], 200f, 400f, 50f, out, 20f));
		assertEquals(CopyLoupeLayout.HIT_DISC,
				CopyLoupeLayout.hit(200f, 400f, 200f, 400f, 50f, out, 20f));
		assertEquals(CopyLoupeLayout.HIT_NONE,
				CopyLoupeLayout.hit(10f, 10f, 200f, 400f, 50f, out, 20f));
	}

	@Test
	public void buttonsStayOnScreenInACorner() {
		float discR = 50f;
		float btnR = 20f;
		float viewW = 400f;
		float viewH = 800f;
		float[] out = new float[6];
		CopyLoupeLayout.placeButtons(viewW - discR, viewH - discR, discR, btnR,
				8f, viewW, viewH, out);
		assertOnScreen(out[0], out[1], btnR, viewW, viewH);
		assertOnScreen(out[2], out[3], btnR, viewW, viewH);
		assertOnScreen(out[4], out[5], btnR, viewW, viewH);
	}

	@Test
	public void buttonRadiusScalesWithDensity() {
		assertEquals(CopyLoupeLayout.BUTTON_RADIUS_DP * 2f,
				CopyLoupeLayout.buttonRadiusPx(2f), 0.01f);
		assertEquals(CopyLoupeLayout.BUTTON_GAP_DP,
				CopyLoupeLayout.gapPx(1f), 0.01f);
	}

	@Test
	public void defaultGapTucksButtonsAgainstTheDisc() {
		assertTrue(CopyLoupeLayout.BUTTON_GAP_DP < 0f);
		assertTrue(CopyLoupeLayout.BUTTON_RADIUS_DP
				+ CopyLoupeLayout.BUTTON_GAP_DP < 32f + 2f);
	}

	@Test
	public void drawnIconIsLargerThanTheHitRadius() {
		assertTrue(CopyLoupeLayout.iconSidePx(34f) > 34f);
		assertEquals(34f * CopyLoupeLayout.ICON_DRAW_SCALE,
				CopyLoupeLayout.iconSidePx(34f), 0.01f);
	}

	@Test
	public void discAgainstTheRightEdgeWhenFlush() {
		assertTrue(CopyLoupeLayout.discAgainstRight(350f, 50f, 400f));
		assertFalse(CopyLoupeLayout.discAgainstRight(200f, 50f, 400f));
	}

	@Test
	public void discAgainstTheLeftEdgeWhenFlush() {
		assertTrue(CopyLoupeLayout.discAgainstLeft(50f, 50f));
		assertFalse(CopyLoupeLayout.discAgainstLeft(200f, 50f));
	}

	private static void assertOnScreen(final float x, final float y,
			final float btnR, final float viewW, final float viewH) {
		assertTrue("x=" + x, x - btnR >= 0f && x + btnR <= viewW);
		assertTrue("y=" + y, y - btnR >= 0f && y + btnR <= viewH);
	}

	private static void assertOutside(final float cx, final float cy,
			final float discR, final float btnR, final float x, final float y) {
		float dx = x - cx;
		float dy = y - cy;
		float dist = (float) Math.sqrt(dx * dx + dy * dy);
		assertTrue("button overlaps the disc: dist=" + dist,
				dist + 0.01f >= discR + btnR);
	}
}
