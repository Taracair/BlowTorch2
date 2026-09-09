package com.resurrection.blowtorch2.lib.ping;

/**
 * Number colour for the ping HUD. Thresholds are display policy, not a
 * measured SLA — worlds differ. {@code rttMs < 0} is no sample yet.
 */
public final class PingHudColor {

	public static final int UNKNOWN = 0;
	public static final int GOOD = 1;
	public static final int OK = 2;
	public static final int SLOW = 3;

	public static final int GOOD_BELOW_MS = 80;
	public static final int OK_BELOW_MS = 180;

	private PingHudColor() {
	}

	public static int band(final int rttMs) {
		if (rttMs < 0) {
			return UNKNOWN;
		}
		if (rttMs < GOOD_BELOW_MS) {
			return GOOD;
		}
		if (rttMs < OK_BELOW_MS) {
			return OK;
		}
		return SLOW;
	}

	public static String label(final int rttMs) {
		if (rttMs < 0) {
			return "—";
		}
		return rttMs + " ms";
	}
}
