package com.resurrection.blowtorch2.lib.service.function;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LupaCommandTest {

	@Test
	public void emptyIsOnce() {
		assertEquals(LupaCommand.MODE_ONCE, LupaCommand.parseMode(""));
		assertEquals(LupaCommand.MODE_ONCE, LupaCommand.parseMode("once"));
	}

	@Test
	public void holdAliases() {
		assertEquals(LupaCommand.MODE_HOLD, LupaCommand.parseMode("hold"));
		assertEquals(LupaCommand.MODE_HOLD, LupaCommand.parseMode("on"));
	}

	@Test
	public void tapAndOff() {
		assertEquals(LupaCommand.MODE_TAP, LupaCommand.parseMode("tap"));
		assertEquals(LupaCommand.MODE_OFF, LupaCommand.parseMode("off"));
		assertEquals(-1, LupaCommand.parseMode("banana"));
	}
}
