package com.resurrection.blowtorch2.lib.service.sensor;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * How hard this phone has to be shaken, as measured on this phone.
 *
 * <p><b>Kept out of the world profile on purpose.</b> Profiles are exported and
 * swapped between players — the pair store already rides along with a world — so
 * a threshold measured on one device travelling to another would mean a gesture
 * that never fires, or one that fires in a pocket. Calibration belongs to the
 * device. What a profile carries is "this trigger answers to a shake", never
 * "at 14.2 m/s²".
 *
 * <p>Written and read in the service process, which is where the detector runs,
 * so there is no cross-process preference sharing to get wrong.
 */
public final class GestureTuning {

	private static final String PREFS = "bt_gesture_tuning";
	private static final String KEY_SHAKE = "shake_threshold";

	/**
	 * Shipped starting value, in m/s².
	 *
	 * <p>Measured on a Pixel 9a on 8 Aug 2026 — a shake peaked at 27.7 and a
	 * quiet baseline at 10.4 — but both runs were done sitting at a desk, so this
	 * is a starting point and not a fact about anybody else's phone. Options →
	 * Device → Calibrate shake replaces it with a real measurement.
	 */
	public static final float DEFAULT_SHAKE = 15.0f;

	private GestureTuning() {
	}

	public static float shakeThreshold(final Context context) {
		if (context == null) {
			return DEFAULT_SHAKE;
		}
		try {
			SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
			return prefs.getFloat(KEY_SHAKE, DEFAULT_SHAKE);
		} catch (Exception e) {
			return DEFAULT_SHAKE;
		}
	}

	/** @return the value actually stored, clamped to something usable. */
	public static float setShakeThreshold(final Context context, final float value) {
		float clamped = Math.max(2.0f, Math.min(60.0f, value));
		if (context == null) {
			return clamped;
		}
		try {
			context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
					.putFloat(KEY_SHAKE, clamped).apply();
		} catch (Exception ignored) {
			// A phone that cannot keep this keeps the default, which still works.
		}
		return clamped;
	}

	/** Back to the shipped value. */
	public static void clear(final Context context) {
		if (context == null) {
			return;
		}
		try {
			context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
					.remove(KEY_SHAKE).apply();
		} catch (Exception ignored) {
		}
	}
}
