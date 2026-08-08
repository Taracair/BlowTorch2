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

	/** The reading as {@code .probe sensors state} prints it. */
	public String report() {
		StringBuilder out = new StringBuilder();
		out.append("\n--- device state ---\n");
		if (values.isEmpty()) {
			out.append("Nothing is being watched. Turn on \"Device state as variables\"\n");
			out.append("in Settings, or nothing here will ever be set.\n");
			return out.toString();
		}
		for (Map.Entry<String, String> e : values.entrySet()) {
			out.append("  ").append(e.getKey()).append(" = ").append(e.getValue()).append('\n');
		}
		out.append("\nA name missing from this list is a thing this phone cannot tell,\n");
		out.append("and a condition testing it is false — not true. Use these in a\n");
		out.append("trigger's Conditions tab, or from Lua with GetVariable.\n");
		return out.toString();
	}
}
