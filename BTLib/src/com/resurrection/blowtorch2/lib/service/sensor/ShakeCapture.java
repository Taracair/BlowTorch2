package com.resurrection.blowtorch2.lib.service.sensor;

import java.util.ArrayList;

/**
 * One shake, from the sample that crosses the hardness threshold until the
 * phone is quiet again. The quiet tail is dropped so it does not pull the
 * shape back to the origin. No Android types.
 */
public final class ShakeCapture {

	/**
	 * Gap before another shake can start, measured from the sample that opened
	 * the previous stroke. Play and the recorder both use {@link #mayStart}.
	 */
	public static final long BETWEEN_STROKES_MILLIS = 500L;
	/** How long the phone must stay under the loud floor before the stroke ends. */
	public static final long QUIET_MILLIS = 180L;
	/** A shake longer than this is closed even if it is still moving. */
	public static final long MAX_MILLIS = 750L;
	/** Fewer samples than this is a spike, not a shape. */
	public static final int MIN_SAMPLES = 4;

	private boolean open;
	private long startedAt;
	private long lastLoudAt;
	private final ArrayList<float[]> samples = new ArrayList<float[]>();

	/**
	 * @param magnitude movement in m/s², gravity already removed by the caller
	 * @return the finished stroke, or null while it is still open or too short
	 */
	public ShakeTrace add(final long nowMs, final float x, final float y, final float z,
			final float magnitude, final float shakeThreshold) {
		float loud = loudFloor(shakeThreshold);
		if (!open) {
			if (magnitude < shakeThreshold) {
				return null;
			}
			open = true;
			startedAt = nowMs;
			lastLoudAt = nowMs;
			samples.clear();
			samples.add(new float[] { x, y, z });
			return null;
		}
		samples.add(new float[] { x, y, z });
		if (magnitude >= loud) {
			lastLoudAt = nowMs;
		}
		boolean quiet = nowMs - lastLoudAt >= QUIET_MILLIS;
		boolean maxed = nowMs - startedAt >= MAX_MILLIS;
		if (!quiet && !maxed) {
			return null;
		}
		trimQuietTail(loud);
		ArrayList<float[]> kept = new ArrayList<float[]>(samples);
		reset();
		if (kept.size() < MIN_SAMPLES) {
			return null;
		}
		float[] xs = new float[kept.size()];
		float[] ys = new float[kept.size()];
		float[] zs = new float[kept.size()];
		for (int i = 0; i < kept.size(); i++) {
			float[] s = kept.get(i);
			xs[i] = s[0];
			ys[i] = s[1];
			zs[i] = s[2];
		}
		return new ShakeTrace(xs, ys, zs);
	}

	public boolean isOpen() {
		return open;
	}

	/**
	 * True when a new stroke may open. {@code previousStartMs} is the clock of
	 * the sample that opened the previous one, or negative when there is none.
	 * An already-open stroke keeps taking samples; this does not apply to it.
	 */
	public static boolean mayStart(final long nowMs, final long previousStartMs) {
		return previousStartMs < 0L
				|| nowMs - previousStartMs >= BETWEEN_STROKES_MILLIS;
	}

	public void reset() {
		open = false;
		startedAt = 0L;
		lastLoudAt = 0L;
		samples.clear();
	}

	static float loudFloor(final float shakeThreshold) {
		return Math.max(2.5f, shakeThreshold * 0.35f);
	}

	private void trimQuietTail(final float loud) {
		while (!samples.isEmpty()) {
			float[] s = samples.get(samples.size() - 1);
			float mag = (float) Math.sqrt((s[0] * s[0]) + (s[1] * s[1]) + (s[2] * s[2]));
			if (mag >= loud) {
				return;
			}
			samples.remove(samples.size() - 1);
		}
	}
}
