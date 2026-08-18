/*
 * Copyright (C) BlowTorch contributors
 */
package com.resurrection.blowtorch2.lib.gauge;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * JVM math for {@link GaugeGeometry} + {@link GaugePainter} (no Canvas / View).
 */
public class GaugePainterMathTest {

	@Test
	public void hbarFillLeftToRight() {
		assertArrayEquals(new int[] { 0, 0, 0, 20 },
				GaugeGeometry.hbarFill(0, 0, 100, 20, 0.0));
		assertArrayEquals(new int[] { 0, 0, 50, 20 },
				GaugeGeometry.hbarFill(0, 0, 100, 20, 0.5));
		assertArrayEquals(new int[] { 0, 0, 100, 20 },
				GaugeGeometry.hbarFill(0, 0, 100, 20, 1.0));
		assertArrayEquals(new int[] { 10, 5, 60, 25 },
				GaugeGeometry.hbarFill(10, 5, 100, 20, 0.5));
	}

	@Test
	public void vbarFillBottomToTop() {
		assertArrayEquals(new int[] { 0, 100, 20, 100 },
				GaugeGeometry.vbarFill(0, 0, 20, 100, 0.0));
		assertArrayEquals(new int[] { 0, 50, 20, 100 },
				GaugeGeometry.vbarFill(0, 0, 20, 100, 0.5));
		assertArrayEquals(new int[] { 0, 0, 20, 100 },
				GaugeGeometry.vbarFill(0, 0, 20, 100, 1.0));
	}

	@Test
	public void ringSweepFromTwelveOClock() {
		assertEquals(-90f, GaugePainter.RING_START_ANGLE_DEG, 0.01f);
		assertEquals(0f, GaugeGeometry.ringSweepDegrees(0), 0.01f);
		assertEquals(180f, GaugeGeometry.ringSweepDegrees(0.5), 0.01f);
		assertEquals(360f, GaugeGeometry.ringSweepDegrees(1), 0.01f);
		assertEquals(0f, GaugeGeometry.ringSweepDegrees(-1), 0.01f);
		assertEquals(360f, GaugeGeometry.ringSweepDegrees(2), 0.01f);
		assertEquals(0f, GaugeGeometry.ringSweepDegrees(Double.NaN), 0.01f);
	}

	@Test
	public void timerUsesSameSweepAsRing() {
		assertEquals(GaugeGeometry.ringSweepDegrees(0.25),
				GaugeGeometry.ringSweepDegrees(GaugePainter.ratio(15, 60)), 0.01f);
		GaugeWidget timer = new GaugeWidget("stun");
		timer.setShape(GaugeWidget.Shape.TIMER);
		timer.setDurationSec(30);
		timer.setRemainSec(15);
		assertEquals((float) timer.ratio(), GaugePainter.ratio(15, 30), 0.01f);
		assertEquals(180f, GaugeGeometry.ringSweepDegrees(timer.ratio()), 0.01f);
	}

	@Test
	public void ratioClamps() {
		assertEquals(0f, GaugePainter.ratio(10, 0), 0.01f);
		assertEquals(0f, GaugePainter.ratio(-4, 10), 0.01f);
		assertEquals(1f, GaugePainter.ratio(12, 10), 0.01f);
		assertEquals(0.5f, GaugePainter.ratio(50, 100), 0.01f);
		assertEquals(0f, GaugePainter.ratio(Double.NaN, 100), 0.01f);
	}

	@Test
	public void lowUsesWarnColor() {
		assertEquals(0xFFFFAA00, GaugePainter.resolveFillColor(true, 0xFFCC2222, 0xFFFFAA00));
		assertEquals(0xFFCC2222, GaugePainter.resolveFillColor(false, 0xFFCC2222, 0xFFFFAA00));
	}

	@Test
	public void timerStrokeIsThickerThanRing() {
		float ring = GaugePainter.ringStrokeWidth(100, 100, false);
		float timer = GaugePainter.ringStrokeWidth(100, 100, true);
		assertTrue("timer " + timer + " should exceed ring " + ring, timer > ring);
		assertEquals(100f * GaugePainter.RING_STROKE_FRACTION, ring, 0.01f);
		assertEquals(100f * GaugePainter.TIMER_STROKE_FRACTION, timer, 0.01f);
	}

	@Test
	public void canonicalizeShapes() {
		assertEquals(GaugePainter.SHAPE_HBAR, GaugePainter.canonicalizeShape(null));
		assertEquals(GaugePainter.SHAPE_HBAR, GaugePainter.canonicalizeShape("bar"));
		assertEquals(GaugePainter.SHAPE_VBAR, GaugePainter.canonicalizeShape("vertical"));
		assertEquals(GaugePainter.SHAPE_RING, GaugePainter.canonicalizeShape("zelda"));
		assertEquals(GaugePainter.SHAPE_TIMER, GaugePainter.canonicalizeShape("tim"));
		assertEquals(GaugePainter.SHAPE_TIMER, GaugePainter.canonicalizeShape("TIMER"));
		assertEquals(GaugePainter.SHAPE_TIMER, GaugePainter.canonicalizeShape("countdown"));
		assertEquals(GaugePainter.SHAPE_HBAR, GaugePainter.canonicalizeShape("ime"));
	}

	@Test
	public void formatAmountSkipsDecimalsWhenWhole() {
		assertEquals("80/100", GaugePainter.formatAmount(80, 100));
		assertEquals("0/0", GaugePainter.formatAmount(0, 0));
		assertEquals("12.5/30", GaugePainter.formatAmount(12.5, 30));
	}

	@Test
	public void clampSizeMinThenMax() {
		assertArrayEquals(new int[] { 20, 20 },
				GaugeGeometry.clampSize(4, 8, 20, 400, 400));
		assertArrayEquals(new int[] { 100, 50 },
				GaugeGeometry.clampSize(100, 50, 20, 400, 400));
		assertArrayEquals(new int[] { 400, 400 },
				GaugeGeometry.clampSize(900, 900, 20, 400, 400));
	}
}
