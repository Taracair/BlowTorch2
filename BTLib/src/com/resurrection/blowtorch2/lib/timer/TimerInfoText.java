package com.resurrection.blowtorch2.lib.timer;

/**
 * Player-facing status for {@code .timer info} / {@code .timer dump}.
 *
 * <p>Numbers stay in whole seconds internally; {@link TimerDuration#format} is the
 * hours/minutes/seconds face. Stage is derived from the scheduler's running flag
 * and the stored remaining time, not from the {@code playing} field, which can
 * be stale.
 */
public final class TimerInfoText {

	private TimerInfoText() {
	}

	/**
	 * @param name Timer name as the player typed it.
	 * @param seconds Stored duration.
	 * @param remaining Seconds left in this run (already clamped by the caller).
	 * @param running Whether a scheduler entry exists right now.
	 * @param repeat Whether it starts again when it fires.
	 */
	public static String describe(final String name, final int seconds,
			final int remaining, final boolean running, final boolean repeat) {
		int full = seconds < 0 ? 0 : seconds;
		int left = remaining;
		if (left < 0) {
			left = 0;
		}
		if (left > full) {
			left = full;
		}

		String stage;
		int elapsed;
		if (running) {
			stage = "running";
			elapsed = full - left;
		} else if (left > 0 && left < full) {
			stage = "paused";
			elapsed = full - left;
		} else {
			stage = "stopped";
			left = full;
			elapsed = 0;
		}

		String repeatWord = repeat
				? "repeats when it fires"
				: "one-shot (stops after it fires)";

		StringBuilder sb = new StringBuilder();
		sb.append("Timer ").append(name == null ? "" : name).append('\n');
		sb.append("  State: ").append(stage).append(" — ").append(repeatWord).append('\n');
		sb.append("  Set for: ").append(TimerDuration.format(full)).append('\n');
		if ("stopped".equals(stage)) {
			sb.append("  Elapsed: none (not running)\n");
			sb.append("  Remaining: ").append(TimerDuration.format(full)).append(" (full)");
		} else {
			sb.append("  Elapsed: ").append(TimerDuration.format(elapsed)).append('\n');
			sb.append("  Remaining: ").append(TimerDuration.format(left));
		}
		return sb.toString();
	}
}
