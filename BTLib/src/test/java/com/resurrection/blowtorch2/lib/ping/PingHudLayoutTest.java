package com.resurrection.blowtorch2.lib.ping;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PingHudLayoutTest {

	@Test
	public void percentRoundTripAtEdges() {
		int outer = 200;
		int inner = 40;
		assertEquals(0, PingHudLayout.pxFromPercent(0, outer, inner));
		assertEquals(160, PingHudLayout.pxFromPercent(100, outer, inner));
		assertEquals(0, PingHudLayout.percentFromPx(0, outer, inner));
		assertEquals(100, PingHudLayout.percentFromPx(160, outer, inner));
	}

	@Test
	public void zeroSpanDoesNotDivide() {
		assertEquals(0, PingHudLayout.pxFromPercent(50, 10, 10));
		assertEquals(0, PingHudLayout.percentFromPx(5, 10, 10));
	}

	@Test
	public void alphaMatchesOpacity() {
		assertEquals(0.85f, PingHudLayout.alphaFromOpacity(85), 0.001f);
		assertEquals(0.15f, PingHudLayout.alphaFromOpacity(1), 0.001f);
	}

	@Test
	public void colorBands() {
		assertEquals(PingHudColor.UNKNOWN, PingHudColor.band(-1));
		assertEquals(PingHudColor.GOOD, PingHudColor.band(0));
		assertEquals(PingHudColor.GOOD, PingHudColor.band(79));
		assertEquals(PingHudColor.OK, PingHudColor.band(80));
		assertEquals(PingHudColor.SLOW, PingHudColor.band(180));
		assertEquals("—", PingHudColor.label(-1));
		assertEquals("42 ms", PingHudColor.label(42));
	}
}
