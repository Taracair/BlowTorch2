package com.resurrection.blowtorch2.lib.service.function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class JumpSendCommandTest {

	@Test
	public void usageNamesTheSwitch() {
		String u = JumpSendCommand.usage();
		assertTrue(u.contains(".jumpsend on"));
		assertTrue(u.contains("off"));
	}

	@Test
	public void keyAndName() {
		assertEquals("jump_on_send", JumpSendCommand.OPTION_KEY);
		assertEquals("jumpsend", new JumpSendCommand().commandName);
	}
}
