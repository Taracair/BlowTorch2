package com.resurrection.blowtorch2.lib.gauge;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GaugeGeometryTest {

	@Test
	public void hbarFill_ratios() {
		assertArrayEquals(new int[] { 10, 20, 10, 28 },
				GaugeGeometry.hbarFill(10, 20, 100, 8, 0));
		assertArrayEquals(new int[] { 10, 20, 60, 28 },
				GaugeGeometry.hbarFill(10, 20, 100, 8, 0.5));
		assertArrayEquals(new int[] { 10, 20, 110, 28 },
				GaugeGeometry.hbarFill(10, 20, 100, 8, 1));
	}

	@Test
	public void vbarFill_ratios() {
		assertArrayEquals(new int[] { 10, 28, 30, 28 },
				GaugeGeometry.vbarFill(10, 20, 20, 8, 0));
		assertArrayEquals(new int[] { 10, 24, 30, 28 },
				GaugeGeometry.vbarFill(10, 20, 20, 8, 0.5));
		assertArrayEquals(new int[] { 10, 20, 30, 28 },
				GaugeGeometry.vbarFill(10, 20, 20, 8, 1));
	}

	@Test
	public void ringSweep_ratiosIncludingTimer() {
		assertEquals(0f, GaugeGeometry.ringSweepDegrees(0), 0.001f);
		assertEquals(180f, GaugeGeometry.ringSweepDegrees(0.5), 0.001f);
		assertEquals(360f, GaugeGeometry.ringSweepDegrees(1), 0.001f);
		GaugeWidget timer = new GaugeWidget("stun");
		timer.setShape(GaugeWidget.Shape.TIMER);
		timer.setDurationSec(30);
		timer.setRemainSec(15);
		assertEquals(180f, GaugeGeometry.ringSweepDegrees(timer.ratio()), 0.001f);
		timer.setRemainSec(0);
		assertEquals(0f, GaugeGeometry.ringSweepDegrees(timer.ratio()), 0.001f);
		timer.setRemainSec(30);
		assertEquals(360f, GaugeGeometry.ringSweepDegrees(timer.ratio()), 0.001f);
	}

	@Test
	public void warn_thresholdAndDisabled() {
		GaugeWidget g = new GaugeWidget("hp");
		g.setLiveValue(20);
		g.setLiveMax(100);
		g.setWarnPct(25);
		assertEquals(0.2, g.ratio(), 0.0001);
		assertTrue(g.isLow());
		g.setLiveValue(25);
		assertTrue(g.isLow());
		g.setLiveValue(26);
		assertFalse(g.isLow());
		g.setWarnPct(0);
		g.setLiveValue(0);
		assertFalse(g.isLow());
		GaugeWidget timer = new GaugeWidget("stun");
		timer.setShape(GaugeWidget.Shape.TIMER);
		timer.setDurationSec(100);
		timer.setRemainSec(20);
		timer.setWarnPct(25);
		assertTrue(timer.isLow());
	}

	@Test
	public void clampSize() {
		assertArrayEquals(new int[] { 24, 24 },
				GaugeGeometry.clampSize(8, 8, 24, 400, 400));
		assertArrayEquals(new int[] { 200, 24 },
				GaugeGeometry.clampSize(200, 24, 8, 400, 400));
		assertArrayEquals(new int[] { 400, 80 },
				GaugeGeometry.clampSize(900, 900, 8, 400, 80));
	}

	@Test
	public void ratio_clampsAndZeroMax() {
		GaugeWidget g = new GaugeWidget("hp");
		g.setLiveValue(50);
		g.setLiveMax(100);
		assertEquals(0.5, g.ratio(), 0.0001);
		g.setLiveValue(-10);
		assertEquals(0.0, g.ratio(), 0.0001);
		g.setLiveValue(200);
		assertEquals(1.0, g.ratio(), 0.0001);
		g.setLiveMax(0);
		assertEquals(0.0, g.ratio(), 0.0001);
		GaugeWidget timer = new GaugeWidget("stun");
		timer.setShape(GaugeWidget.Shape.TIMER);
		timer.setDurationSec(0);
		timer.setRemainSec(12);
		assertEquals(0.0, timer.ratio(), 0.0001);
	}
}
