package com.resurrection.blowtorch2.lib.timer;

/**
 * Delay and period for {@code java.util.Timer}, clamped before they leave here.
 * A negative value on the binder ({@code Plugin.startTimer}) kills the UI.
 * Remaining time is untrusted: {@code elapsedRealtime()} counts through doze,
 * {@code Timer} does not, so pause can store a large negative.
 */
public final class TimerSchedule {

	/** Milliseconds in a second. */
	private static final long MILLIS_PER_SECOND = 1000L;

	/** Shortest timer the scheduler will accept: the period must be positive. */
	public static final int MIN_SECONDS = 1;

	private TimerSchedule() {
	}

	/**
	 * A timer's length, made usable.
	 *
	 * <p>Zero is reachable — {@code TimerDuration.toSeconds} adds three blank editor
	 * fields to 0 — and a zero period is an {@code IllegalArgumentException}.
	 *
	 * @param seconds The stored duration.
	 * @return The same value, at least {@link #MIN_SECONDS}.
	 */
	public static int normaliseSeconds(final int seconds) {
		return seconds < MIN_SECONDS ? MIN_SECONDS : seconds;
	}

	/**
	 * The remaining time to actually schedule against.
	 *
	 * @param seconds The timer's full duration.
	 * @param remaining The stored remaining time, trusted only if it is inside the run.
	 * @return {@code remaining} when it is in (0, seconds]; the full duration otherwise,
	 *     which is also the "not paused" value the rest of the code tests for.
	 */
	public static int clampRemaining(final int seconds, final int remaining) {
		int full = normaliseSeconds(seconds);
		if (remaining <= 0 || remaining > full) {
			return full;
		}
		return remaining;
	}

	/**
	 * What is left after a pause.
	 *
	 * @param seconds The timer's full duration.
	 * @param elapsedMillis How long the current run had been going.
	 * @return Seconds left, never negative and never more than the duration.
	 */
	public static int remainingAfterPause(final int seconds, final long elapsedMillis) {
		int full = normaliseSeconds(seconds);
		if (elapsedMillis < 0) {
			return full;
		}
		long left = full - (elapsedMillis / MILLIS_PER_SECOND);
		if (left <= 0) {
			// Overran: the run is finished, so the next play starts a whole one.
			return full;
		}
		return clampRemaining(full, (int) left);
	}

	/**
	 * Delay to hand to {@code Timer.schedule}.
	 *
	 * <p>{@code long} throughout: {@code seconds * 1000} in {@code int} overflows at
	 * about 24.8 days, and {@code TimerDuration.toSeconds} clamps to
	 * {@code Integer.MAX_VALUE} rather than rejecting, so an absurd hours entry used to
	 * arrive here as a negative delay.
	 *
	 * @param seconds The timer's full duration.
	 * @param remaining The stored remaining time.
	 * @return Milliseconds, always positive.
	 */
	public static long delayMillis(final int seconds, final int remaining) {
		return (long) clampRemaining(seconds, remaining) * MILLIS_PER_SECOND;
	}

	/**
	 * Period to hand to {@code Timer.schedule} for a repeating timer.
	 *
	 * @param seconds The timer's full duration.
	 * @return Milliseconds, always positive.
	 */
	public static long periodMillis(final int seconds) {
		return (long) normaliseSeconds(seconds) * MILLIS_PER_SECOND;
	}

	/**
	 * The start stamp to record so progress reads correctly for a resumed run.
	 *
	 * @param nowMillis {@code SystemClock.elapsedRealtime()}.
	 * @param seconds The timer's full duration.
	 * @param remaining The stored remaining time.
	 * @return The instant the run would have begun had it never been paused.
	 */
	public static long startStamp(final long nowMillis, final int seconds, final int remaining) {
		int left = clampRemaining(seconds, remaining);
		return nowMillis - ((long) (normaliseSeconds(seconds) - left) * MILLIS_PER_SECOND);
	}

	/**
	 * Progress for a running timer, for the list rows.
	 *
	 * @param seconds The timer's full duration.
	 * @param elapsedMillis How long the current run has been going.
	 * @return Seconds left, never negative and never more than the duration.
	 */
	public static int remainingWhileRunning(final int seconds, final long elapsedMillis) {
		int full = normaliseSeconds(seconds);
		if (elapsedMillis < 0) {
			return full;
		}
		long left = full - (elapsedMillis / MILLIS_PER_SECOND);
		if (left < 0) {
			// Overdue rather than negative: the scheduler has not caught up yet.
			return 0;
		}
		return left > full ? full : (int) left;
	}
}
