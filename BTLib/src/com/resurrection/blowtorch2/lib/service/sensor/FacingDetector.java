package com.resurrection.blowtorch2.lib.service.sensor;

/**
 * Which way up the phone is, and when that counts as having been turned over.
 *
 * <p><b>Three rules, and all three are needed.</b> Gravity on the screen's axis
 * says up, down or neither ({@link DeviceState#classifyFacing}). A reading has
 * to hold for {@link #SETTLE_MILLIS} before it is believed, because turning a
 * phone over passes through every angle on the way and the trip would otherwise
 * fire halfway. And the first settled reading after the sensor is picked up is
 * where the phone was already lying, not something anybody did.
 *
 * <p><b>Why it is a class of its own.</b> {@code DeviceStateWatcher} runs this
 * in the service to fire real triggers, and the Sensors probe runs it in the UI
 * to tell the player whether their phone saw them. Written twice, the screen
 * that says "seen it" would say so at a moment when nothing fired — which is a
 * worse answer than no screen at all.
 *
 * <p>No Android types, so the rules above are tested rather than asserted. Not
 * thread-safe: each owner keeps its own and feeds it from one sensor callback.
 */
public final class FacingDetector {

	/**
	 * How long a side has to hold before it is believed, in milliseconds.
	 *
	 * <p>Measured against the gesture, not the hardware: putting a phone down
	 * is not a fast event, and a person turning one over takes longer than this.
	 */
	public static final long SETTLE_MILLIS = 400L;

	private String facing = DeviceState.UNKNOWN;
	private String candidate = DeviceState.UNKNOWN;
	private long candidateSince;
	private String gesture;

	/**
	 * Take a reading.
	 *
	 * @param z gravity along the screen's axis.
	 * @param nowMillis a monotonic clock; the caller's, so tests need no wait.
	 * @return true when the settled side changed, which is when
	 *         {@link #getFacing()} and {@link #getGesture()} are worth reading.
	 */
	public boolean accept(final float z, final long nowMillis) {
		gesture = null;
		String now = DeviceState.classifyFacing(z);
		if (!now.equals(candidate)) {
			candidate = now;
			candidateSince = nowMillis;
			return false;
		}
		if (now.equals(facing) || (nowMillis - candidateSince) < SETTLE_MILLIS) {
			return false;
		}
		String previous = facing;
		facing = now;
		// Settling out of unknown is the phone being noticed where it lay, not
		// a side anybody turned it to.
		if (!DeviceState.UNKNOWN.equals(previous)) {
			if (DeviceState.DOWN.equals(facing)) {
				gesture = "facedown";
			} else if (DeviceState.UP.equals(facing)) {
				gesture = "faceup";
			}
		}
		return true;
	}

	/** The settled side: {@code up}, {@code down} or {@code unknown}. */
	public String getFacing() {
		return facing;
	}

	/**
	 * The gesture the last {@link #accept} earned, or null.
	 *
	 * <p>Null is normal and means one of three things: nothing settled, it
	 * settled into neither side, or it was the first settling after the sensor
	 * was picked up.
	 */
	public String getGesture() {
		return gesture;
	}
}
