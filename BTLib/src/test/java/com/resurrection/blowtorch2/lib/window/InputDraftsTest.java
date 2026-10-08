package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

/**
 * One input widget, one unsent draft per open world.
 */
public class InputDraftsTest {

	@Test
	public void typeOnASwitchToBAndBackKeepsBoth() {
		InputDrafts drafts = new InputDrafts();
		drafts.pinShowing("world-a");

		InputDrafts.Draft toB = drafts.switchTo("world-b", "k gob", 2, 2);
		assertEquals("", toB.text());
		assertEquals(0, toB.selectionStart());
		assertEquals(0, toB.selectionEnd());
		assertEquals("world-b", drafts.showing());

		InputDrafts.Draft toA = drafts.switchTo("world-a", "look", 4, 4);
		assertEquals("k gob", toA.text());
		assertEquals(2, toA.selectionStart());
		assertEquals(2, toA.selectionEnd());

		InputDrafts.Draft toBAgain = drafts.switchTo("world-b", "k gob", 2, 2);
		assertEquals("look", toBAgain.text());
		assertEquals(4, toBAgain.selectionStart());
		assertEquals(4, toBAgain.selectionEnd());
	}

	@Test
	public void aSelectionInTheMiddleOfTheLineComesBack() {
		InputDrafts drafts = new InputDrafts();
		drafts.pinShowing("world-a");
		drafts.switchTo("world-b", "abcdef", 1, 4);

		InputDrafts.Draft back = drafts.switchTo("world-a", "other", 0, 5);
		assertEquals("abcdef", back.text());
		assertEquals(1, back.selectionStart());
		assertEquals(4, back.selectionEnd());
	}

	@Test
	public void sendingOnAClearsOnlyThatWorldsDraft() {
		InputDrafts drafts = new InputDrafts();
		drafts.pinShowing("world-a");
		drafts.switchTo("world-b", "unsent", 3, 6);
		drafts.switchTo("world-a", "from-b", 6, 6);

		InputDrafts.Draft b = drafts.switchTo("world-b", "", 0, 0);
		assertEquals("from-b", b.text());
		assertEquals(6, b.selectionStart());

		InputDrafts.Draft a = drafts.switchTo("world-a", "from-b", 6, 6);
		assertSame(InputDrafts.Draft.EMPTY, a);
	}

	@Test
	public void theSameWorldDoesNotTouchTheBar() {
		InputDrafts drafts = new InputDrafts();
		drafts.pinShowing("world-a");
		assertNull(drafts.switchTo("world-a", "hello", 5, 5));
		assertEquals("world-a", drafts.showing());

		InputDrafts.Draft toB = drafts.switchTo("world-b", "hello", 5, 5);
		assertEquals("", toB.text());
		InputDrafts.Draft back = drafts.switchTo("world-a", "", 0, 0);
		assertEquals("hello", back.text());
		assertEquals(5, back.selectionStart());
	}

	@Test
	public void aLaterPinDoesNotMoveTheDraftOffTheWorldOnScreen() {
		InputDrafts drafts = new InputDrafts();
		drafts.pinShowing("world-a");
		drafts.pinShowing("world-b");

		InputDrafts.Draft arrived = drafts.switchTo("world-b", "half", 2, 4);
		assertEquals("", arrived.text());
		InputDrafts.Draft back = drafts.switchTo("world-a", "", 0, 0);
		assertEquals("half", back.text());
		assertEquals(2, back.selectionStart());
		assertEquals(4, back.selectionEnd());
	}

	@Test
	public void closingAWorldDropsItsDraft() {
		InputDrafts drafts = new InputDrafts();
		drafts.pinShowing("world-a");
		drafts.switchTo("world-b", "keep-a", 6, 6);
		drafts.drop("world-a");

		InputDrafts.Draft back = drafts.switchTo("world-a", "keep-b", 6, 6);
		assertSame(InputDrafts.Draft.EMPTY, back);
		InputDrafts.Draft b = drafts.switchTo("world-b", "", 0, 0);
		assertEquals("keep-b", b.text());
	}

	@Test
	public void aBlankDestinationLeavesTheBarAlone() {
		InputDrafts drafts = new InputDrafts();
		drafts.pinShowing("world-a");
		assertNull(drafts.switchTo("", "typed", 5, 5));
		assertNull(drafts.switchTo(null, "typed", 5, 5));
		assertEquals("world-a", drafts.showing());
	}
}
