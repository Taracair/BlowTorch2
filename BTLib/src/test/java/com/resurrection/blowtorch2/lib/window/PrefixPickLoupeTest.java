package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PrefixPickLoupeTest {

	@Test
	public void sizeDefaultIsLargerThanTheOriginalHundred() {
		assertEquals(118, PrefixPickLoupe.DEFAULT_SIZE);
		assertTrue(PrefixPickLoupe.DEFAULT_SIZE >= 115);
		assertTrue(PrefixPickLoupe.DEFAULT_SIZE <= 120);
	}

	@Test
	public void sizeAndZoomClamp() {
		assertEquals(PrefixPickLoupe.MIN_SIZE, PrefixPickLoupe.clampSize(0));
		assertEquals(PrefixPickLoupe.MAX_SIZE, PrefixPickLoupe.clampSize(999));
		assertEquals(PrefixPickLoupe.MIN_ZOOM, PrefixPickLoupe.clampZoom(10));
		assertEquals(PrefixPickLoupe.MAX_ZOOM, PrefixPickLoupe.clampZoom(999));
		assertEquals(200, PrefixPickLoupe.clampZoom(200));
	}

	@Test
	public void scaleTwoHundredIsDouble() {
		assertEquals(2f, PrefixPickLoupe.scale(200), 0.001f);
	}

	@Test
	public void cellLeftMatchesTheWindowGrid() {
		assertEquals(25f, PrefixPickLoupe.cellLeft(3, 10f, 5f), 0.001f);
		assertEquals(-8f, PrefixPickLoupe.cellLeft(0, 12f, 8f), 0.001f);
	}

	@Test
	public void radiusGrowsWithSizePercent() {
		float at100 = PrefixPickLoupe.radiusPx(1f, 100);
		float at118 = PrefixPickLoupe.radiusPx(1f, 118);
		assertEquals(PrefixPickLoupe.BASE_RADIUS_DP, at100, 0.01f);
		assertEquals(at100 * 1.18f, at118, 0.05f);
	}

	@Test
	public void captionPrefersAboveTheCircle() {
		float[] out = new float[4];
		PrefixPickLoupe.captionBox(200, 400, 80, 100, 30, 8, 400, 800, out);
		assertTrue("caption bottom should sit above the circle",
				out[3] <= 400 - 80 - 8 + 0.1f);
	}

	@Test
	public void captionFallsBelowWhenTheTopWouldClip() {
		float[] out = new float[4];
		PrefixPickLoupe.captionBox(200, 50, 40, 100, 30, 8, 400, 800, out);
		assertTrue("caption should drop below when above is off-screen",
				out[1] >= 50 + 40);
	}
}
