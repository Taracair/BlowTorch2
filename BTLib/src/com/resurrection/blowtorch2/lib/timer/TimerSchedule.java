package com.resurrection.blowtorch2.lib.timer;

/**
 * The arithmetic between a {@link TimerData} and {@code java.util.Timer}.
 *
 * <p>Extracted for the same reason {@link TimerDuration}, {@code AliasPattern} and
 * {@code TriggerPattern} were: {@code Plugin} cannot be tested on the JVM — it needs
 * {@code Handler}, {@code SystemClock} and a live scheduler — so any sum left inside it
 * is a sum nothing checks.
 *
 * <p>The sums here are not cosmetic. {@code Timer.schedule} throws
 * {@code IllegalArgumentException} on a negative delay or a non-positive period, and
 * {@code Plugin.startTimer} is reached from {@code ConnectionBinderFacade.startTimer},
 * which is a <em>synchronous</em> UI→service binder call with no {@code catch} anywhere
 * in the facade. An exception there is parcelled back and re-thrown in the UI process,
 * i.e. it kills the window. Two things used to feed it a negative number:
 *
 * <ul>
 * <li>{@code pauseTimer} stored {@code seconds - elapsed} unclamped. The task start is
 *     stamped with {@code elapsedRealtime()}, which counts through doze, while
 *     {@code java.util.Timer} does not fire while the device sleeps — so a 10 s repeat
 *     timer and an hour in a pocket left {@code remainingTime} at about -3590.
 * <li>{@code updateTimerProgress} wrote the same subtraction, and it runs on a binder
 *     thread out of {@code getTimers()} — opening the timer list was enough.
 * </ul>
 *
 * <p>So a stored remaining time is treated as untrusted here, and every value handed to
 * the scheduler is clamped at the point it is produced. A remaining time that is not a
 * sane fraction of the duration means the run is over rather than paused part-way, and a
 * fresh full-length run is the answer that cannot surprise the player.
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
