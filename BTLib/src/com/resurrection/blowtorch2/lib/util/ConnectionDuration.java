package com.resurrection.blowtorch2.lib.util;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Formats connection uptime / last-session duration for notifications and launcher. */
public final class ConnectionDuration {

	private ConnectionDuration() {
	}

	/** Compact label such as {@code 45s}, {@code 12m 03s}, {@code 1h 05m}. */
	public static String formatElapsed(long elapsedMs) {
		if (elapsedMs < 0L) {
			elapsedMs = 0L;
		}
		long totalSec = TimeUnit.MILLISECONDS.toSeconds(elapsedMs);
		long hours = totalSec / 3600L;
		long minutes = (totalSec % 3600L) / 60L;
		long seconds = totalSec % 60L;
		if (hours > 0L) {
			return String.format(Locale.US, "%dh %02dm", hours, minutes);
		}
		if (minutes > 0L) {
			return String.format(Locale.US, "%dm %02ds", minutes, seconds);
		}
		return String.format(Locale.US, "%ds", seconds);
	}
}
