package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

public class HistoryPrefixTest {

	@Test
	public void upFindsAnOlderLineThatStartsWithWhatWasTyped() {
		HistoryPrefix.Step first = HistoryPrefix.step(
				Arrays.asList("kill orc", "kill goblin", "look"), "ki", -1, true);
		assertEquals(0, first.index);
		assertEquals("kill orc", first.line);
		HistoryPrefix.Step older = HistoryPrefix.step(
				Arrays.asList("kill orc", "kill goblin", "look"), "ki", first.index, true);
		assertEquals("kill goblin", older.line);
		HistoryPrefix.Step back = HistoryPrefix.step(
				Arrays.asList("kill orc", "kill goblin", "look"), "ki", older.index, false);
		assertEquals("kill orc", back.line);
		assertNull(HistoryPrefix.step(
				Arrays.asList("kill orc", "kill goblin", "look"), "ki", older.index, true).line);
		HistoryPrefix.Step draft = HistoryPrefix.step(
				Arrays.asList("kill orc", "kill goblin", "look"), "ki", 0, false);
		assertEquals(-1, draft.index);
		assertEquals("ki", draft.line);
		assertNull(HistoryPrefix.step(
				Arrays.asList("kill orc"), "ki", -1, false).line);
		assertFalse(HistoryPrefix.ended("ki", "kill orc", "kill orc"));
		assertTrue(HistoryPrefix.ended("ki", "kill orc", "kill orcs"));
		assertFalse(HistoryPrefix.ended(null, "kill orc", "x"));
	}

	@Test
	public void theLineAlreadyShowingIsNotTheFirstMatch() {
		HistoryPrefix.Step step = HistoryPrefix.step(
				Arrays.asList("kill goblin", "kill goblin now"), "kill goblin", -1, true);
		assertEquals("kill goblin now", step.line);
		assertNull(HistoryPrefix.step(Arrays.asList("look"), "", -1, true).line);
	}
}
