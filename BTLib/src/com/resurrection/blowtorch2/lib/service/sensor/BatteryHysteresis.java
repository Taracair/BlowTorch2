package com.resurrection.blowtorch2.lib.service.sensor;

/**
 * When charge <em>crosses</em> a line, not every percent along the way.
 *
 * <p>One threshold would flap: 21% → 19% → 21% would fire low, ok, low. Two
 * states and a gap between them — low at or under 20, recovered at or over 35
 * by default — mean sitting at 18 then 19 does nothing, and oscillating 19–21
 * stays low until charge actually climbs out.
 *
 * <p>The first reading is where the phone already was, never a fire. Same hole
 * as the sticky charger broadcast: treating "already at 15%" as "just went low"
 * would run the player's command every time the watcher is picked up.
 *
 * <p>No Android types: the watcher feeds it a percent, tests feed it a percent.
 */
public final class BatteryHysteresis {

	public static final String UNKNOWN = "unknown";
	public static final String LOW = "low";
	public static final String OK = "ok";

	public static final String FIRE_LOW = "batterylow";
	public static final String FIRE_OK = "batteryok";

	private int lowAt;
	private int recoverAt;
	private String band = UNKNOWN;

	public BatteryHysteresis() {
		this(GestureTuning.DEFAULT_BATTERY_LOW, GestureTuning.DEFAULT_BATTERY_RECOVER);
	}

	public BatteryHysteresis(final int lowAt, final int recoverAt) {
		this.lowAt = lowAt;
		this.recoverAt = recoverAt;
	}

	/** New numbers, same band. A calibration is not a crossing. */
	public void setThresholds(final int lowAt, final int recoverAt) {
		this.lowAt = lowAt;
		this.recoverAt = recoverAt;
	}

	/** Forget the band so the next percent is a seed again. */
	public void reset() {
		band = UNKNOWN;
	}

	/**
	 * Where the phone already is. Never a fire — used when a sticky broadcast
	 * restates the current charge, or when the watcher already knew the percent
	 * before anyone asked for these gestures.
	 */
	public void seed(final int percent) {
		band = percent <= lowAt ? LOW : OK;
	}

	/** {@link #seed} only while still unknown. */
	public void seedIfUnknown(final int percent) {
		if (UNKNOWN.equals(band)) {
			seed(percent);
		}
	}

	/**
	 * Take a percent.
	 *
	 * @return {@link #FIRE_LOW}, {@link #FIRE_OK}, or null when nothing crossed.
	 */
	public String observe(final int percent) {
		if (UNKNOWN.equals(band)) {
			seed(percent);
			return null;
		}
		if (OK.equals(band) && percent <= lowAt) {
			band = LOW;
			return FIRE_LOW;
		}
		if (LOW.equals(band) && percent >= recoverAt) {
			band = OK;
			return FIRE_OK;
		}
		return null;
	}

	/** {@link #LOW}, {@link #OK} or {@link #UNKNOWN}. */
	public String getBand() {
		return band;
	}
}
