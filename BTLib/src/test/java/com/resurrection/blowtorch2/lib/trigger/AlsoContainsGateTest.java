package com.resurrection.blowtorch2.lib.trigger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AlsoContainsGateTest {

	@Test
	public void emptyNeedleAlwaysPasses() {
		assertTrue(AlsoContainsGate.passes("-- map --", 0, "", true));
		assertTrue(AlsoContainsGate.passes("-- map --", 0, null, false));
	}

	@Test
	public void literalLooksOnlyAtThatLine() {
		String chunk = "[chan]: title -- spam\n-- map --\n";
		int chanDash = chunk.indexOf("-- spam");
		int mapDash = chunk.indexOf("-- map");
		assertTrue(AlsoContainsGate.passes(chunk, chanDash, "[chan]:", true));
		assertFalse(AlsoContainsGate.passes(chunk, mapDash, "[chan]:", true));
	}

	@Test
	public void regexFindOnTheLine() {
		String line = "[chan]: title -- spam";
		assertTrue(AlsoContainsGate.passes(line, 0, "\\[chan\\]:", false));
		assertFalse(AlsoContainsGate.passes(line, 0, "\\[other\\]:", false));
	}

	@Test
	public void brokenRegexFailsClosed() {
		assertFalse(AlsoContainsGate.passes("hello --", 6, "[unterminated", false));
	}

	@Test
	public void lineAtSplitsOnNewline() {
		String chunk = "aaa\nbbb\nccc";
		assertEquals("aaa", AlsoContainsGate.lineAt(chunk, 1));
		assertEquals("bbb", AlsoContainsGate.lineAt(chunk, 4));
		assertEquals("ccc", AlsoContainsGate.lineAt(chunk, 8));
	}
}
