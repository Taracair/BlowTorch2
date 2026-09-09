package com.resurrection.blowtorch2.lib.service;

import com.resurrection.blowtorch2.lib.ping.CommandRtt;
import com.resurrection.blowtorch2.lib.ping.PingProbe;
import com.resurrection.blowtorch2.lib.service.function.PingCommand;

import android.os.SystemClock;

/**
 * RTT while the ping overlay is on: time from a command written to the
 * socket until the next game line. Not ICMP. Does not send {@code Core.Ping}
 * or Timing Mark — most worlds do not reply, and some parse those as typed
 * commands.
 */
final class ConnectionPing {

	private final Connection host;
	private final CommandRtt rtt = new CommandRtt();

	ConnectionPing(final Connection host) {
		this.host = host;
	}

	void onConnected() {
		rtt.clear();
		notifyHud(PingProbe.NO_SAMPLE);
	}

	void onDisconnected() {
		rtt.clear();
		if (host.mHandler != null) {
			host.mHandler.removeMessages(Connection.MESSAGE_PING_TICK);
		}
		notifyHud(PingProbe.NO_SAMPLE);
	}

	void tick() {
		if (host.mHandler == null) {
			return;
		}
		host.mHandler.removeMessages(Connection.MESSAGE_PING_TICK);
		if (!host.isConnected() || !hudOn()) {
			rtt.dropPending();
			return;
		}
		long now = SystemClock.elapsedRealtime();
		rtt.onTimeout(now);
		if (rtt.isPending()) {
			scheduleTick(rtt.msUntilTimeout(now));
		}
	}

	void onCommandSent() {
		if (!host.isConnected() || !hudOn()) {
			return;
		}
		boolean wasPending = rtt.isPending();
		rtt.onCommandSent(SystemClock.elapsedRealtime());
		if (!wasPending && rtt.isPending()) {
			scheduleTick(PingProbe.TIMEOUT_MS);
		}
	}

	void onIncomingGameText(final String stripped) {
		if (!hudOn()) {
			return;
		}
		int sample = rtt.onIncomingGameText(stripped, SystemClock.elapsedRealtime());
		if (sample != PingProbe.NO_SAMPLE) {
			notifyHud(sample);
			if (host.mHandler != null) {
				host.mHandler.removeMessages(Connection.MESSAGE_PING_TICK);
			}
		}
	}

	/** Unsolicited WILL TM / Core.Ping is not a command round-trip. */
	void onTimingMark() {
	}

	void onGmcpPing() {
	}

	private boolean hudOn() {
		return host.getMainWindowBooleanOption(PingCommand.OPTION_ENABLED, false);
	}

	private void scheduleTick(final int delayMs) {
		if (host.mHandler == null) {
			return;
		}
		host.mHandler.sendEmptyMessageDelayed(Connection.MESSAGE_PING_TICK, delayMs);
	}

	private void notifyHud(final int rttMs) {
		if (host.mService != null) {
			host.mService.notifyPingHud(host.mDisplay, rttMs);
		}
	}
}
