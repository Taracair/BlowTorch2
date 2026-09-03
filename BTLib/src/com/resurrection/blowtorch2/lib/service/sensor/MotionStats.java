package com.resurrection.blowtorch2.lib.service.sensor;

import java.util.Arrays;
import java.util.Locale;

/**
 * Motion samples and how many separate shakes a threshold would have fired —
 * not how many samples crossed it. Also reports delivery gaps (FIFO batches).
 * No Android types.
 */
public final class MotionStats {

	/** Samples kept. At 200 Hz this is 100 seconds, well past any probe run. */
	public static final int MAX_SAMPLES = 20000;

	/**
	 * How far apart two crossings must be to count as two gestures. Matches the
	 * dead time a real detector needs — without one, a single shake is four.
	 */
	public static final long DEFAULT_DEAD_TIME_NANOS = 500L * 1000L * 1000L;

	/** Thresholds the report scores, in m/s². */
	private static final double[] CANDIDATE_THRESHOLDS = {
		4.0, 6.0, 8.0, 10.0, 12.0, 15.0, 20.0, 25.0
	};

	private final long[] timestamps = new long[MAX_SAMPLES];
	private final double[] magnitudes = new double[MAX_SAMPLES];
	private int count;
	private int dropped;
	private String sourceLabel = "unknown";
	private boolean wakeUp;

	/** What produced these numbers, for the report header. */
	public void describeSource(final String label, final boolean isWakeUpSensor) {
		this.sourceLabel = label == null ? "unknown" : label;
		this.wakeUp = isWakeUpSensor;
	}

	/**
	 * Record one reading.
	 *
	 * @param timestampNanos the sensor's own timestamp, not the clock. Batched
	 *        deliveries are only visible in the sensor's numbers.
	 * @param magnitude movement in m/s², gravity already taken out by the
	 *        caller — see {@link #gravityRemoved}.
	 */
	public void record(final long timestampNanos, final double magnitude) {
		if (count >= MAX_SAMPLES) {
			dropped++;
			return;
		}
		timestamps[count] = timestampNanos;
		magnitudes[count] = magnitude;
		count++;
	}

	/**
	 * Movement left after gravity is taken out of a raw accelerometer reading.
	 *
	 * <p>A phone lying still reads about 9.81 m/s² and is not moving. The
	 * deviation from that is the movement, in either direction — dropping the
	 * phone reads below gravity, and that is motion too.
	 */
	public static double gravityRemoved(final double x, final double y, final double z,
			final double gravity) {
		double magnitude = Math.sqrt((x * x) + (y * y) + (z * z));
		return Math.abs(magnitude - gravity);
	}

	public int getSampleCount() {
		return count;
	}

	/** Nanoseconds between the first and last reading, 0 with fewer than two. */
	public long spanNanos() {
		if (count < 2) {
			return 0L;
		}
		return timestamps[count - 1] - timestamps[0];
	}

	/** Readings per second, measured rather than requested. 0 when unknown. */
	public double measuredHz() {
		long span = spanNanos();
		if (span <= 0L) {
			return 0.0;
		}
		return (count - 1) * 1e9 / span;
	}

	public double peak() {
		double top = 0.0;
		for (int i = 0; i < count; i++) {
			if (magnitudes[i] > top) {
				top = magnitudes[i];
			}
		}
		return top;
	}

	/** Value below which the given fraction of readings sit; 0 when empty. */
	public double percentile(final double fraction) {
		if (count == 0) {
			return 0.0;
		}
		double[] sorted = Arrays.copyOf(magnitudes, count);
		Arrays.sort(sorted);
		int index = (int) Math.floor(fraction * (count - 1));
		if (index < 0) {
			index = 0;
		}
		if (index >= count) {
			index = count - 1;
		}
		return sorted[index];
	}

	/**
	 * How many separate gestures a detector with this threshold would have
	 * fired: a crossing counts, and further crossings are ignored until the
	 * dead time has passed. This is the number that decides a threshold.
	 */
	public int gesturesAbove(final double threshold, final long deadTimeNanos) {
		int fired = 0;
		long lastFire = Long.MIN_VALUE;
		for (int i = 0; i < count; i++) {
			if (magnitudes[i] < threshold) {
				continue;
			}
			if (lastFire == Long.MIN_VALUE || (timestamps[i] - lastFire) >= deadTimeNanos) {
				fired++;
				lastFire = timestamps[i];
			}
		}
		return fired;
	}

	/** Largest gap between consecutive readings, in milliseconds. */
	public double largestGapMillis() {
		double worst = 0.0;
		for (int i = 1; i < count; i++) {
			double gap = (timestamps[i] - timestamps[i - 1]) / 1e6;
			if (gap > worst) {
				worst = gap;
			}
		}
		return worst;
	}

	/**
	 * Readings that arrived less than a tenth of the average gap after the one
	 * before. A sensor delivering a hardware batch shows up here as a run of
	 * near-zero gaps; a steady sensor shows none.
	 */
	public int batchedSamples() {
		if (count < 3) {
			return 0;
		}
		double averageGap = (double) spanNanos() / (count - 1);
		double tight = averageGap / 10.0;
		int batched = 0;
		for (int i = 1; i < count; i++) {
			if ((timestamps[i] - timestamps[i - 1]) < tight) {
				batched++;
			}
		}
		return batched;
	}

	/** The whole reading, as it goes into the game window. */
	public String report() {
		StringBuilder out = new StringBuilder();
		out.append("\n--- motion probe ---\n");
		out.append("source        : ").append(sourceLabel)
			.append(wakeUp ? " (wake-up)" : " (not wake-up)").append('\n');

		if (count == 0) {
			out.append("samples       : NONE\n");
			out.append("The sensor was registered and delivered nothing. Either this\n");
			out.append("process cannot receive sensor events, or the sensor is asleep.\n");
			out.append("That answers a bigger question than the threshold does.\n");
			return out.toString();
		}

		out.append(String.format(Locale.US, "samples       : %d in %.1f s%n",
				count, spanNanos() / 1e9));
		out.append(String.format(Locale.US, "measured rate : %.0f Hz%n", measuredHz()));
		out.append(String.format(Locale.US, "largest gap   : %.1f ms%n", largestGapMillis()));
		out.append(String.format(Locale.US, "batched       : %d of %d readings%n",
				batchedSamples(), count));
		if (dropped > 0) {
			out.append(String.format(Locale.US, "dropped       : %d (buffer full)%n", dropped));
		}
		out.append(String.format(Locale.US, "peak          : %.1f m/s2%n", peak()));
		out.append(String.format(Locale.US, "median / p95  : %.1f / %.1f m/s2%n",
				percentile(0.5), percentile(0.95)));
		out.append("\nReadings a detector would have crossed (500 ms dead time):\n");
		for (int i = 0; i < CANDIDATE_THRESHOLDS.length; i++) {
			double threshold = CANDIDATE_THRESHOLDS[i];
			out.append(String.format(Locale.US, "  above %5.1f m/s2 : %d%n",
					threshold, gesturesAbove(threshold, DEFAULT_DEAD_TIME_NANOS)));
		}
		out.append("\nRun this once shaking and once walking. A usable threshold is\n");
		out.append("the lowest one that counts your shakes and counts the walk as 0.\n");
		out.append("A single shake held longer than the dead time counts more than\n");
		out.append("once here — and a real detector would fire more than once too.\n");
		return out.toString();
	}
}
