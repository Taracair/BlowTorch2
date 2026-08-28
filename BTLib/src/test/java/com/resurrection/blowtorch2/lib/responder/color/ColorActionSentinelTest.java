package com.resurrection.blowtorch2.lib.responder.color;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Trigger background sentinels are indices, not remapped RGB. 0 / 16 / 231
 * still mean foreground-only ({@code 49} in the stream), even on light paper.
 */
public class ColorActionSentinelTest {

	@Test
	public void sentinelBackgroundsStayForegroundOnly() {
		assertTrue(ColorAction.skipsBackgroundPaint(0));
		assertTrue(ColorAction.skipsBackgroundPaint(16));
		assertTrue(ColorAction.skipsBackgroundPaint(231));
		assertFalse(ColorAction.skipsBackgroundPaint(15));
		assertFalse(ColorAction.skipsBackgroundPaint(17));
		assertFalse(ColorAction.skipsBackgroundPaint(20));
		assertFalse(ColorAction.skipsBackgroundPaint(232));
		assertFalse(ColorAction.skipsBackgroundPaint(255));
	}
}
