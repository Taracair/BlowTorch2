package com.resurrection.blowtorch2.lib.service.sensor;

/**
 * Portrait ↔ landscape as a {@code BY_SYSTEM} gesture. Values match
 * {@code Configuration.ORIENTATION_PORTRAIT} (1) and {@code LANDSCAPE} (2)
 * so this half stays testable without a configuration object.
 *
 * <p>The first reading is never a gesture (the phone already was that way).
 * Square / undefined orientations are ignored.
 */
public final class OrientationGesture {

	/** Same as {@code Configuration.ORIENTATION_PORTRAIT}. */
	public static final int PORTRAIT = 1;
	/** Same as {@code Configuration.ORIENTATION_LANDSCAPE}. */
	public static final int LANDSCAPE = 2;

	public static final String ID_LANDSCAPE = "landscape";
	public static final String ID_PORTRAIT = "portrait";

	private OrientationGesture() {
	}

	/**
	 * The gesture for this change, or null when nothing should fire (same
	 * orientation, or not portrait/landscape).
	 */
	public static String idForChange(int previous, int next) {
		if (next != PORTRAIT && next != LANDSCAPE) {
			return null;
		}
		if (previous == next) {
			return null;
		}
		return next == LANDSCAPE ? ID_LANDSCAPE : ID_PORTRAIT;
	}
}
