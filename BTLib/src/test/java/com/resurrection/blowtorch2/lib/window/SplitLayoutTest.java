package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SplitLayoutTest {

	@Test
	public void emptyIsStatus() {
		assertEquals(SplitLayout.ACTION_STATUS, SplitLayout.parse("").action);
		assertEquals(SplitLayout.ACTION_STATUS, SplitLayout.parse(null).action);
	}

	@Test
	public void offAlone() {
		assertEquals(SplitLayout.ACTION_OFF, SplitLayout.parse("off").action);
		assertEquals(SplitLayout.ACTION_OFF, SplitLayout.parse("hide").action);
		assertNotNull(SplitLayout.parse("off 40").error);
	}

	@Test
	public void percentAloneEnables() {
		SplitLayout.Result r = SplitLayout.parse("40");
		assertEquals(SplitLayout.ACTION_APPLY, r.action);
		assertTrue(r.percentSet);
		assertEquals(40, r.percent);
		assertFalse(r.orientationSet);
		assertEquals(SplitLayout.ORIENTATION_HORIZONTAL, r.orientation);
	}

	@Test
	public void defaultPercentIsFifty() {
		SplitLayout.Result r = SplitLayout.parse("on");
		assertEquals(SplitLayout.ACTION_APPLY, r.action);
		assertFalse(r.percentSet);
		assertEquals(SplitLayout.DEFAULT_PERCENT, r.percent);
	}

	@Test
	public void orientationTokens() {
		assertEquals(SplitLayout.ORIENTATION_HORIZONTAL,
				SplitLayout.parse("left").orientation);
		assertEquals(SplitLayout.ORIENTATION_HORIZONTAL,
				SplitLayout.parse("horizontal").orientation);
		assertEquals(SplitLayout.ORIENTATION_HORIZONTAL,
				SplitLayout.parse("h").orientation);
		assertEquals(SplitLayout.ORIENTATION_VERTICAL,
				SplitLayout.parse("top").orientation);
		assertEquals(SplitLayout.ORIENTATION_VERTICAL,
				SplitLayout.parse("vertical").orientation);
		assertEquals(SplitLayout.ORIENTATION_VERTICAL,
				SplitLayout.parse("v").orientation);
		assertTrue(SplitLayout.parse("top").orientationSet);
	}

	@Test
	public void percentAndOrientationEitherOrder() {
		SplitLayout.Result a = SplitLayout.parse("40 top");
		assertEquals(40, a.percent);
		assertEquals(SplitLayout.ORIENTATION_VERTICAL, a.orientation);
		SplitLayout.Result b = SplitLayout.parse("vertical 60");
		assertEquals(60, b.percent);
		assertEquals(SplitLayout.ORIENTATION_VERTICAL, b.orientation);
	}

	@Test
	public void clampPercent() {
		assertEquals(SplitLayout.MIN_PERCENT, SplitLayout.clampPercent(0));
		assertEquals(SplitLayout.MAX_PERCENT, SplitLayout.clampPercent(100));
		assertEquals(50, SplitLayout.clampPercent(50));
		assertEquals(SplitLayout.MIN_PERCENT, SplitLayout.parse("5").percent);
		assertEquals(SplitLayout.MAX_PERCENT, SplitLayout.parse("99").percent);
	}

	@Test
	public void unknownIsError() {
		assertNotNull(SplitLayout.parse("banana").error);
		assertNull(SplitLayout.parse("40").error);
	}

	@Test
	public void describe() {
		assertTrue(SplitLayout.describe(false, SplitLayout.ORIENTATION_HORIZONTAL, 50)
				.contains("off"));
		assertTrue(SplitLayout.describe(true, SplitLayout.ORIENTATION_VERTICAL, 40)
				.contains("40%"));
		assertTrue(SplitLayout.describe(true, SplitLayout.ORIENTATION_VERTICAL, 40)
				.contains("vertical"));
		assertTrue(SplitLayout.describe(true, SplitLayout.ORIENTATION_VERTICAL, 40)
				.contains("top/bottom"));
		assertTrue(SplitLayout.describe(true, SplitLayout.ORIENTATION_HORIZONTAL, 50)
				.contains("horizontal"));
		assertTrue(SplitLayout.describe(true, SplitLayout.ORIENTATION_HORIZONTAL, 50)
				.contains("left/right"));
	}

	@Test
	public void usageNamesOrientations() {
		String u = SplitLayout.usage();
		assertTrue(u.contains("horizontal|h|vertical|v|left|right|top|bottom"));
		assertTrue(u.contains("horizontal/h is left/right"));
		assertTrue(u.contains("vertical/v is top/bottom"));
	}
}
