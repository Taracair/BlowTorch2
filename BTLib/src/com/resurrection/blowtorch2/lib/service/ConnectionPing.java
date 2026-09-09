package com.resurrection.blowtorch2.lib.service;

import com.resurrection.blowtorch2.lib.ping.PingProbe;
import com.resurrection.blowtorch2.lib.service.function.PingCommand;

import android.os.Bundle;
import android.os.Message;
import android.os.SystemClock;

/**
 * RTT while the ping overlay is on: GMCP {@code Core.Ping} when GMCP is on,
 * plus telnet Timing Mark (option 6). First reply wins. Not ICMP.
 */
final class ConnectionPing {

	private final Connection host;
	private final PingProbe probe = new PingProbe();

	ConnectionPing(final Connection host) {
		this.host = host;
	}

	void onConnected() {
		probe.clear();
		scheduleTick(0);
	}

	void onDisconnected() {
		probe.clear();
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
		if (!host.isConnected()) {
			return;
		}
		if (!hudOn()) {
			probe.clear();
			return;
		}
		long now = SystemClock.elapsedRealtime();
		if (probe.timedOut(now)) {
			probe.complete(now);
			notifyHud(PingProbe.NO_SAMPLE);
		}
		if (probe.isPending()) {
			scheduleTick(probe.msUntilTimeout(now));
			return;
		}
		sendProbe(now);
		scheduleTick(PingProbe.INTERVAL_MS);
	}

	void onTimingMark() {
		finish(SystemClock.elapsedRealtime());
	}

	void onGmcpPing() {
		finish(SystemClock.elapsedRealtime());
	}

	private void finish(final long now) {
		int rtt = probe.complete(now);
		if (rtt == PingProbe.NO_SAMPLE) {
			return;
		}
		notifyHud(rtt);
	}

	private void sendProbe(final long now) {
		Processor p = host.getProcessor();
		if (p != null && p.isUseGMCP()) {
			host.mHandler.obtainMessage(Connection.MESSAGE_SENDGMCPDATA, "Core.Ping")
					.sendToTarget();
		}
		byte[] tm = new byte[] { TC.IAC, TC.DO, TC.TM };
		Message opt = host.mHandler.obtainMessage(Connection.MESSAGE_SENDOPTIONDATA);
		Bundle b = opt.getData();
		b.putByteArray("THE_DATA", tm);
		opt.setData(b);
		host.mHandler.sendMessage(opt);
		probe.markSent(now);
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
