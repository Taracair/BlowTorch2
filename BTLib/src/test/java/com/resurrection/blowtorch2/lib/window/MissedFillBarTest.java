package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * A missing {@code .last input} slot restores the bar from before Send.
 * Keep Last off would have cleared it. Keep Last on would have selected
 * the recall line.
 */
public class MissedFillBarTest {

	@Test
	public void aMissingSlotKeepsTheLineFromBeforeSend() {
		MissedFillBar.State before = new MissedFillBar.State(".5last input", 12, 12, 0, false);
		MissedFillBar.State back = MissedFillBar.afterMissingSlot(before);
		assertEquals(".5last input", back.text());
		assertEquals(12, back.selectionStart());
		assertEquals(12, back.selectionEnd());
		assertEquals(0, back.keepLastReplaceLength());
		assertFalse(back.historyWidgetKept());
	}

	@Test
	public void aMissingSlotDoesNotSelectTheRecallLine() {
		MissedFillBar.State before = new MissedFillBar.State(".9last input", 11, 11, 0, false);
		MissedFillBar.State back = MissedFillBar.afterMissingSlot(before);
		assertEquals(".9last input", back.text());
		assertEquals(11, back.selectionStart());
		assertEquals(11, back.selectionEnd());
		assertFalse(back.selectionStart() == 0 && back.selectionEnd() == back.text().length()
				&& back.selectionStart() != back.selectionEnd());
	}

	@Test
	public void aSelectionOnThePreviousLineComesBack() {
		MissedFillBar.State before = new MissedFillBar.State("west", 1, 3, 0, false);
		MissedFillBar.State back = MissedFillBar.afterMissingSlot(before);
		assertEquals("west", back.text());
		assertEquals(1, back.selectionStart());
		assertEquals(3, back.selectionEnd());
	}

	@Test
	public void selectionIndexesAreClampedToTheLine() {
		MissedFillBar.State back = MissedFillBar.afterMissingSlot(
				new MissedFillBar.State("look", -1, 40, 9, true));
		assertEquals("look", back.text());
		assertEquals(0, back.selectionStart());
		assertEquals(4, back.selectionEnd());
		assertEquals(4, back.keepLastReplaceLength());
		assertTrue(back.historyWidgetKept());
	}
}
