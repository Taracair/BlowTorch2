package com.resurrection.blowtorch2.lib.timer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * {@code java.util.Timer.schedule} throws {@code IllegalArgumentException} on a negative
 * delay or a non-positive period, and it is reached from a synchronous binder method with
 * no catch, so the exception lands in the UI process. These pin the clamps that stop a
 * stored remaining time from ever becoming one.
 */
public class TimerScheduleTest {

	@Test
	public void pausingAfterTheRunOverranDoesNotStoreANegative() {
		// A 10 s repeat timer, an hour of doze: elapsedRealtime counts through sleep,
		// java.util.Timer does not fire during it. This produced -3590.
		int remaining = TimerSchedule.remainingAfterPause(10, 3600L * 1000L);
		assertTrue("remaining must never be negative", remaining >= 0);
		assertEquals("an overrun run is finished, so the next play is a full one", 10, remaining);
	}

	@Test
	public void pausingPartWayKeepsThePosition() {
		assertEquals(7, TimerSchedule.remainingAfterPause(10, 3000L));
		assertEquals(1, TimerSchedule.remainingAfterPause(10, 9500L));
	}

	@Test
	public void aBackwardsClockDoesNotProduceARemainingBiggerThanTheTimer() {
		assertEquals(10, TimerSchedule.remainingAfterPause(10, -5000L));
	}

	@Test
	public void everyDelayAndPeriodTheSchedulerCanBeGivenIsPositive() {
		int[] durations = { Integer.MIN_VALUE, -1, 0, 1, 30, 3600, 86400,
			Integer.MAX_VALUE / 2, Integer.MAX_VALUE };
		int[] remainings = { Integer.MIN_VALUE, -3590, -1, 0, 1, 9, 30, 100000,
			Integer.MAX_VALUE };
		for (int seconds : durations) {
			assertTrue("period for " + seconds, TimerSchedule.periodMillis(seconds) > 0);
			for (int remaining : remainings) {
				long delay = TimerSchedule.delayMillis(seconds, remaining);
				assertTrue("delay for " + seconds + "/" + remaining, delay > 0);
			}
		}
	}

	@Test
	public void anAbsurdDurationDoesNotOverflowIntoANegativeDelay() {
		// TimerDuration.toSeconds clamps to Integer.MAX_VALUE rather than rejecting,
		// so this value really can reach the scheduler. seconds * 1000 in int is
		// negative here; the answer must not be.
		long delay = TimerSchedule.delayMillis(Integer.MAX_VALUE, Integer.MAX_VALUE);
		assertEquals(Integer.MAX_VALUE * 1000L, delay);
		assertTrue(delay > 0);
	}

	@Test
	public void aRemainingLongerThanTheTimerIsIgnored() {
		// What an edit used to leave behind: duration cut from 30 s to 10 s with the
		// old remaining still stored, so play waited 30 s instead of 10.
		assertEquals(10, TimerSchedule.clampRemaining(10, 30));
		assertEquals(10000L, TimerSchedule.delayMillis(10, 30));
	}

	@Test
	public void aZeroLengthTimerBecomesTheShortestTheSchedulerAccepts() {
		// Three blank editor fields add to zero, and a zero period throws.
		assertEquals(1, TimerSchedule.normaliseSeconds(0));
		assertEquals(1000L, TimerSchedule.periodMillis(0));
		assertEquals(1000L, TimerSchedule.delayMillis(0, 0));
	}

	@Test
	public void resumingStampsTheStartWhereTheRunWouldHaveBegun() {
		long now = 1000000L;
		assertEquals("7 s left of 10 means 3 s already spent",
				now - 3000L, TimerSchedule.startStamp(now, 10, 7));
		assertEquals("a full remaining starts now", now, TimerSchedule.startStamp(now, 10, 10));
		assertEquals("a nonsense remaining starts now, not in the future",
				now, TimerSchedule.startStamp(now, 10, -3590));
	}

	@Test
	public void progressOfARunningTimerNeverGoesNegative() {
		// updateTimerProgress runs on a binder thread out of getTimers(); it used to
		// write this straight into TimerData with no clamp.
		assertEquals(0, TimerSchedule.remainingWhileRunning(10, 3600L * 1000L));
		assertEquals(4, TimerSchedule.remainingWhileRunning(10, 6000L));
		assertEquals(10, TimerSchedule.remainingWhileRunning(10, 0L));
		assertEquals(10, TimerSchedule.remainingWhileRunning(10, -1L));
	}
}
