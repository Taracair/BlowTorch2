package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.resurrection.blowtorch2.lib.window.InputBarEscape.Kind;
import com.resurrection.blowtorch2.lib.window.InputBarEscape.Recall;
import com.resurrection.blowtorch2.lib.window.InputBarEscape.Result;

/** Escape on the bar. The examples are the shortcut spec, not a previous run. */
public class InputBarEscapeTest {

	@Test
	public void aRecalledLinePutsTheDraftBack() {
		Result restored = InputBarEscape.decide(true, "ki", "kill goblin");
		assertEquals(Kind.RESTORE, restored.kind);
		assertEquals("ki", restored.text);
		Result emptyDraft = InputBarEscape.decide(true, "", "kill goblin");
		assertEquals(Kind.RESTORE, emptyDraft.kind);
		assertEquals("", emptyDraft.text);
	}

	@Test
	public void otherTextClearsAndAnEmptyBarHides() {
		Result clear = InputBarEscape.decide(false, "", "ki");
		assertEquals(Kind.CLEAR, clear.kind);
		Result hide = InputBarEscape.decide(false, "ki", "");
		assertEquals(Kind.HIDE, hide.kind);
	}

	@Test
	public void theFirstStepKeepsWhatWasTyped() {
		Recall first = InputBarEscape.note(false, "", "ki", "kill goblin");
		assertTrue(first.recalling);
		assertEquals("ki", first.draft);
		Recall later = InputBarEscape.note(true, first.draft, "kill goblin", "kill goblin king");
		assertTrue(later.recalling);
		assertEquals("ki", later.draft);
		Recall back = InputBarEscape.note(true, "ki", "kill goblin", "");
		assertFalse(back.recalling);
		assertEquals("ki", back.draft);
	}
}
