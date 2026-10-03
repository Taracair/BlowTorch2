package com.resurrection.blowtorch2.lib.service.sensor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class CustomShakeTest {

	@Test public void aSmallAngleIsCloserThanAnotherAxis() {
		ShakeTrace right = pulse(1f, 0f, 0f);
		ShakeTrace angled = pulse(1f, 0.35f, 0f);
		ShakeTrace up = pulse(0f, 1f, 0f);
		ShakeTrace forward = pulse(0f, 0f, 1f);
		ShakeTrace left = pulse(-1f, 0f, 0f);
		float same = dist(right, right);
		float angle = dist(right, angled);
		float other = dist(right, up);
		float depth = dist(right, forward);
		float opposite = dist(right, left);
		assertTrue("identical " + same, same < 0.02f);
		assertTrue("angle " + angle + " vs up " + other, angle < other);
		assertTrue("up " + other, other > 0.5f);
		assertTrue("forward " + depth, depth > 0.5f);
		assertTrue("opposite " + opposite, opposite > 0.5f);
	}

	@Test public void theClosestRecordingWinsAndTheSliderCanRejectIt() {
		CustomShakeLibrary.Entry right = entry("slash", 40, pulse(1f, 0f, 0f));
		CustomShakeLibrary.Entry poke = entry("poke", 40, pulse(0f, 0f, 1f));
		CustomShakeLibrary lib = CustomShakeLibrary.empty().with(right).with(poke);
		assertEquals("slash", lib.match(pulse(1f, 0.05f, 0f)));
		assertEquals("poke", lib.match(pulse(0f, 0.05f, 1f)));
		assertNull(lib.match(pulse(0f, 1f, 0f)));

		CustomShakeLibrary strict = CustomShakeLibrary.empty()
				.with(entry("slash", 0, pulse(1f, 0f, 0f)));
		assertEquals("slash", strict.match(pulse(1f, 0f, 0f)));
		assertNull(strict.match(pulse(1f, 0.8f, 0f)));
	}

	@Test public void twoCloseNamesPickTheNearerOne() {
		CustomShakeLibrary lib = CustomShakeLibrary.empty()
				.with(entry("flat", 100, pulse(1f, 0f, 0f)))
				.with(entry("down", 100, pulse(1f, -0.8f, 0f)));
		assertEquals("flat", lib.match(pulse(1f, -0.05f, 0f)));
		assertEquals("down", lib.match(pulse(1f, -0.75f, 0f)));
	}

	@Test public void fewerThanThreeStrokesDoNotMatch() {
		List<ShakeTrace> two = new ArrayList<ShakeTrace>();
		two.add(pulse(1f, 0f, 0f));
		two.add(pulse(1f, 0f, 0f));
		CustomShakeLibrary lib = CustomShakeLibrary.empty()
				.with(new CustomShakeLibrary.Entry("slash", 100, two));
		assertNull(lib.match(pulse(1f, 0f, 0f)));
	}

	@Test public void theDocumentRoundTrips() {
		CustomShakeLibrary lib = CustomShakeLibrary.empty()
				.with(entry("slash", 55, pulse(1f, -0.2f, 0.1f)));
		CustomShakeLibrary again = CustomShakeLibrary.decode(lib.encode());
		assertEquals(1, again.entries().size());
		assertEquals("slash", again.entries().get(0).getName());
		assertEquals(55, again.entries().get(0).getTolerance());
		assertEquals(CustomShakeLibrary.MIN_TRACES, again.entries().get(0).getTraces().size());
		assertEquals("slash", again.match(pulse(1f, -0.2f, 0.1f)));
	}

	@Test public void aBrokenDocumentIsEmpty() {
		assertTrue(CustomShakeLibrary.decode(null).entries().isEmpty());
		assertTrue(CustomShakeLibrary.decode("nope").entries().isEmpty());
		assertTrue(CustomShakeLibrary.decode("1\n1\nwave 40\n").entries().isEmpty());
	}

	@Test public void reservedNamesStayWithTheBuiltInReadings() {
		assertTrue(CustomShakeLibrary.isReserved("shake"));
		assertTrue(CustomShakeLibrary.isReserved("shakeright"));
		assertTrue(CustomShakeLibrary.isReserved("all"));
		assertNull(CustomShakeNames.gesture("wave"));
		assertNull(CustomShakeNames.gesture("a"));
		assertNotNull(CustomShakeNames.gesture("Slash"));
		assertEquals("u:slash", CustomShakeNames.gesture("Slash").getId());
	}

	@Test public void quietTailDoesNotBecomeTheShape() {
		ShakeCapture cap = new ShakeCapture();
		assertNull(cap.add(0L, 0f, 0f, 0f, 1f, 15f));
		assertNull(cap.add(10L, 20f, 1f, 0f, 20f, 15f));
		assertNull(cap.add(40L, 18f, 1f, 0f, 18f, 15f));
		assertNull(cap.add(70L, 16f, 0f, 0f, 16f, 15f));
		assertNull(cap.add(100L, 12f, 0f, 0f, 12f, 15f));
		ShakeTrace done = cap.add(300L, 0.2f, 0f, 0f, 0.2f, 15f);
		assertNotNull(done);
		assertTrue(done.size() >= ShakeCapture.MIN_SAMPLES);
		assertTrue(done.x(done.size() - 1) > 5f);
	}

	@Test public void aSingleSpikeIsNotAStroke() {
		ShakeCapture cap = new ShakeCapture();
		assertNull(cap.add(0L, 20f, 0f, 0f, 20f, 15f));
		assertNull(cap.add(200L, 0f, 0f, 0f, 0f, 15f));
	}

	@Test public void theNextStrokeWaitsFromThePreviousStart() {
		assertTrue(ShakeCapture.mayStart(1000L, -1L));
		assertFalse(ShakeCapture.mayStart(1499L, 1000L));
		assertTrue(ShakeCapture.mayStart(1500L, 1000L));
		assertTrue(ShakeCapture.mayStart(1520L, 1000L));
	}

	@Test public void theFortyFirstNameIsRefused() {
		CustomShakeLibrary lib = CustomShakeLibrary.empty();
		for (int i = 0; i < CustomShakeLibrary.MAX_NAMES; i++) {
			lib = lib.with(entry("n" + i, 40, pulse(1f, 0f, 0f)));
		}
		assertEquals(CustomShakeLibrary.MAX_NAMES, lib.entries().size());
		CustomShakeLibrary stuck = lib.with(entry("extra", 40, pulse(0f, 1f, 0f)));
		assertSame(lib, stuck);
		assertNull(stuck.byName("extra"));
		CustomShakeLibrary replaced = lib.with(entry("n0", 10, pulse(0f, 1f, 0f)));
		assertEquals(CustomShakeLibrary.MAX_NAMES, replaced.entries().size());
		assertEquals(10, replaced.byName("n0").getTolerance());
		assertTrue(CustomShakeLibrary.decode("1\n41\n").entries().isEmpty());
		assertTrue(CustomShakeStore.dropsClaimedEntries("1\n41\n",
				CustomShakeLibrary.empty()));
		assertFalse(CustomShakeStore.dropsClaimedEntries("1\n0\n",
				CustomShakeLibrary.empty()));
	}

	@Test public void screenRightStaysRightWhenThePhoneTurns() {
		float[] out = new float[3];
		ScreenAxes.toScreen(ScreenAxes.ROTATION_0, 2f, 3f, 4f, out);
		assertEquals(2f, out[0], 0.001f);
		assertEquals(3f, out[1], 0.001f);
		assertEquals(4f, out[2], 0.001f);
		ScreenAxes.toScreen(ScreenAxes.ROTATION_90, 2f, 3f, 4f, out);
		assertEquals(3f, out[0], 0.001f);
		assertEquals(-2f, out[1], 0.001f);
		assertEquals(4f, out[2], 0.001f);
		ScreenAxes.toScreen(ScreenAxes.ROTATION_180, 2f, 3f, 4f, out);
		assertEquals(-2f, out[0], 0.001f);
		assertEquals(-3f, out[1], 0.001f);
		ScreenAxes.toScreen(ScreenAxes.ROTATION_270, 2f, 3f, 4f, out);
		assertEquals(-3f, out[0], 0.001f);
		assertEquals(2f, out[1], 0.001f);
		assertEquals(4f, out[2], 0.001f);
	}

	private static CustomShakeLibrary.Entry entry(final String name, final int tolerance,
			final ShakeTrace trace) {
		return new CustomShakeLibrary.Entry(name, tolerance,
				Arrays.asList(trace, trace, trace));
	}

	private static float dist(final ShakeTrace a, final ShakeTrace b) {
		return a.resampled(CustomShakeLibrary.POINTS).normalized()
				.distanceTo(b.resampled(CustomShakeLibrary.POINTS).normalized());
	}

	/** A half-sine burst along one direction, out and not back. */
	private static ShakeTrace pulse(final float x, final float y, final float z) {
		int n = 9;
		float[] xs = new float[n];
		float[] ys = new float[n];
		float[] zs = new float[n];
		for (int i = 0; i < n; i++) {
			float s = (float) Math.sin(Math.PI * i / (n - 1));
			xs[i] = x * s * 20f;
			ys[i] = y * s * 20f;
			zs[i] = z * s * 20f;
		}
		return new ShakeTrace(xs, ys, zs);
	}
}
