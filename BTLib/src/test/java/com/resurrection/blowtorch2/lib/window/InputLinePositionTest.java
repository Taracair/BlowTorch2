package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Where the caret is in the input line, which is what decides whether the
 * suggestions treat what you are typing as a command or as its target.
 */
public class InputLinePositionTest {

	@Test
	public void theFirstWordOfALineIsTheCommand() {
		assertTrue(MainWindow.isAtLineStart("kil", 3, "kil"));
		assertTrue(MainWindow.isAtLineStart("   kil", 6, "kil"));
	}

	@Test
	public void afterTheFirstWordYouAreNamingSomething() {
		assertFalse(MainWindow.isAtLineStart("kill tro", 8, "tro"));
	}

	@Test
	public void aLineOpeningWithPunctuationStillStartsWithItsFirstWord() {
		// Found by a probe on the device: typing .help logged atLineStart=false
		// with the half-typed word handed to the pairing as the verb it follows.
		// The leading dot is not a word that has gone by.
		assertTrue(MainWindow.isAtLineStart(".hel", 4, "hel"));
		// The say alias on most worlds.
		assertTrue(MainWindow.isAtLineStart("'hel", 4, "hel"));
	}

	@Test
	public void aDotCommandStillHasATargetPosition() {
		assertFalse(MainWindow.isAtLineStart(".suggest ra", 11, "ra"));
	}

	@Test
	public void theLeadingVerbIsTheFirstWordWithoutItsPunctuation() {
		assertEquals("help", MainWindow.leadingVerb(".help"));
		assertEquals("kill", MainWindow.leadingVerb("Kill troll"));
		assertEquals("kill", MainWindow.leadingVerb("  kill,  troll"));
	}

	@Test
	public void theGhostSaysHowManyOtherSuggestionsThereAre() {
		// The ghost is one word by nature. A player who uses it without a bar
		// sees one word and concludes that is all there is — which is exactly
		// what happened, and why this mark exists.
		assertEquals("", MainWindow.moreMark(0, 0));
		assertEquals("", MainWindow.moreMark(1, 0));
		assertEquals(" +1", MainWindow.moreMark(2, 0));
		assertEquals(" +7", MainWindow.moreMark(8, 0));
	}

	@Test
	public void steppingThroughTheGhostSaysWhereYouAre() {
		// Holding the ghost walks the list. "+4" would not answer the question
		// that matters once you are walking, which is whether you have gone past
		// the one you wanted.
		assertEquals(" 2/6", MainWindow.moreMark(6, 1));
		assertEquals(" 6/6", MainWindow.moreMark(6, 5));
		// Back at the top it is a count again.
		assertEquals(" +5", MainWindow.moreMark(6, 0));
	}

	@Test
	public void anEmptyLineHasNoVerb() {
		assertEquals(null, MainWindow.leadingVerb(""));
		assertEquals(null, MainWindow.leadingVerb("   "));
		assertEquals(null, MainWindow.leadingVerb("..."));
	}
}
