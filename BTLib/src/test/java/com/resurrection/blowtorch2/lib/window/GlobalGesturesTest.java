package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GlobalGesturesTest {

	@Test
	public void defaultsAreClassicAndHold() {
		GlobalGestures g = GlobalGestures.defaults();
		assertEquals(GlobalGestures.MODE_CLASSIC, g.mode());
		assertEquals(GlobalGestures.SCROLL_HOLD, g.scroll());
		assertEquals(280, g.holdMs());
		assertEquals(false, g.showMode());
		assertEquals(true, g.twoDir());
		assertEquals(false, g.twoScroll());
		assertNull(g.binding(1, "n"));
	}

	@Test
	public void directionsAreEqualSlices() {
		assertEquals("e", GlobalGestures.direction(100f, 0f, 24f));
		assertEquals("w", GlobalGestures.direction(-100f, 0f, 24f));
		assertEquals("s", GlobalGestures.direction(0f, 100f, 24f));
		assertEquals("n", GlobalGestures.direction(0f, -100f, 24f));
		assertEquals("se", GlobalGestures.direction(100f, 100f, 24f));
		assertEquals("ne", GlobalGestures.direction(100f, -100f, 24f));
		assertEquals("sw", GlobalGestures.direction(-100f, 100f, 24f));
		assertEquals("nw", GlobalGestures.direction(-100f, -100f, 24f));
		assertNull(GlobalGestures.direction(10f, 0f, 24f));
	}

	@Test
	public void bindingsRoundTripAndSkipBlanks() {
		GlobalGestures g = new GlobalGestures(GlobalGestures.MODE_ONE,
				GlobalGestures.SCROLL_OFF, 280, false, true, true, true, true, false,
				"1.n=north\n2.sw=southwest\nbad\n1.nope=x\n");
		assertEquals("north", g.binding(1, "n"));
		assertEquals("southwest", g.binding(2, "sw"));
		assertNull(g.binding(1, "e"));
		String formatted = g.formatBindings();
		assertTrue(formatted.contains("1.n=north"));
		assertTrue(formatted.contains("2.sw=southwest"));
	}

	@Test
	public void holdClamps() {
		assertEquals(80, GlobalGestures.clampHold(1));
		assertEquals(800, GlobalGestures.clampHold(5000));
		assertEquals(280, GlobalGestures.clampHold(280));
	}

	@Test
	public void classicGreysEverythingAGestureWouldUse() {
		assertFalse(GlobalGestures.optionUnused(GlobalGestures.KEY_MODE, 0, 1));
		assertFalse(GlobalGestures.optionUnused(GlobalGestures.KEY_SHOW_MODE, 0, 1));
		assertTrue(GlobalGestures.optionUnused(GlobalGestures.KEY_SCROLL, 0, 1));
		assertTrue(GlobalGestures.optionUnused(GlobalGestures.KEY_HOLD_MS, 0, 1));
		assertTrue(GlobalGestures.optionUnused(GlobalGestures.KEY_TWO_DIR, 0, 1));
		assertTrue(GlobalGestures.optionUnused(GlobalGestures.KEY_SHOW_ARROW, 0, 1));
	}

	@Test
	public void holdRowIsLiveOnlyWhileOneFingerGesturesUseHold() {
		assertFalse(GlobalGestures.optionUnused(GlobalGestures.KEY_HOLD_MS,
				GlobalGestures.MODE_ONE, GlobalGestures.SCROLL_HOLD));
		assertTrue(GlobalGestures.optionUnused(GlobalGestures.KEY_HOLD_MS,
				GlobalGestures.MODE_ONE, GlobalGestures.SCROLL_OFF));
		assertTrue(GlobalGestures.optionUnused(GlobalGestures.KEY_SCROLL,
				GlobalGestures.MODE_TWO, GlobalGestures.SCROLL_HOLD));
		assertFalse(GlobalGestures.optionUnused(GlobalGestures.KEY_TWO_DIR,
				GlobalGestures.MODE_BOTH, GlobalGestures.SCROLL_HOLD));
		assertTrue(GlobalGestures.optionUnused(GlobalGestures.KEY_TWO_DIR,
				GlobalGestures.MODE_ONE, GlobalGestures.SCROLL_TWO));
		assertTrue(GlobalGestures.optionUnused(GlobalGestures.KEY_TWO_SCROLL,
				GlobalGestures.MODE_ONE, GlobalGestures.SCROLL_TWO));
		assertTrue(GlobalGestures.optionUnused(GlobalGestures.KEY_TWO_SCROLL,
				GlobalGestures.MODE_TWO, GlobalGestures.SCROLL_HOLD));
		assertFalse(GlobalGestures.optionUnused(GlobalGestures.KEY_TWO_SCROLL,
				GlobalGestures.MODE_BOTH, GlobalGestures.SCROLL_HOLD));
		assertTrue(GlobalGestures.optionUnused(GlobalGestures.KEY_TWO_SCROLL,
				GlobalGestures.MODE_BOTH, GlobalGestures.SCROLL_TWO));
		assertTrue(GlobalGestures.optionUnused(GlobalGestures.KEY_TWO_DIR,
				GlobalGestures.MODE_BOTH, GlobalGestures.SCROLL_TWO));
		assertEquals("With two fingers", GlobalGestures.scrollLabel(GlobalGestures.SCROLL_TWO));
		assertEquals("Off", GlobalGestures.scrollLabel(GlobalGestures.SCROLL_OFF));
	}

	@Test
	public void scrollCommandMatchesTheGreyRow() {
		assertEquals(Integer.valueOf(GlobalGestures.SCROLL_TWO), GlobalGestures.scrollIndex("two"));
		assertEquals(Integer.valueOf(GlobalGestures.SCROLL_TWO), GlobalGestures.scrollIndex("With"));
		assertEquals(Integer.valueOf(GlobalGestures.SCROLL_HOLD), GlobalGestures.scrollIndex("hold"));
		assertEquals(Integer.valueOf(GlobalGestures.SCROLL_OFF), GlobalGestures.scrollIndex("off"));
		assertNull(GlobalGestures.scrollIndex("classic"));
		assertNull(GlobalGestures.scrollIndex("2"));
		assertTrue(GlobalGestures.optionUnused(GlobalGestures.KEY_SCROLL,
				GlobalGestures.MODE_CLASSIC, GlobalGestures.SCROLL_HOLD));
		assertTrue(GlobalGestures.optionUnused(GlobalGestures.KEY_SCROLL,
				GlobalGestures.MODE_TWO, GlobalGestures.SCROLL_TWO));
		assertFalse(GlobalGestures.optionUnused(GlobalGestures.KEY_SCROLL,
				GlobalGestures.MODE_ONE, GlobalGestures.SCROLL_OFF));
		assertFalse(GlobalGestures.optionUnused(GlobalGestures.KEY_SCROLL,
				GlobalGestures.MODE_BOTH, GlobalGestures.SCROLL_TWO));
	}
}
