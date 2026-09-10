package com.resurrection.blowtorch2.lib.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The dump a frozen session has to carry off the phone. If the wording that
 * distinguishes a waiting read from held UI is wrong, the next fix will be a
 * guess.
 */
public class ConnectionHealthProbeTest {

	@Test
	public void snapshotWithoutHistorySaysSo() {
		ConnectionHealthProbe p = new ConnectionHealthProbe();
		ConnectionHealthProbe.Live live = new ConnectionHealthProbe.Live();
		live.socketPresent = true;
		live.soTimeoutMs = 0;
		live.appConnected = true;
		String report = p.report(live, 10_000L);
		assertTrue(report, report.contains("snapshot only"));
		assertTrue(report, report.contains("soTimeout=0ms"));
		assertTrue(report, report.contains("App says connected: yes"));
		assertTrue(report, report.contains("Last socket read:  never"));
	}

	@Test
	public void inboundSilenceUpdatesWorstOnBeat() {
		ConnectionHealthProbe p = new ConnectionHealthProbe();
		p.markStarted(1000L);
		p.recordRx(1000L, 40);
		p.beat(16_000L, "rxSilence=15s");
		String report = p.report(new ConnectionHealthProbe.Live(), 16_000L);
		assertTrue(report, report.contains("worst inbound silence: 15s"));
		assertTrue(report, report.contains("rxSilence=15s"));
		assertTrue(report, report.contains("40 bytes"));
	}

	@Test
	public void holdAndFlushAreSeparateFromSocketRead() {
		ConnectionHealthProbe p = new ConnectionHealthProbe();
		p.recordRx(1000L, 80);
		p.recordHoldStart(2000L);
		p.recordHold(500);
		p.recordHold(20);
		p.recordHoldOverflow();
		p.recordFlush(8000L);
		String report = p.report(new ConnectionHealthProbe.Live(), 9000L);
		assertTrue(report, report.contains("hold start: 1"));
		assertTrue(report, report.contains("held bytes (sum): 520"));
		assertTrue(report, report.contains("overflow marks: 1"));
		assertTrue(report, report.contains("flushes: 1"));
	}

	@Test
	public void offStopsRecordingButKeepsTheReading() {
		ConnectionHealthProbe p = new ConnectionHealthProbe();
		p.recordRx(1000L, 10);
		p.setOn(false);
		p.recordRx(5000L, 99);
		p.beat(5000L, "should-not-appear");
		String report = p.report(new ConnectionHealthProbe.Live(), 5000L);
		assertFalse(report, report.contains("should-not-appear"));
		assertTrue(report, report.contains("10 bytes"));
		assertFalse(report, report.contains("99"));
	}

	@Test
	public void resetClearsHistory() {
		ConnectionHealthProbe p = new ConnectionHealthProbe();
		p.recordRx(1000L, 10);
		p.beat(2000L, "hello");
		p.reset();
		String report = p.report(new ConnectionHealthProbe.Live(), 3000L);
		assertTrue(report, report.contains("snapshot only"));
		assertTrue(report, report.contains("0 chunks"));
		assertFalse(report, report.contains("hello"));
	}

	@Test
	public void liveHeldBytesAreTheCurrentBufferNotTheSum() {
		ConnectionHealthProbe.Live live = new ConnectionHealthProbe.Live();
		live.holdingUi = true;
		live.heldBytes = 4096;
		live.heldWindows = 1;
		live.windowShowing = false;
		String report = new ConnectionHealthProbe().report(live, 1000L);
		assertTrue(report, report.contains("Holding for hidden UI: yes"));
		assertTrue(report, report.contains("held now: 4096 bytes / 1 windows"));
		assertTrue(report, report.contains("Game window on screen: no"));
	}

	@Test
	public void describeThreadNamesALiveThread() {
		Thread t = Thread.currentThread();
		String d = ConnectionHealthProbe.describeThread(t, 3);
		assertTrue(d, d.contains(t.getName()));
		assertTrue(d, d.contains(t.getState().name()));
		assertTrue(d, d.contains("\n  "));
	}

	@Test
	public void describeThreadNone() {
		assertEquals("none", ConnectionHealthProbe.describeThread(null, 8));
	}

	@Test
	public void ageAndFmtMsAreWhatThePhonePrints() {
		assertEquals("never", ConnectionHealthProbe.age(0L, 5000L));
		assertEquals("500ms ago", ConnectionHealthProbe.age(1000L, 1500L));
		assertEquals("15s ago", ConnectionHealthProbe.age(1000L, 16_000L));
		assertEquals("2m 5s ago", ConnectionHealthProbe.age(0L + 1, 125_001L));
		assertEquals("1h 2m", ConnectionHealthProbe.fmtMs(3_720_000L));
	}

	@Test
	public void ringDropsOldestHeartbeats() {
		ConnectionHealthProbe p = new ConnectionHealthProbe();
		p.markStarted(1L);
		for (int i = 0; i < ConnectionHealthProbe.RING + 3; i++) {
			p.beat(1000L + i, "n=" + i);
		}
		String report = p.report(new ConnectionHealthProbe.Live(), 2000L);
		assertTrue(report, report.contains("dropped: 3"));
		assertFalse(report, report.contains("n=0"));
		assertTrue(report, report.contains("n=" + (ConnectionHealthProbe.RING + 2)));
	}
}
