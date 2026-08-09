package com.resurrection.blowtorch2.lib.service.sensor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What the phone is doing, as session variables a trigger can already read.
 *
 * <p><b>Why this shape.</b> Conditions on triggers and timers already resolve
 * {@code variableEquals} against the connection's session variables — see
 * {@code ConditionEvaluator}. So the cheapest useful half of "sensors" is not a
 * new kind of trigger at all: it is writing the phone's state into the variables
 * that already gate everything. "Only shout when the phone is face up" then
 * costs one condition the player can add today, and Lua reads the same values
 * through {@code GetVariable}.
 *
 * <p>Names are prefixed {@code device.} so they cannot collide with a player's
 * own variables. A value that this device cannot know is <b>absent</b>, not
 * {@code no}: a phone with no proximity sensor never sets {@code device.covered},
 * and a condition testing it is false rather than quietly true. That asymmetry
 * is deliberate and it is what makes a profile safe to move between phones.
 *
 * <p>No Android types here on purpose — this is the half that can be tested.
 */
public final class DeviceState {

	public static final String KEY_HEADPHONES = "device.headphones";
	public static final String KEY_CHARGING = "device.charging";
	public static final String KEY_BATTERY = "device.battery";
	public static final String KEY_SCREEN = "device.screen";
	public static final String KEY_COVERED = "device.covered";
	public static final String KEY_FACING = "device.facing";

	public static final String UNKNOWN = "unknown";
	public static final String UP = "up";
	public static final String DOWN = "down";

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
