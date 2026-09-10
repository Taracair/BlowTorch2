package com.resurrection.blowtorch2.lib.service;

import java.util.ArrayDeque;

/**
 * {@code .probe connection} — socket vs held UI vs inbound silence.
 *
 * <p>A measurement the player runs, same shape as {@code .probe lines}. Off
 * unless asked, except for two uptime stamps on the pump (last read / last
 * write) which are a pair of volatile longs. The report is built for the game
 * window so it can leave the phone without logcat.
 *
 * <p>It does not decide why a session looked frozen. It records the facts that
 * distinguish "the read is waiting forever", "bytes arrived and were held for
 * a hidden UI", and "the app still says connected".
 */
public final class ConnectionHealthProbe {

	public static final String LOG_TAG = "BlowTorchNet";
	public static final int HEARTBEAT_MS = 15000;
	static final int RING = 40;

	private final ArrayDeque<String> beats = new ArrayDeque<String>();
	private int droppedBeats;
	private long startedAt;
	private long lastRxAt;
	private long lastTxAt;
	private long lastDispatchAt;
	private long lastHoldStartAt;
	private long lastFlushAt;
	private long lastDisconnectAt;
	private String lastDisconnect = "";
	private long rxBytes;
	private long txBytes;
	private int rxChunks;
	private int txChunks;
	private int holdStarts;
	private long holdBytesTotal;
	private int flushes;
	private int overflows;
	private long worstRxSilenceMs;
	private boolean on = true;

	public void setOn(final boolean on) {
		this.on = on;
	}

	public boolean isOn() {
		return on;
	}

	public void reset() {
		beats.clear();
		droppedBeats = 0;
		startedAt = 0L;
		lastRxAt = 0L;
		lastTxAt = 0L;
		lastDispatchAt = 0L;
		lastHoldStartAt = 0L;
		lastFlushAt = 0L;
		lastDisconnectAt = 0L;
		lastDisconnect = "";
		rxBytes = 0L;
		txBytes = 0L;
		rxChunks = 0;
		txChunks = 0;
		holdStarts = 0;
		holdBytesTotal = 0L;
		flushes = 0;
		overflows = 0;
		worstRxSilenceMs = 0L;
	}

	public void markStarted(final long now) {
		if (startedAt == 0L) {
			startedAt = now;
		}
	}

	public void recordRx(final long now, final int bytes) {
		if (!on) {
			return;
		}
		noteSilence(now);
		lastRxAt = now;
		rxChunks++;
		if (bytes > 0) {
			rxBytes += bytes;
		}
	}

	public void recordTx(final long now, final int bytes) {
		if (!on) {
			return;
		}
		lastTxAt = now;
		txChunks++;
		if (bytes > 0) {
			txBytes += bytes;
		}
	}

	public void recordDispatch(final long now) {
		if (!on) {
			return;
		}
		lastDispatchAt = now;
	}

	public void recordHoldStart(final long now) {
		if (!on) {
			return;
		}
		lastHoldStartAt = now;
		holdStarts++;
	}

	public void recordHold(final int bytes) {
		if (!on || bytes <= 0) {
			return;
		}
		holdBytesTotal += bytes;
	}

	public void recordHoldOverflow() {
		if (!on) {
			return;
		}
		overflows++;
	}

	public void recordFlush(final long now) {
		if (!on) {
			return;
		}
		lastFlushAt = now;
		flushes++;
	}

	public void recordDisconnect(final long now, final String reason) {
		if (!on) {
			return;
		}
		lastDisconnectAt = now;
		lastDisconnect = reason == null ? "" : reason;
	}

	public void beat(final long now, final String line) {
		if (!on) {
			return;
		}
		markStarted(now);
		noteSilence(now);
		addBeat(now, line);
	}

	private void noteSilence(final long now) {
		if (lastRxAt <= 0L) {
			return;
		}
		long silence = now - lastRxAt;
		if (silence > worstRxSilenceMs) {
			worstRxSilenceMs = silence;
		}
	}

	private void addBeat(final long now, final String line) {
		String stamped = age(startedAt, now) + " " + (line == null ? "" : line);
		beats.addLast(stamped);
		while (beats.size() > RING) {
			beats.removeFirst();
			droppedBeats++;
		}
	}

