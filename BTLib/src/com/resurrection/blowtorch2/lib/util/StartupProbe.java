package com.resurrection.blowtorch2.lib.util;

import java.util.Locale;

/**
 * PROBE — revert me.
 *
 * <p>Where the 1.5 seconds between the process starting and the first drawn
 * frame goes. Measured on the device, 7 August: process start at 20:24:43.903,
 * first Choreographer frame at 20:24:45.403. What is inside that gap is unknown,
 * and this project has produced a confident wrong performance hypothesis several
 * times, so nothing is being changed until this says which step it is.
 *
 * <p>Statics are per process, so {@code :stellar} keeps its own reckoning; the
 * pid is printed to keep the two apart in one logcat.
 */
public final class StartupProbe {

	private StartupProbe() {
	}

	/** uptimeMillis of the previous mark, for the per-step cost. */
	private static long sLast = 0;

	/** uptimeMillis of the first mark in this process. */
	private static long sFirst = 0;

	/**
	 * Log one step.
	 *
	 * @param tag what just finished, in the order the code runs it.
	 */
	public static void mark(final String tag) {
		long now = android.os.SystemClock.uptimeMillis();
		if (sFirst == 0) {
			sFirst = now;
			sLast = now;
		}
		long sinceProcess = now - android.os.Process.getStartUptimeMillis();
		android.util.Log.i("BTPROF", String.format(Locale.US,
				"%-26s step=%5d  first+%5d  proc+%5d  pid=%d",
				tag, now - sLast, now - sFirst, sinceProcess,
				android.os.Process.myPid()));
		sLast = now;
	}
}
