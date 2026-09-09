package com.resurrection.blowtorch2.lib.ping;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CommandRttTest {

	@Test
	public void commandThenTextIsOneRoundTrip() {
		CommandRtt r = new CommandRtt();
		r.onCommandSent(1000);
		assertEquals(80, r.onIncomingGameText("You see a room.\n", 1080));
		assertEquals(80, r.lastMs());
		assertEquals(PingProbe.NO_SAMPLE, r.onIncomingGameText("more\n", 1100));
	}

	@Test
	public void whitespaceIncomingDoesNotComplete() {
		CommandRtt r = new CommandRtt();
		r.onCommandSent(0);
		assertEquals(PingProbe.NO_SAMPLE, r.onIncomingGameText("\n\n  \t", 50));
		assertTrue(r.isPending());
		assertEquals(50, r.onIncomingGameText(">", 50));
	}

	@Test
	public void secondCommandWhileWaitingIsIgnored() {
		CommandRtt r = new CommandRtt();
		r.onCommandSent(0);
		r.onCommandSent(30);
		assertEquals(80, r.onIncomingGameText("ok\n", 80));
	}

	@Test
	public void timeoutKeepsLastSample() {
		CommandRtt r = new CommandRtt();
		r.onCommandSent(0);
		r.onIncomingGameText("hi\n", 40);
		r.onCommandSent(100);
		assertTrue(r.onTimeout(100 + PingProbe.TIMEOUT_MS));
		assertFalse(r.isPending());
		assertEquals(40, r.lastMs());
	}

	@Test
	public void incomingWithoutSendIsNoSample() {
		assertEquals(PingProbe.NO_SAMPLE,
				new CommandRtt().onIncomingGameText("noise\n", 10));
	}

	@Test
	public void looksLikeGameText() {
		assertFalse(CommandRtt.looksLikeGameText(null));
		assertFalse(CommandRtt.looksLikeGameText(""));
		assertFalse(CommandRtt.looksLikeGameText("\n \t"));
		assertTrue(CommandRtt.looksLikeGameText("[chan]: hi"));
	}
}
