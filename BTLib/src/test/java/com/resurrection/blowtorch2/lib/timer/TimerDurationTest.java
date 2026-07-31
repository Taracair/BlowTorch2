package com.resurrection.blowtorch2.lib.timer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Storage stays "total seconds"; these guard the h/m/s face put on it in the editor. */
public class TimerDurationTest {

	@Test
	public void splitsThenRecomposesExactly() {
		int[] samples = { 0, 1, 30, 59, 60, 90, 3599, 3600, 5400, 86399, 86400, 123456 };
		for (int total : samples) {
			int h = TimerDuration.hoursOf(total);
			int m = TimerDuration.minutesOf(total);
			int s = TimerDuration.secondsOf(total);
			assertEquals("round trip of " + total, total, TimerDuration.toSeconds(h, m, s));
		}
	}

	@Test
	public void splitsIntoFieldsInRange() {
		assertEquals(1, TimerDuration.hoursOf(5400));
		assertEquals(30, TimerDuration.minutesOf(5400));
		assertEquals(0, TimerDuration.secondsOf(5400));

		assertEquals(0, TimerDuration.hoursOf(90));
		assertEquals(1, TimerDuration.minutesOf(90));
		assertEquals(30, TimerDuration.secondsOf(90));
	}

	@Test
	public void addsFieldsRatherThanRangeCheckingThem() {
		assertEquals(90, TimerDuration.toSeconds(0, 0, 90));
		assertEquals(5400, TimerDuration.toSeconds(0, 90, 0));
		assertEquals(3661, TimerDuration.toSeconds(1, 1, 1));
	}

	@Test
	public void blankAndJunkFieldsReadAsZero() {
		assertEquals(0, TimerDuration.parseField(null));
		assertEquals(0, TimerDuration.parseField(""));
		assertEquals(0, TimerDuration.parseField("   "));
		assertEquals(0, TimerDuration.parseField("abc"));
		assertEquals(0, TimerDuration.parseField("-5"));
		assertEquals(12, TimerDuration.parseField(" 12 "));
	}

	@Test
	public void negativeTotalsDoNotProduceNegativeFields() {
		assertEquals(0, TimerDuration.hoursOf(-10));
		assertEquals(0, TimerDuration.minutesOf(-10));
		assertEquals(0, TimerDuration.secondsOf(-10));
		assertEquals("0s", TimerDuration.format(-10));
	}

	@Test
	public void formatsForListRows() {
		assertEquals("45s", TimerDuration.format(45));
		assertEquals("2m 05s", TimerDuration.format(125));
		assertEquals("2m", TimerDuration.format(120));
		assertEquals("1h 30m", TimerDuration.format(5400));
		assertEquals("1h", TimerDuration.format(3600));
		assertEquals("0s", TimerDuration.format(0));
	}
}
