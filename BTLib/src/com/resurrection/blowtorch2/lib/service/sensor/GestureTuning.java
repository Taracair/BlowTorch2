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
	private static final String KEY_DARK = "light_dark_below";
	private static final String KEY_BRIGHT = "light_bright_above";

	/**
	 * Shipped starting value, in m/s².
	 *
	 * <p>Measured on a Pixel 9a on 8 Aug 2026 — a shake peaked at 27.7 and a
	 * quiet baseline at 10.4 — but both runs were done sitting at a desk, so this
	 * is a starting point and not a fact about anybody else's phone. Options →
	 * Device → Calibrate shake replaces it with a real measurement.
	 */
	public static final float DEFAULT_SHAKE = 15.0f;

	/**
	 * At or below this many lux it is dark; at or above the other it is bright.
	 *
	 * <p>Measured on one Pixel 9a on 9 Aug 2026: an unlit room read 0 and an
	 * ordinary lit room 150 to 350, with no outdoor run. So "dark" is confident
	 * and "bright" is a guess at daylight — which is exactly why Options → Device
	 * → Calibrate light exists, and why these are starting values rather than
	 * anything to build a default on.
	 */
	public static final float DEFAULT_DARK_BELOW = 10.0f;
	public static final float DEFAULT_BRIGHT_ABOVE = 1000.0f;

	private GestureTuning() {
	}

	public static float darkBelow(final Context context) {
		return readFloat(context, KEY_DARK, DEFAULT_DARK_BELOW);
	}

	public static float brightAbove(final Context context) {
		return readFloat(context, KEY_BRIGHT, DEFAULT_BRIGHT_ABOVE);
	}

	/**
	 * Store both light thresholds at once.
	 *
	 * <p>Together, because they only mean anything as a pair: a "bright" under a
	 * "dark" would put every reading in one bucket or the other with no middle,
	 * which is the flapping the middle band exists to prevent.
	 */
	public static void setLightThresholds(final Context context, final float darkBelow,
			final float brightAbove) {
		float dark = Math.max(0f, darkBelow);
		float bright = Math.max(dark + 1f, brightAbove);
		if (context == null) {
			return;
		}
		try {
			context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
					.putFloat(KEY_DARK, dark).putFloat(KEY_BRIGHT, bright).apply();
		} catch (Exception ignored) {
		}
	}

	private static float readFloat(final Context context, final String key,
			final float fallback) {
		if (context == null) {
			return fallback;
		}
		try {
			return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
					.getFloat(key, fallback);
		} catch (Exception e) {
			return fallback;
		}
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
