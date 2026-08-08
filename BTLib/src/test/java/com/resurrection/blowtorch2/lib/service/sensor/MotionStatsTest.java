package com.resurrection.blowtorch2.lib.service.sensor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The arithmetic a shake threshold will be chosen from. A wrong count here is a
 * wrong threshold on every phone, and the report would still look convincing.
 */
public class MotionStatsTest {

	private static final long MS = 1000L * 1000L;

	/** One reading every 20 ms, quiet, with a burst of movement at the given second. */
	private static MotionStats withShakesAt(final double magnitude, final int... shakeIndexes) {
		MotionStats s = new MotionStats();
		for (int i = 0; i < 500; i++) {
			double value = 0.5;
			for (int k = 0; k < shakeIndexes.length; k++) {
				// Six consecutive loud readings: what one wrist flick looks like.
				if (i >= shakeIndexes[k] && i < shakeIndexes[k] + 6) {
					value = magnitude;
				}
			}
			s.record(i * 20L * MS, value);
		}
		return s;
	}

	@Test
	public void oneShakeIsOneGestureNotSixCrossings() {
		// The dead time is the whole point: six readings above the line inside
		// 120 ms are one flick of the wrist, and a detector without it sends the
		// command six times. That bug has already been paid for once here, on
		// the per-trigger sound gap.
		MotionStats s = withShakesAt(18.0, 100);
		assertEquals(1, s.gesturesAbove(12.0, MotionStats.DEFAULT_DEAD_TIME_NANOS));
	}

	@Test
	public void shakesFurtherApartThanTheDeadTimeCountSeparately() {
		// 100, 150, 200 → one second apart at 20 ms per reading.
		MotionStats s = withShakesAt(18.0, 100, 150, 200);
		assertEquals(3, s.gesturesAbove(12.0, MotionStats.DEFAULT_DEAD_TIME_NANOS));
	}

	@Test
	public void aThresholdAboveThePeakFiresNothing() {
		MotionStats s = withShakesAt(18.0, 100, 150, 200);
		assertEquals(0, s.gesturesAbove(25.0, MotionStats.DEFAULT_DEAD_TIME_NANOS));
		assertEquals(18.0, s.peak(), 0.001);
	}

	@Test
	public void rateIsMeasuredFromTheSensorsOwnTimestamps() {
		MotionStats s = withShakesAt(18.0, 100);
		assertEquals(50.0, s.measuredHz(), 0.5);
		assertEquals(20.0, s.largestGapMillis(), 0.5);
	}

	@Test
	public void aHardwareBatchIsVisibleAsNearZeroGaps() {
		// Ten readings handed over at once, then a normal stretch. A detector
		// timing by the clock instead of by event.timestamp sees one movement
		// as ten; this is how we find out whether this device does that.
		MotionStats s = new MotionStats();
		for (int i = 0; i < 10; i++) {
			s.record(i * 20L * MS, 1.0);
		}
		for (int i = 0; i < 10; i++) {
			s.record((200L + i) * MS, 1.0);
		}
		assertTrue("expected the tight run to be visible", s.batchedSamples() >= 9);
	}

	@Test
	public void gravityIsTakenOutOfARawAccelerometerReading() {
		// A phone lying still on the table is not moving, whatever the raw
		// magnitude says.
		assertEquals(0.0, MotionStats.gravityRemoved(0.0, 0.0, 9.81, 9.81), 0.001);
		// Free fall is movement too, and it reads below gravity, not above.
		assertEquals(9.81, MotionStats.gravityRemoved(0.0, 0.0, 0.0, 9.81), 0.001);
		assertEquals(5.19, MotionStats.gravityRemoved(0.0, 0.0, 15.0, 9.81), 0.001);
	}

	@Test
	public void anEmptyRunSaysSoRatherThanReportingZeroes() {
		MotionStats s = new MotionStats();
		s.describeSource("accelerometer", false);
		String report = s.report();
		assertTrue(report.contains("NONE"));
		assertEquals(0, s.getSampleCount());
		assertEquals(0.0, s.measuredHz(), 0.001);
	}

	@Test
	public void theReportNamesItsSourceAndScoresEveryCandidate() {
		MotionStats s = withShakesAt(18.0, 100, 150, 200);
		s.describeSource("linear acceleration", true);
		String report = s.report();
		assertTrue(report.contains("linear acceleration"));
		assertTrue(report.contains("(wake-up)"));
		assertTrue(report.contains("above  12.0 m/s2 : 3"));
		assertTrue(report.contains("above  25.0 m/s2 : 0"));
	}
}
