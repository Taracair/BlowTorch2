package com.resurrection.blowtorch2.lib.util;

/**
 * Which player-visible wait the next main-window frame closes.
 * The earliest start wins, inside {@link #SWITCH_FRESH_MS}: a launcher tap
 * beats a later window rebuild, and a resume that then switches keeps the resume.
 */
public final class TimingWatch {

	static final long SWITCH_FRESH_MS = 30000L;

	public final String reason;
	public final long fromUp;
	public final long armedUp;

	public TimingWatch(final String reason, final long fromUp, final long armedUp) {
		this.reason = reason;
		this.fromUp = fromUp;
		this.armedUp = armedUp;
	}

	public static TimingWatch arm(final TimingWatch prev, final long launchUp,
			final long switchUp, final long resumeUp, final String reason,
			final long fromUp, final long now) {
		String why = reason == null || reason.length() == 0 ? "paint" : reason;
		long from = fromUp;
		if (launchUp > 0L && now - launchUp < SWITCH_FRESH_MS
				&& (from <= 0L || launchUp < from)) {
			from = launchUp;
			why = "launch";
		}
		if (switchUp > 0L && now - switchUp < SWITCH_FRESH_MS
				&& (from <= 0L || switchUp < from)) {
			from = switchUp;
			why = "switch";
		}
		if (resumeUp > 0L && now - resumeUp < SWITCH_FRESH_MS
				&& (from <= 0L || resumeUp < from) && !"launch".equals(why)) {
			if ("switch".equals(why)) {
				why = "resume+switch";
			} else if (!"resume".equals(why)) {
				why = "resume";
			}
			from = resumeUp;
		}
		if (prev != null && prev.fromUp > 0L && now - prev.fromUp < SWITCH_FRESH_MS
				&& prev.fromUp < from) {
			from = prev.fromUp;
			if (!prev.reason.equals(why)) {
				why = prev.reason + "+" + why;
			}
		}
		if (from <= 0L) {
			from = now;
		}
		return new TimingWatch(why, from, now);
	}
}
