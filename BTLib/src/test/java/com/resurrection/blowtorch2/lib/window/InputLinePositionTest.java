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
	public void anEmptyLineHasNoVerb() {
		assertEquals(null, MainWindow.leadingVerb(""));
		assertEquals(null, MainWindow.leadingVerb("   "));
		assertEquals(null, MainWindow.leadingVerb("..."));
	}

	@Test
	public void suggestClearIsRecognisedUnderEveryNameItAnswersTo() {
		assertTrue(MainWindow.isSuggestForgetCommand(".suggest clear"));
		assertTrue(MainWindow.isSuggestForgetCommand("  .Suggest  Forget  "));
		assertTrue(MainWindow.isSuggestForgetCommand(".complete clear"));
		assertTrue(MainWindow.isSuggestForgetCommand(".suggestions forget"));
	}

	@Test
	public void otherSuggestLinesAreNotAForget() {
		assertFalse(MainWindow.isSuggestForgetCommand(".suggest learned"));
		assertFalse(MainWindow.isSuggestForgetCommand(".suggest on"));
		assertFalse(MainWindow.isSuggestForgetCommand(".suggest clear now"));
		assertFalse(MainWindow.isSuggestForgetCommand("suggest clear"));
		assertFalse(MainWindow.isSuggestForgetCommand("kill troll"));
	}

	@Test
	public void keyboardFlushIsRecognisedUnderBothNames() {
		assertTrue(MainWindow.isKeyboardFlushCommand(".kb flush"));
		assertTrue(MainWindow.isKeyboardFlushCommand("  .Keyboard  Flush  "));
		assertTrue(MainWindow.isKeyboardFlushCommand(".keyboard flush"));
	}

	@Test
	public void otherKeyboardLinesAreNotAFlush() {
		assertFalse(MainWindow.isKeyboardFlushCommand(".kb clear"));
		assertFalse(MainWindow.isKeyboardFlushCommand(".kb flush now"));
		assertFalse(MainWindow.isKeyboardFlushCommand(".kb"));
		assertFalse(MainWindow.isKeyboardFlushCommand("kb flush"));
		assertFalse(MainWindow.isKeyboardFlushCommand("kill troll"));
	}
}
