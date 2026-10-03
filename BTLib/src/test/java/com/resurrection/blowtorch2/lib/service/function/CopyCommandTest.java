package com.resurrection.blowtorch2.lib.service.function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CopyCommandTest {

	@Test
	public void copyLoupeUsesTheSameTokensAsPickLoupe() {
		assertTrue(PickCommand.isLoupeCommand("loupe"));
		assertTrue(PickCommand.isLoupeCommand("loupe size 118"));
		assertTrue(PickCommand.isLoupeCommand("size 130"));
		assertTrue(PickCommand.isLoupeCommand("zoom 250"));
		assertTrue(PickCommand.isLoupeCommand("loupe default"));
		assertFalse(PickCommand.isLoupeCommand("hold"));
		assertFalse(PickCommand.isLoupeCommand(""));
	}

	@Test
	public void commandNameIsCopy() {
		assertEquals("copy", new CopyCommand().commandName);
	}
}
