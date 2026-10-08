package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Drawn ghost spacing, and the real space inserted when a suggestion is taken. */
public class GhostSpacingTest {

	@Test
	public void theRestOfTheWordStaysGlued() {
		assertTrue(GhostSpacing.continues("gri", "grizzled"));
		assertEquals("zzled", GhostSpacing.draw("k gri", "zzled", true));
		GhostSpacing.Accepted taken = GhostSpacing.accept("k gri", 5, "grizzled");
		assertEquals("k grizzled ", taken.text());
		assertEquals("k grizzled ".length(), taken.caret());
	}

	@Test
	public void aDifferentWordIsDrawnWithOneSpaceAndInsertedForReal() {
		assertFalse(GhostSpacing.continues("kill", "goblin"));
		assertEquals(" goblin", GhostSpacing.draw("kill", "goblin", false));
		// The drawn space is not part of the word passed to accept.
		GhostSpacing.Accepted taken = GhostSpacing.accept("kill", 4, "goblin");
		assertEquals("kill goblin ", taken.text());
		assertEquals("kill goblin ".length(), taken.caret());
	}

	@Test
	public void aSpaceAlreadyTypedIsNotDrawnAgain() {
		assertEquals("goblin", GhostSpacing.draw("kill ", "goblin", false));
		assertEquals("kill goblin ", GhostSpacing.accept("kill ", 5, "goblin").text());
	}

	@Test
	public void aPhraseTailThatAlreadyStartsWithASpaceIsLeftAlone() {
		assertEquals(" goblin", GhostSpacing.draw("kill", " goblin", true));
		assertEquals("kill goblin ",
				GhostSpacing.accept("kill", 4, "kill goblin").text());
	}

	@Test
	public void anOpenerDoesNotGainASpace() {
		assertEquals("foo", GhostSpacing.draw("(", "foo", false));
		assertEquals("(foo ", GhostSpacing.accept("(", 1, "foo").text());
	}

	@Test
	public void punctuationBeforeTheCaretGetsOneSpace() {
		assertEquals(" goblin", GhostSpacing.draw("kill.", "goblin", false));
		assertEquals("kill. goblin ", GhostSpacing.accept("kill.", 5, "goblin").text());
	}

	@Test
	public void aCommaSticksToTheWordInFront() {
		assertEquals(",", GhostSpacing.draw("kill", ",", false));
		assertEquals("kill, ", GhostSpacing.accept("kill", 4, ",").text());
	}

	@Test
	public void aNearMissReplacesTheTypedToken() {
		assertEquals(" grizzled", GhostSpacing.draw("k grzld", "grizzled", false));
		assertEquals("k grizzled ", GhostSpacing.accept("k grzld", 7, "grizzled").text());
		assertEquals("look grizzled now",
				GhostSpacing.accept("look grzizled now", 10, "grizzled").text());
		assertEquals("helmet ", GhostSpacing.accept("helmte", 6, "helmet").text());
		assertEquals("ironhelmet ",
				GhostSpacing.accept("onhelmet", 8, "ironhelmet").text());
		assertEquals("grizzled cave troll ",
				GhostSpacing.accept("grzld", 5, "grizzled cave troll").text());
	}

	@Test
	public void aHyphenMarkIsNotASpaceAndARealSpaceStillCounts() {
		assertEquals(" goblin",
				GhostSpacing.draw("kill" + InputHyphenBreaks.MARK, "goblin", false));
		assertEquals("goblin",
				GhostSpacing.draw("kill " + InputHyphenBreaks.MARK, "goblin", false));
	}

	@Test
	public void packedExtrasNeedAGapOnlyWhenTheLineDoesNotAlreadyEndOnOne() {
		assertTrue(GhostSpacing.gapBeforeNewWord("kill"));
		assertTrue(GhostSpacing.gapBeforeNewWord("kill."));
		assertFalse(GhostSpacing.gapBeforeNewWord("kill "));
		assertFalse(GhostSpacing.gapBeforeNewWord(""));
		assertFalse(GhostSpacing.gapBeforeNewWord("("));
		assertFalse(GhostSpacing.gapBeforeNewWord("kill " + InputHyphenBreaks.MARK));
	}

	@Test
	public void anEmptyBodyDrawsNothing() {
		assertEquals(null, GhostSpacing.draw("kill", null, false));
		assertEquals("", GhostSpacing.draw("kill", "", false));
		assertEquals("kill", GhostSpacing.accept("kill", 4, null).text());
		assertEquals("kill", GhostSpacing.accept("kill", 4, "  ").text());
	}
}
