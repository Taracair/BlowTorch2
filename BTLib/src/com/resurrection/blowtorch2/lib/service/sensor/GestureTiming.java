package com.resurrection.blowtorch2.lib.service.sensor;

/**
 * Telling a wave from a cover, which is a question about time and nothing else.
 *
 * <p>A proximity sensor reports "near" and "far" and very little in between, so
 * there is no second reading to distinguish two gestures with. What is left is
 * how long the hand stayed: gone again quickly is a wave, still there after a
 * moment is a deliberate cover.
 *
 * <p><b>Why these numbers live here.</b> {@code DeviceStateWatcher} detects the
 * pair in the service process, and the Sensors probe shows the player what the
 * sensor is doing in the UI process. Two copies of "how long is a wave" would
 * drift, and the screen that tells you your wave was seen would then disagree
 * with the thing that actually fires the trigger.
 *
 * <p>No Android types: this half is testable on the JVM.
 */
public final class GestureTiming {

	/** How long a hand must stay put before it counts as a deliberate cover. */
	public static final long COVER_MILLIS = 1000L;

	/**
	 * Anything shorter than a cover is a wave. Deliberately the same number:
	 * with a gap between the two, a hand held for exactly that long would be
	 * neither, and the gesture would fail intermittently for no visible reason.
	 */
	public static final long WAVE_MAX_MILLIS = COVER_MILLIS;

	private GestureTiming() {
	}

	/**
	 * What a hand that has just left the sensor was.
	 *
	 * <p>Only answers for a hand that has gone again. A hand still in place is
	 * a cover once {@link #COVER_MILLIS} have passed, and that is decided by
	 * waiting rather than by asking.
	 *
	 * @param heldMillis how long the sensor read near.
	 * @return the gesture id, or null when nothing was covered in the first
	 *         place or it stayed long enough to have already been a cover.
	 */
	public static String classifyRelease(final long heldMillis) {
		if (heldMillis < 0L) {
			return null;
		}
		return heldMillis <= WAVE_MAX_MILLIS ? "wave" : null;
	}
}
