package com.resurrection.blowtorch2.lib.service.sensor;

/**
 * One recorded shake as linear-acceleration samples, in m/s².
 * +X is to the right of the screen, +Y toward the top, +Z toward the player.
 * No Android types.
 */
public final class ShakeTrace {

	private final float[] x;
	private final float[] y;
	private final float[] z;

	public ShakeTrace(final float[] x, final float[] y, final float[] z) {
		int n = x == null ? 0 : x.length;
		if (y == null || z == null || y.length != n || z.length != n) {
			throw new IllegalArgumentException("shake trace axes differ in length");
		}
		this.x = new float[n];
		this.y = new float[n];
		this.z = new float[n];
		System.arraycopy(x, 0, this.x, 0, n);
		System.arraycopy(y, 0, this.y, 0, n);
		System.arraycopy(z, 0, this.z, 0, n);
	}

	public int size() {
		return x.length;
	}

	public float x(final int i) {
		return x[i];
	}

	public float y(final int i) {
		return y[i];
	}

	public float z(final int i) {
		return z[i];
	}

	/**
	 * Evenly spaced along the 3D chord, including both ends. A trace that does
	 * not move comes back as repeated copies of its first sample.
	 */
	public ShakeTrace resampled(final int points) {
		if (points < 2) {
			throw new IllegalArgumentException("need at least two points");
		}
		int n = x.length;
		float[] ox = new float[points];
		float[] oy = new float[points];
		float[] oz = new float[points];
		if (n == 0) {
			return new ShakeTrace(ox, oy, oz);
		}
		if (n == points) {
			return this;
		}
		double[] cum = new double[n];
		for (int i = 1; i < n; i++) {
			double dx = x[i] - x[i - 1];
			double dy = y[i] - y[i - 1];
			double dz = z[i] - z[i - 1];
			cum[i] = cum[i - 1] + Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
		}
		double total = cum[n - 1];
		if (total < 1e-4) {
			for (int p = 0; p < points; p++) {
				ox[p] = x[0];
				oy[p] = y[0];
				oz[p] = z[0];
			}
			return new ShakeTrace(ox, oy, oz);
		}
		int seg = 1;
		for (int p = 0; p < points; p++) {
			double target = total * p / (double) (points - 1);
			while (seg < n - 1 && cum[seg] < target) {
				seg++;
			}
			double span = cum[seg] - cum[seg - 1];
			float t = span < 1e-6 ? 0f : (float) ((target - cum[seg - 1]) / span);
			if (t < 0f) {
				t = 0f;
			} else if (t > 1f) {
				t = 1f;
			}
			ox[p] = x[seg - 1] + (t * (x[seg] - x[seg - 1]));
			oy[p] = y[seg - 1] + (t * (y[seg] - y[seg - 1]));
			oz[p] = z[seg - 1] + (t * (z[seg] - z[seg - 1]));
		}
		return new ShakeTrace(ox, oy, oz);
	}

	/** Centroid at the origin, loudest point at distance 1. A still trace stays at 0. */
	public ShakeTrace normalized() {
		int n = x.length;
		float[] ox = new float[n];
		float[] oy = new float[n];
		float[] oz = new float[n];
		if (n == 0) {
			return new ShakeTrace(ox, oy, oz);
		}
		float mx = 0f;
		float my = 0f;
		float mz = 0f;
		for (int i = 0; i < n; i++) {
			mx += x[i];
			my += y[i];
			mz += z[i];
		}
		mx /= n;
		my /= n;
		mz /= n;
		float max = 0f;
		for (int i = 0; i < n; i++) {
			float dx = x[i] - mx;
			float dy = y[i] - my;
			float dz = z[i] - mz;
			float r = (float) Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
			if (r > max) {
				max = r;
			}
		}
		if (max < 1e-4f) {
			return new ShakeTrace(ox, oy, oz);
		}
		for (int i = 0; i < n; i++) {
			ox[i] = (x[i] - mx) / max;
			oy[i] = (y[i] - my) / max;
			oz[i] = (z[i] - mz) / max;
		}
		return new ShakeTrace(ox, oy, oz);
	}

	/** Mean distance between corresponding samples. The traces must be the same length. */
	public float distanceTo(final ShakeTrace other) {
		if (other == null || other.x.length != x.length || x.length == 0) {
			return Float.MAX_VALUE;
		}
		double sum = 0.0;
		for (int i = 0; i < x.length; i++) {
			double dx = x[i] - other.x[i];
			double dy = y[i] - other.y[i];
			double dz = z[i] - other.z[i];
			sum += Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
		}
		return (float) (sum / x.length);
	}
}
