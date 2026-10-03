package com.resurrection.blowtorch2.lib.service.sensor;

/**
 * Per-world switches that live in the connection profile. The Sensors list
 * draws them; Options hides the rows so they are not offered twice.
 */
public final class SensorWorldFlags {

	/** False silences every reading on the Sensors list in this world. Default on. */
	public static final String ENABLED = "sensors_enabled";

	/**
	 * When on, shake left/right/up/down and {@code pat:} patterns do not fire
	 * here. Recorded shakes may. Plain {@code shake} keeps its own trigger.
	 */
	public static final String MY_SHAKES = "sensor_my_shakes";

	private SensorWorldFlags() {
	}
}
