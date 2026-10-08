package com.resurrection.blowtorch2.lib.service.function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FullScreenCommandTest {

	@Test
	public void toggleCommandFlipsOnEveryCall() {
		FullScreenCommand toggle = new FullScreenCommand();
		assertEquals("togglefullscreen", toggle.commandName);
		assertTrue(toggle.flipsOnEveryCall());
	}

	@Test
	public void fullscreenBareIsStatus() {
		FullScreenCommand flag = new FullScreenCommand(false);
		assertEquals("fullscreen", flag.commandName);
		assertFalse(flag.flipsOnEveryCall());
		assertEquals(FullScreenCommand.STATUS, FullScreenCommand.parseFlag(""));
		assertEquals(FullScreenCommand.STATUS, FullScreenCommand.parseFlag("   "));
	}

	@Test
	public void onOffToggleAndNothingElse() {
		assertEquals(FullScreenCommand.ON, FullScreenCommand.parseFlag("on"));
		assertEquals(FullScreenCommand.OFF, FullScreenCommand.parseFlag("OFF"));
		assertEquals(FullScreenCommand.TOGGLE, FullScreenCommand.parseFlag("toggle"));
		assertEquals(FullScreenCommand.BAD, FullScreenCommand.parseFlag("off please"));
		assertEquals(FullScreenCommand.BAD, FullScreenCommand.parseFlag("true"));
		assertEquals(FullScreenCommand.BAD, FullScreenCommand.parseFlag("1"));
		assertEquals(FullScreenCommand.BAD, FullScreenCommand.parseFlag(null));
	}
}
