package com.resurrection.blowtorch2.lib.service.sensor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * A wave and a cover come out of the same sensor and are told apart by time
 * alone, so this is the whole of that distinction.
 */
public class GestureTimingTest {

	@Test
	public void aHandGoneQuicklyIsAWave() {
		assertEquals("wave", GestureTiming.classifyRelease(0L));
		assertEquals("wave", GestureTiming.classifyRelease(400L));
	}

	@Test
	public void aHandThatStayedIsNotAWaveWhenItLeaves() {
		// It has already fired as a cover by then, on the timer. Calling it a
		// wave as well would send both commands for one gesture.
		assertNull(GestureTiming.classifyRelease(GestureTiming.COVER_MILLIS + 1));
		assertNull(GestureTiming.classifyRelease(5000L));
	}

	@Test
	public void theBoundaryBelongsToTheWaveSoNothingFallsBetweenThem() {
		// The two limits are deliberately the same number. A gap would leave a
		// hand held for exactly that long as neither gesture, and the reading
		// would fail every so often with nothing to see in the settings.
		assertEquals(GestureTiming.COVER_MILLIS, GestureTiming.WAVE_MAX_MILLIS);
		assertEquals("wave", GestureTiming.classifyRelease(GestureTiming.WAVE_MAX_MILLIS));
	}

	@Test
	public void anImpossibleHoldIsNotAGesture() {
		assertNull(GestureTiming.classifyRelease(-1L));
	}
}
