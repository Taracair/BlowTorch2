package com.resurrection.blowtorch2.lib.service.sensor;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * How hard this phone has to be shaken, as measured on this phone.
 *
 * <p><b>Kept out of the world profile on purpose.</b> Profiles are exported and
 * swapped between players — the pair store already rides along with a world — so
 * a threshold measured on one device travelling to another would mean a reading
 * that never fires, or one that fires in a pocket. Calibration belongs to the
 * device. What a profile carries is "this trigger answers to a shake", never
 * "at 14.2 m/s²".
 *
 * <p>The preference file is still called {@code bt_gesture_tuning}. It is
 * private to the app, never exported and never shown, so renaming it would buy
 * nothing and would throw away a calibration the player measured by hand.
 *
 * <p>Written and read in the service process, which is where the detector runs,
 * so there is no cross-process preference sharing to get wrong.
 */
public final class GestureTuning {

	private static final String PREFS = "bt_gesture_tuning";
	private static final String KEY_SHAKE = "shake_threshold";
	private static final String KEY_DARK = "light_dark_below";
	private static final String KEY_BRIGHT = "light_bright_above";
	private static final String KEY_BATTERY_LOW = "battery_low";
	private static final String KEY_BATTERY_RECOVER = "battery_recover";

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

	/**
	 * Charge at or under this percent is low; at or over the other it has
	 * recovered. The five-point gap is the band that stops 19–21% flapping.
	 *
	 * <p>Percent is already a percent, unlike lux, so these are usable starting
	 * values rather than a guess at someone else's room. Options → Device →
	 * Battery low threshold replaces them. Kept with this phone, never exported.
	 */
	public static final int DEFAULT_BATTERY_LOW = 20;
	public static final int DEFAULT_BATTERY_RECOVER = 35;

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

	/**
	 * Low 1–90, recover low+5–100. A recover sitting on low is the flap the
	 * gap exists to prevent; these bounds are what {@code .sensor threshold
	 * battery} and the Options dialog both store.
	 */
	public static int[] clampBatteryThresholds(final int low, final int recover) {
		int lo = Math.max(1, Math.min(90, low));
		int rec = Math.max(lo + 5, Math.min(100, recover));
		return new int[] { lo, rec };
	}

	public static int batteryLow(final Context context) {
		return batteryThresholds(context)[0];
	}

	public static int batteryRecover(final Context context) {
		return batteryThresholds(context)[1];
	}

	/** The pair actually in force, already clamped. */
	public static int[] batteryThresholds(final Context context) {
		return clampBatteryThresholds(
				readInt(context, KEY_BATTERY_LOW, DEFAULT_BATTERY_LOW),
				readInt(context, KEY_BATTERY_RECOVER, DEFAULT_BATTERY_RECOVER));
	}

	/**
	 * Store both battery thresholds at once.
	 *
	 * @return the pair actually stored, after clamping.
	 */
	public static int[] setBatteryThresholds(final Context context, final int low,
			final int recover) {
		int[] stored = clampBatteryThresholds(low, recover);
		if (context == null) {
			return stored;
		}
		try {
			context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
					.putInt(KEY_BATTERY_LOW, stored[0])
					.putInt(KEY_BATTERY_RECOVER, stored[1]).apply();
		} catch (Exception ignored) {
		}
		return stored;
	}

	private static int readInt(final Context context, final String key,
			final int fallback) {
		if (context == null) {
			return fallback;
		}
		try {
			return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
					.getInt(key, fallback);
		} catch (Exception e) {
			return fallback;
		}
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