	/** Snapshot taken at report time; filled by Connection / DataPumper. */
	public static final class Live {
		public boolean probeOn;
		public boolean appConnected;
		public boolean pumpConnected;
		public boolean socketPresent;
		public boolean socketConnected;
		public boolean socketClosed;
		public boolean socketInputShutdown;
		public boolean socketOutputShutdown;
		public boolean keepAlive;
		public int soTimeoutMs = -1;
		public boolean compressed;
		public boolean closing;
		public boolean tls;
		public boolean holdingUi;
		public int heldBytes;
		public int heldWindows;
		public int heldOverflows;
		public boolean keepCpuAwake;
		public boolean needsCpuKeepalive;
		public boolean keepWifiAlive;
		public boolean autoReconnect;
		public boolean persistent;
		public boolean retryPending;
		public int reconnectAttempt;
		public int reconnectLimit;
		public int worldCount;
		public boolean windowShowing;
		public boolean screenInteractive;
		public String network = "";
		public boolean vpn;
		public String pumpThread = "";
		public String writerThread = "";
		public boolean writerHasQueuedSend;
		public long lastRxUptime;
		public long lastTxUptime;
		public long connectedForMs;
		public long holdStartedUptime;
		public long flushUptime;
	}

	public String report(final Live live, final long now) {
		StringBuilder s = new StringBuilder();
		s.append("\nConnection probe — socket vs held UI vs silence\n");
		if (live != null && live.probeOn) {
			s.append("Probe: on for ").append(age(startedAt, now)).append('\n');
		} else if (startedAt > 0L) {
			s.append("Probe: off (reading kept). Was on for ")
					.append(age(startedAt, now)).append('\n');
		} else {
			s.append("Probe: snapshot only (no heartbeat history). ")
					.append(".probe connection on to record while you play.\n");
		}
		if (live != null) {
			appendLive(s, live, now);
		}
		s.append("Recorded while probe was on:\n");
		s.append("  inbound:  ").append(rxChunks).append(" chunks / ")
				.append(rxBytes).append(" bytes  last ")
				.append(age(lastRxAt, now)).append('\n');
		s.append("  dispatch: last ").append(age(lastDispatchAt, now)).append('\n');
		s.append("  outbound: ").append(txChunks).append(" writes / ")
				.append(txBytes).append(" bytes  last ")
				.append(age(lastTxAt, now)).append('\n');
		s.append("  hold start: ").append(holdStarts).append("  last ")
				.append(age(lastHoldStartAt, now)).append('\n');
		s.append("  held bytes (sum): ").append(holdBytesTotal)
				.append("  overflow marks: ").append(overflows).append('\n');
		s.append("  flushes: ").append(flushes).append("  last ")
				.append(age(lastFlushAt, now)).append('\n');
		s.append("  worst inbound silence: ").append(fmtMs(worstRxSilenceMs)).append('\n');
		if (lastDisconnectAt > 0L) {
			s.append("  last disconnect: ").append(lastDisconnect)
					.append("  ").append(age(lastDisconnectAt, now)).append(" ago\n");
		} else {
			s.append("  last disconnect: none this run\n");
		}
		s.append("Heartbeats kept: ").append(beats.size());
		if (droppedBeats > 0) {
			s.append("  dropped: ").append(droppedBeats);
		}
		s.append("  tag ").append(LOG_TAG).append('\n');
		if (beats.isEmpty()) {
			s.append("(no heartbeats — .probe connection on, or this is a one-shot dump)\n");
		} else {
			for (String beat : beats) {
				s.append("  ").append(beat).append('\n');
			}
		}
		s.append('\n');
		s.append("How to read this:\n");
		s.append("- soTimeout=0: a read waits until a byte or a close. Auto-reconnect\n");
		s.append("  starts only after a close or error, not after silence.\n");
		s.append("- Inbound silence + read stack in getData/read + connected=yes:\n");
		s.append("  the socket still looks open. .ping is ICMP; it does not test this TCP.\n");
		s.append("- Held bytes growing while inbound is recent: the socket is alive;\n");
		s.append("  the UI is not pushed until the game window comes back.\n");
		s.append("- Shade still says Connected when this dump shows inbound silence:\n");
		s.append("  the app has not seen a disconnect.\n");
		return s.toString();
	}

