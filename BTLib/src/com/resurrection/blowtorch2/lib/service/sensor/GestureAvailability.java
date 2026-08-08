package com.resurrection.blowtorch2.lib.service.sensor;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.resurrection.blowtorch2.lib.service.sensor.GestureCatalog.Gesture;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;

/**
 * Which gestures this particular phone can actually do, and with what.
 *
 * <p>Sensor hardware is the least uniform thing about Android. This resolves
 * each gesture against the sensors that are really present, in the order the
 * catalogue prefers, and reports the answer in words a player can act on: it
 * works, it works by a fallback, or it cannot work here and why. A settings
 * screen showing a gesture this phone cannot do is worse than not offering it —
 * the player configures it, nothing ever happens, and the app looks broken.
 *
 * <p>Nothing is registered here. Asking what exists is free; listening is not.
 */
public final class GestureAvailability {

	/** What a gesture resolved to on this device. */
	public static final class Resolution {
		private final Gesture gesture;
		private final String provider;
		private final Sensor sensor;
		private final boolean fallback;

		Resolution(final Gesture gesture, final String provider, final Sensor sensor,
				final boolean fallback) {
			this.gesture = gesture;
			this.provider = provider;
			this.sensor = sensor;
			this.fallback = fallback;
		}

		public Gesture getGesture() {
			return gesture;
		}

		/** True when some sensor on this device can provide the gesture. */
		public boolean isAvailable() {
			return sensor != null || GestureCatalog.BY_SYSTEM.equals(provider);
		}

		/** True when the first choice was missing and a second one is doing it. */
		public boolean isFallback() {
			return fallback;
		}

		/** The catalogue's name for how it is measured, or null. */
		public String getProvider() {
			return provider;
		}

		public Sensor getSensor() {
			return sensor;
		}

		/** Whether events keep coming with the display asleep. */
		public boolean isWakeUp() {
			return GestureCatalog.BY_SYSTEM.equals(provider)
					|| (sensor != null && sensor.isWakeUpSensor());
		}

		/** One line for the player: what happens, and why if it does not. */
		public String describe() {
			if (GestureCatalog.BY_SYSTEM.equals(provider)) {
				return "a system event — works on every phone";
			}
			if (sensor == null) {
				return "not available — this phone has no "
						+ describeProviders(gesture) + " sensor";
			}
			StringBuilder out = new StringBuilder();
			out.append(sensor.getName());
			if (fallback) {
				out.append(" (fallback)");
			}
			if (!isWakeUp()) {
				out.append(", needs the screen on");
			}
			return out.toString();
		}
	}

	private GestureAvailability() {
	}

	/** Resolve every gesture in the catalogue against this device. */
	public static Map<String, Resolution> resolveAll(final Context context) {
		SensorManager manager = managerFrom(context);
		LinkedHashMap<String, Resolution> out = new LinkedHashMap<String, Resolution>();
		for (Gesture g : GestureCatalog.all()) {
			out.put(g.getId(), resolve(manager, g));
		}
		return out;
	}

	/** Resolve one gesture, or an unavailable answer when nothing provides it. */
	public static Resolution resolve(final Context context, final Gesture gesture) {
		return resolve(managerFrom(context), gesture);
	}

	private static Resolution resolve(final SensorManager manager, final Gesture gesture) {
		if (gesture == null) {
			return new Resolution(null, null, null, false);
		}
		// A system event needs no hardware, so there is nothing to resolve and
		// nothing that can be missing.
		if (gesture.getProviders().contains(GestureCatalog.BY_SYSTEM)) {
			return new Resolution(gesture, GestureCatalog.BY_SYSTEM, null, false);
		}
		if (manager == null) {
			return new Resolution(gesture, null, null, false);
		}
		int index = 0;
		for (String provider : gesture.getProviders()) {
			int type = sensorTypeFor(provider);
			Sensor sensor = type == 0 ? null : manager.getDefaultSensor(type);
			if (sensor != null) {
				return new Resolution(gesture, provider, sensor, index > 0);
			}
			index++;
		}
		return new Resolution(gesture, null, null, false);
	}

	/**
	 * The platform sensor a catalogue provider name means.
	 *
	 * <p>The mapping lives here rather than in the catalogue so the catalogue
	 * stays free of Android and can be tested on the JVM.
	 */
	public static int sensorTypeFor(final String provider) {
		if (GestureCatalog.BY_PROXIMITY.equals(provider)) {
			return Sensor.TYPE_PROXIMITY;
		}
		if (GestureCatalog.BY_LIGHT.equals(provider)) {
			return Sensor.TYPE_LIGHT;
		}
		if (GestureCatalog.BY_LINEAR_ACCELERATION.equals(provider)) {
			return Sensor.TYPE_LINEAR_ACCELERATION;
		}
		if (GestureCatalog.BY_ACCELEROMETER.equals(provider)) {
			return Sensor.TYPE_ACCELEROMETER;
		}
		if (GestureCatalog.BY_GRAVITY.equals(provider)) {
			return Sensor.TYPE_GRAVITY;
		}
		return 0;
	}

	/** The whole picture, as {@code .sensor caps} prints it. */
	public static String report(final Context context) {
		Map<String, Resolution> resolved = resolveAll(context);
		StringBuilder out = new StringBuilder();
		out.append("\n--- gestures on this phone ---\n");
		for (Map.Entry<String, Resolution> e : resolved.entrySet()) {
			Resolution r = e.getValue();
			out.append(String.format(Locale.US, "  %-14s %-9s %s%n",
					e.getKey(), r.isAvailable() ? "OK" : "MISSING", r.describe()));
		}
		out.append("\nA gesture is set up like any other trigger: give it actions and\n");
		out.append("it can send a command, run a script, speak, play a sound — the same\n");
		out.append("list a trigger on game text has. Use .sensor wave <command> for the\n");
		out.append("quick way, or the Triggers editor for anything more.\n");
		return out.toString();
	}

	private static String describeProviders(final Gesture gesture) {
		if (gesture == null) {
			return "suitable";
		}
		StringBuilder out = new StringBuilder();
		for (String p : gesture.getProviders()) {
			if (out.length() > 0) {
				out.append(" or ");
			}
			out.append(p);
		}
		return out.toString();
	}

	private static SensorManager managerFrom(final Context context) {
		if (context == null) {
			return null;
		}
		Object service = context.getSystemService(Context.SENSOR_SERVICE);
		return (service instanceof SensorManager) ? (SensorManager) service : null;
	}
}
