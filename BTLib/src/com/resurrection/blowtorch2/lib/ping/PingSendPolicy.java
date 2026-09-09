package com.resurrection.blowtorch2.lib.ping;

/**
 * What may go on the socket for {@code .ping}. Unsolicited Timing Mark or
 * GMCP {@code Core.Ping} is parsed as a typed command on worlds that do not
 * speak that option — the world replies that it does not understand.
 */
public final class PingSendPolicy {

	private PingSendPolicy() {
	}

	/** GMCP payload only after the world offered GMCP, not merely the setting. */
	public static boolean sendGmcp(final boolean useGmcp, final boolean serverOfferedGmcp) {
		return useGmcp && serverOfferedGmcp;
	}

	/** Timing Mark only if the world already sent WILL TM. Do not probe with DO. */
	public static boolean sendTimingMark(final boolean serverOfferedTm) {
		return serverOfferedTm;
	}
}
