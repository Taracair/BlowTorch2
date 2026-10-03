package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ChromeGesturesHoldTest {

	@Test
	public void overflowHoldCanBeCleared() {
		ChromeGestures g = ChromeGestures.parse("overflow.hold=\nedit.up=look\n");
		assertEquals("", g.get(ChromeGestures.TARGET_OVERFLOW, ChromeGestures.GESTURE_HOLD));
		assertEquals("look", g.get(ChromeGestures.TARGET_EDIT, "up"));
		String formatted = g.format();
		assertEquals(true, formatted.contains("overflow.hold=\n"));
		assertEquals(true, formatted.contains("edit.up=look\n"));
	}

	@Test
	public void missingOverflowHoldStaysUnset() {
		ChromeGestures g = ChromeGestures.parse("");
		assertNull(g.get(ChromeGestures.TARGET_OVERFLOW, ChromeGestures.GESTURE_HOLD));
	}
}