	private static void appendLive(final StringBuilder s, final Live live,
			final long now) {
		s.append("App says connected: ").append(yn(live.appConnected));
		if (live.connectedForMs > 0L) {
			s.append("  up ").append(fmtMs(live.connectedForMs));
		}
		s.append('\n');
		s.append("Keep CPU Awake?: ").append(yn(live.keepCpuAwake))
				.append("  lock wanted: ").append(yn(live.needsCpuKeepalive)).append('\n');
		s.append("Keep Wifi Alive?: ").append(yn(live.keepWifiAlive)).append('\n');
		s.append("Auto Reconnect?: ").append(yn(live.autoReconnect))
				.append("  tries ").append(live.reconnectAttempt).append('/')
				.append(live.reconnectLimit)
				.append("  pending: ").append(yn(live.retryPending)).append('\n');
		s.append("Persistent Connection?: ").append(yn(live.persistent)).append('\n');
		s.append("Worlds open: ").append(live.worldCount).append('\n');
		s.append("Game window on screen: ").append(yn(live.windowShowing)).append('\n');
		s.append("Screen on: ").append(yn(live.screenInteractive)).append('\n');
		s.append("Network: ").append(live.network == null ? "" : live.network)
				.append("  vpn: ").append(yn(live.vpn)).append('\n');
		s.append("TLS: ").append(yn(live.tls))
				.append("  MCCP: ").append(yn(live.compressed))
				.append("  closing: ").append(yn(live.closing)).append('\n');
		s.append("Pump connected flag: ").append(yn(live.pumpConnected)).append('\n');
		if (!live.socketPresent) {
			s.append("Socket: none\n");
		} else {
			s.append("Socket: connected=").append(yn(live.socketConnected))
					.append(" closed=").append(yn(live.socketClosed))
					.append(" inShut=").append(yn(live.socketInputShutdown))
					.append(" outShut=").append(yn(live.socketOutputShutdown))
					.append('\n');
			s.append("  keepalive=").append(yn(live.keepAlive))
					.append(" soTimeout=").append(live.soTimeoutMs).append("ms\n");
		}
		s.append("Last socket read:  ").append(age(live.lastRxUptime, now)).append('\n');
		s.append("Last socket write: ").append(age(live.lastTxUptime, now)).append('\n');
		s.append("Holding for hidden UI: ").append(yn(live.holdingUi))
				.append("  held now: ").append(live.heldBytes).append(" bytes / ")
				.append(live.heldWindows).append(" windows")
				.append("  overflowed: ").append(live.heldOverflows).append('\n');
		s.append("Hold started: ").append(age(live.holdStartedUptime, now))
				.append("  last flush: ").append(age(live.flushUptime, now)).append('\n');
		s.append("Read thread: ").append(live.pumpThread == null ? "" : live.pumpThread)
				.append('\n');
		s.append("Write thread: ").append(live.writerThread == null ? "" : live.writerThread)
				.append('\n');
		s.append("Write queue has SEND: ").append(yn(live.writerHasQueuedSend)).append('\n');
		s.append('\n');
	}

	public static String describeThread(final Thread t, final int frames) {
		if (t == null) {
			return "none";
		}
		StringBuilder b = new StringBuilder();
		String name = t.getName();
		b.append(name == null || name.length() == 0 ? "(unnamed)" : name);
		b.append(' ').append(t.getState());
		if (!t.isAlive()) {
			b.append(" dead");
		}
		StackTraceElement[] st = t.getStackTrace();
		if (st == null) {
			return b.toString();
		}
		int n = frames < st.length ? frames : st.length;
		for (int i = 0; i < n; i++) {
			String c = st[i].getClassName();
			int dot = c.lastIndexOf('.');
			String shortName = dot < 0 ? c : c.substring(dot + 1);
			b.append("\n  ").append(shortName).append('.')
					.append(st[i].getMethodName()).append(':')
					.append(st[i].getLineNumber());
		}
		return b.toString();
	}

	static String age(final long then, final long now) {
		if (then <= 0L) {
			return "never";
		}
		long ms = now - then;
		if (ms < 0L) {
			ms = 0L;
		}
		return fmtMs(ms) + " ago";
	}

	static String fmtMs(final long ms) {
		if (ms < 1000L) {
			return ms + "ms";
		}
		long sec = ms / 1000L;
		if (sec < 60L) {
			return sec + "s";
		}
		long min = sec / 60L;
		sec = sec % 60L;
		if (min < 60L) {
			return min + "m " + sec + "s";
		}
		long hr = min / 60L;
		min = min % 60L;
		return hr + "h " + min + "m";
	}

	private static String yn(final boolean v) {
		return v ? "yes" : "no";
	}
}
