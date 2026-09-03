package com.resurrection.blowtorch2.lib.service.sensor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Phone state as {@code device.*} session variables. Unknown hardware leaves
 * the key absent, not {@code no}, so a moved profile does not fire by accident.
 */
public final class DeviceState {

	public static final String KEY_HEADPHONES = "device.headphones";
	public static final String KEY_CHARGING = "device.charging";
	public static final String KEY_BATTERY = "device.battery";
	public static final String KEY_SCREEN = "device.screen";
	public static final String KEY_COVERED = "device.covered";
	public static final String KEY_FACING = "device.facing";
	public static final String KEY_LIGHT = "device.light";

	public static final String UNKNOWN = "unknown";
	public static final String UP = "up";
	public static final String DOWN = "down";

	public static final String DARK = "dark";
	public static final String DIM = "dim";
	public static final String BRIGHT = "bright";

	public static final String YES = "yes";
	public static final String NO = "no";
	public static final String ON = "on";
	public static final String OFF = "off";

	private final LinkedHashMap<String, String> values = new LinkedHashMap<String, String>();

	/**
	 * Set one value.
	 *
	 * @return true when this changed something. Callers push to every live
	 *         connection, and pushing an unchanged value on every battery
	 *         broadcast would be a steady trickle of work for nothing.
	 */
	public boolean put(final String key, final String value) {
		if (key == null || value == null) {
			return false;
		}
		String old = values.put(key, value);
		return old == null || !old.equals(value);
	}

	public boolean setHeadphones(final boolean pluggedIn) {
		return put(KEY_HEADPHONES, pluggedIn ? YES : NO);
	}

	public boolean setCharging(final boolean charging) {
		return put(KEY_CHARGING, charging ? YES : NO);
	}

	public boolean setScreenOn(final boolean on) {
		return put(KEY_SCREEN, on ? ON : OFF);
	}

	public boolean setCovered(final boolean covered) {
		return put(KEY_COVERED, covered ? YES : NO);
	}

	/** Which way up the phone is lying, when it is lying flat enough to tell. */
	public boolean setFacing(final String facing) {
		return put(KEY_FACING, facing);
	}

	/** How light it is around the phone, in the three words a player would use. */
	public boolean setLight(final String level) {
		return put(KEY_LIGHT, level);
	}

	/**
	 * Which of the three words a reading falls into.
	 *
	 * <p>Two thresholds and not one, so there is a band in the middle that is
	 * neither: a room hovering on a single line would otherwise flip between dark
	 * and bright as a cloud went past, and every trigger gated on it would fire
	 * again and again.
	 *
	 * @param lux the sensor's reading.
	 * @param darkBelow at or under this it is dark.
	 * @param brightAbove at or over this it is bright.
	 */
	public static String classifyLight(final float lux, final float darkBelow,
			final float brightAbove) {
		if (lux <= darkBelow) {
			return DARK;
		}
		if (lux >= brightAbove) {
			return BRIGHT;
		}
		return DIM;
	}

	/**
	 * How much of gravity has to lie along the screen's axis before the phone
	 * counts as flat, in m/s² out of about 9.8.
	 *
	 * <p>Two thresholds out of one number rather than a single dividing line: a
	 * phone standing on edge, in a hand or in a pocket is neither face up nor
	 * face down, and a single line would have it flipping between the two all
	 * the way to the shop.
	 */
	public static final float FLAT_ENOUGH = 8.0f;

	/**
	 * Which way up the phone is, from gravity along the screen's axis.
	 *
	 * <p>Here rather than in the watcher because the Sensors probe classifies
	 * the same reading in the UI process, and two copies of this number would
	 * eventually disagree about what "face down" means.
	 *
	 * @param z gravity on the screen's axis: about +9.8 face up, -9.8 face down.
	 * @return {@link #UP}, {@link #DOWN} or {@link #UNKNOWN}.
	 */
	public static String classifyFacing(final float z) {
		if (z >= FLAT_ENOUGH) {
			return UP;
		}
		if (z <= -FLAT_ENOUGH) {
			return DOWN;
		}
		return UNKNOWN;
	}

	/** Battery as a whole percent. Out-of-range readings are ignored. */
	public boolean setBatteryPercent(final int level, final int scale) {
		if (scale <= 0 || level < 0) {
			return false;
		}
		int percent = (int) Math.round((level * 100.0) / scale);
		if (percent < 0 || percent > 100) {
			return false;
		}
		return put(KEY_BATTERY, Integer.toString(percent));
	}

	/**
	 * Whether something is over the proximity sensor.
	 *
	 * <p>Most devices report only two values, 0 and the sensor's maximum, so the
	 * useful test is "less than maximum" rather than any distance in
	 * centimetres. A threshold in cm would be a number invented at a desk and
	 * wrong on the next phone.
	 */
	public static boolean isCovered(final float reading, final float maximumRange) {
		if (maximumRange <= 0f) {
			return false;
		}
		return reading < maximumRange;
	}

	/** Every value currently known, in the order they were first set. */
	public Map<String, String> snapshot() {
		return new LinkedHashMap<String, String>(values);
	}

	public String get(final String key) {
		return values.get(key);
	}

	public boolean isEmpty() {
		return values.isEmpty();
	}

	/** Every name, what it can hold, and what it holds now. */
	private static final String[][] CATALOGUE = {
		{KEY_FACING, "up | down | unknown", "which way up it is lying"},
		{KEY_SCREEN, "on | off", "the display"},
		{KEY_HEADPHONES, "yes | no", "the headphone jack"},
		{KEY_CHARGING, "yes | no", "the charger"},
		{KEY_BATTERY, "0 to 100", "charge, as text holding a number"},
		{KEY_COVERED, "yes | no", "something over the proximity sensor"},
		{KEY_LIGHT, "dark | dim | bright", "how light the room is"},
	};

	/**
	 * The reading as {@code .sensor state} prints it.
	 *
	 * <p>Every name is listed whether it is set or not, with what it can hold.
	 * A name that is missing from a bare list of current values is the single
	 * most confusing thing here — it means "this phone cannot tell", and a
	 * condition on it is false — so the list is the catalogue, not the contents.
	 */
	public String report() {
		StringBuilder out = new StringBuilder();
		out.append("\n--- what the phone knows ---\n");
		for (int i = 0; i < CATALOGUE.length; i++) {
			String key = CATALOGUE[i][0];
			String held = values.get(key);
			out.append(String.format(java.util.Locale.US, "  %-18s %-16s %s%n",
					key, CATALOGUE[i][1],
					held == null ? "(not set — " + CATALOGUE[i][2] + ")" : "= " + held));
		}
		if (values.isEmpty()) {
			out.append("\nNothing is being watched, so none of these are set.\n");
			out.append("Turn it on with .sensor watch on, or Options \u2192 Device \u2192\n");
			out.append("\"Device state as variables\".\n");
			return out.toString();
		}
		out.append("\nEvery value is text, including the battery — conditions compare it\n");
		out.append("exactly. \"device.battery equals 74\" works; \"below 30\" needs a Lua\n");
		out.append("script, because there is no less-than in a condition.\n");
		out.append("\nUse them in a trigger's or timer's Conditions tab: the type is\n");
		out.append("\"Variable equals\", and the picker at the top of that screen fills\n");
		out.append("both fields for you. A name shown as not set means this phone cannot\n");
		out.append("tell, and a condition on it is false rather than true.\n");
		return out.toString();
	}
}
