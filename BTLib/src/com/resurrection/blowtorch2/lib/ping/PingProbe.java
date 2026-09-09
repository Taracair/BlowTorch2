package com.resurrection.blowtorch2.lib.ping;

/**
 * One in-flight RTT sample. First reply wins; a second complete is ignored.
 * Android-free (elapsed ms from the caller).
 */
public final class PingProbe {

	public static final int NO_SAMPLE = -1;
	public static final int TIMEOUT_MS = 4000;
	public static final int INTERVAL_MS = 3000;

	private boolean pending;
	private long sentAt;

	public void markSent(final long nowMs) {
		pending = true;
		sentAt = nowMs;
	}

	public boolean isPending() {
		return pending;
	}

	public boolean timedOut(final long nowMs) {
		return pending && (nowMs - sentAt) >= TIMEOUT_MS;
	}

	/**
	 * @return elapsed ms, or {@link #NO_SAMPLE} if nothing was in flight
	 */
	public int complete(final long nowMs) {
		if (!pending) {
			return NO_SAMPLE;
		}
		pending = false;
		long d = nowMs - sentAt;
		if (d < 0) {
			d = 0;
		}
		if (d > Integer.MAX_VALUE) {
			d = Integer.MAX_VALUE;
		}
		return (int) d;
	}

	public void clear() {
		pending = false;
	}

	/** Milliseconds until {@link #TIMEOUT_MS} elapses, or 1 if already due. */
	public int msUntilTimeout(final long nowMs) {
		if (!pending) {
			return 0;
		}
		long left = TIMEOUT_MS - (nowMs - sentAt);
		if (left < 1L) {
			return 1;
		}
		if (left > Integer.MAX_VALUE) {
			return Integer.MAX_VALUE;
		}
		return (int) left;
	}
}
