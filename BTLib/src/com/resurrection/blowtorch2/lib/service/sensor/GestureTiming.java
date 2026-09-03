package com.resurrection.blowtorch2.lib.service.sensor;

/**
 * Wave vs cover from how long proximity stayed near. Shared by the service
 * detector and the UI probe so they agree.
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
