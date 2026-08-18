/*
 * Copyright (C) BlowTorch contributors
 */
package com.resurrection.blowtorch2.lib.gauge;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Resize-grip hit box without constructing {@link GaugeWidgetView} (JVM tests
 * have no Robolectric).
 */
public class GaugeWidgetViewHitTest {

	@Test
	public void bottomRightSquare() {
		assertEquals(20, GaugeResizeGrip.GRIP_DP);
		assertEquals(20, GaugeResizeGrip.gripPx(1f));
		assertEquals(60, GaugeResizeGrip.gripPx(3f));
		assertArrayEquals(new int[] { 180, 20, 200, 40 },
				GaugeResizeGrip.rect(200, 40, 20));
	}

	@Test
	public void containsIsHalfOpen() {
		assertTrue(GaugeResizeGrip.contains(180, 20, 200, 40, 20));
		assertTrue(GaugeResizeGrip.contains(199, 39, 200, 40, 20));
		assertFalse(GaugeResizeGrip.contains(179, 20, 200, 40, 20));
		assertFalse(GaugeResizeGrip.contains(180, 19, 200, 40, 20));
		assertFalse(GaugeResizeGrip.contains(200, 39, 200, 40, 20));
		assertFalse(GaugeResizeGrip.contains(199, 40, 200, 40, 20));
		assertFalse(GaugeResizeGrip.contains(0, 0, 200, 40, 20));
	}

	@Test
	public void tinyViewKeepsBottomRightSquare() {
		// Side is min(grip, w, h): 8×8 in the bottom-right of a 10×8 view.
		assertArrayEquals(new int[] { 2, 0, 10, 8 },
				GaugeResizeGrip.rect(10, 8, 20));
		assertTrue(GaugeResizeGrip.contains(2, 0, 10, 8, 20));
		assertFalse(GaugeResizeGrip.contains(1, 0, 10, 8, 20));
		assertFalse(GaugeResizeGrip.contains(0, 0, 0, 0, 20));
	}
}
