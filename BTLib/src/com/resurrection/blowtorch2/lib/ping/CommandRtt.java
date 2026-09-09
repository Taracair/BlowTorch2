package com.resurrection.blowtorch2.lib.ping;

/**
 * Round-trip from a player command on the socket to the next game line.
 * One RTT (there and back), plus the world's think time. Not ICMP, and not
 * a second hop. Android-free (elapsed ms from the caller).
 *
 * <p>Does not put {@code Core.Ping} or Timing Mark on the wire: most worlds
 * ignore those or parse them as typed commands, and the chip stayed {@code —}.
 */
public final class CommandRtt {

	private final PingProbe probe = new PingProbe();
	private int last = PingProbe.NO_SAMPLE;

	public void clear() {
		probe.clear();
		last = PingProbe.NO_SAMPLE;
	}

	/** Drop an in-flight sample; keep {@link #lastMs()}. */
	public void dropPending() {
		probe.clear();
	}

	public int lastMs() {
		return last;
	}

	public boolean isPending() {
		return probe.isPending();
	}

	/**
	 * Start a sample. A command while one is already in flight is ignored
	 * (first send wins, so a burst of {@code n} is not under-counted).
	 */
	public void onCommandSent(final long nowMs) {
		if (probe.isPending()) {
			return;
		}
		probe.markSent(nowMs);
	}

	/**
	 * @return elapsed ms, or {@link PingProbe#NO_SAMPLE} if nothing was waiting
	 *     or {@code stripped} is not game text
	 */
	public int onIncomingGameText(final String stripped, final long nowMs) {
		if (!looksLikeGameText(stripped)) {
			return PingProbe.NO_SAMPLE;
		}
		int rtt = probe.complete(nowMs);
		if (rtt != PingProbe.NO_SAMPLE) {
			last = rtt;
		}
		return rtt;
	}

	/**
	 * Hung command: stop waiting, keep the last number. Does not become
	 * {@link PingProbe#NO_SAMPLE}.
	 */
	public boolean onTimeout(final long nowMs) {
		if (!probe.timedOut(nowMs)) {
			return false;
		}
		probe.clear();
		return true;
	}

	public int msUntilTimeout(final long nowMs) {
		return probe.msUntilTimeout(nowMs);
	}

	/** True when {@code stripped} has a glyph that is not whitespace. */
	public static boolean looksLikeGameText(final String stripped) {
		if (stripped == null) {
			return false;
		}
		for (int i = 0; i < stripped.length(); i++) {
			char c = stripped.charAt(i);
			if (c > ' ') {
				return true;
			}
		}
		return false;
	}
}
