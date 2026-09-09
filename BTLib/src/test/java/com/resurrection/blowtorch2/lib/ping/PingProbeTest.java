package com.resurrection.blowtorch2.lib.ping;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PingProbeTest {

	@Test
	public void firstReplyWins() {
		PingProbe p = new PingProbe();
		p.markSent(1000);
		assertEquals(42, p.complete(1042));
		assertEquals(PingProbe.NO_SAMPLE, p.complete(1100));
	}

	@Test
	public void timeoutThenCompleteIsIgnored() {
		PingProbe p = new PingProbe();
		p.markSent(0);
		assertFalse(p.timedOut(PingProbe.TIMEOUT_MS - 1));
		assertTrue(p.timedOut(PingProbe.TIMEOUT_MS));
		assertEquals(PingProbe.TIMEOUT_MS, p.complete(PingProbe.TIMEOUT_MS));
	}

	@Test
	public void completeWithoutSendIsNoSample() {
		assertEquals(PingProbe.NO_SAMPLE, new PingProbe().complete(50));
	}

	@Test
	public void clearDropsPending() {
		PingProbe p = new PingProbe();
		p.markSent(10);
		p.clear();
		assertFalse(p.isPending());
		assertEquals(PingProbe.NO_SAMPLE, p.complete(20));
	}

	@Test
	public void msUntilTimeoutWhilePending() {
		PingProbe p = new PingProbe();
		p.markSent(0);
		assertEquals(PingProbe.TIMEOUT_MS - 1000, p.msUntilTimeout(1000));
		assertEquals(1, p.msUntilTimeout(PingProbe.TIMEOUT_MS));
		p.clear();
		assertEquals(0, p.msUntilTimeout(0));
	}
}
