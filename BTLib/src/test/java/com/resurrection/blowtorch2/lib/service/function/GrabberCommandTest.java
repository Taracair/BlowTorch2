package com.resurrection.blowtorch2.lib.service.function;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GrabberCommandTest {

	@Test
	public void emptyIsOnce() {
		assertEquals(GrabberCommand.MODE_ONCE, GrabberCommand.parseMode(""));
		assertEquals(GrabberCommand.MODE_ONCE, GrabberCommand.parseMode("once"));
	}

	@Test
	public void holdAliases() {
		assertEquals(GrabberCommand.MODE_HOLD, GrabberCommand.parseMode("hold"));
		assertEquals(GrabberCommand.MODE_HOLD, GrabberCommand.parseMode("on"));
	}

	@Test
	public void tapAndOff() {
		assertEquals(GrabberCommand.MODE_TAP, GrabberCommand.parseMode("tap"));
		assertEquals(GrabberCommand.MODE_OFF, GrabberCommand.parseMode("off"));
		assertEquals(-1, GrabberCommand.parseMode("banana"));
	}
}
