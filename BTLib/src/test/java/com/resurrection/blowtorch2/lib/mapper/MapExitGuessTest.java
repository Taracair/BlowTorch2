package com.resurrection.blowtorch2.lib.mapper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The guessed flag is what lets an invented reverse exit be taken back without
 * touching anything the player made, so its default matters as much as its use.
 */
public class MapExitGuessTest {

	@Test
	public void exitsAreNotGuessesUnlessSaidSo() {
		assertFalse(new MapExit("a", "b", "n").isGuessed());
		assertFalse(new MapExit("a", "b", "n", false, "s").isGuessed());
	}

	/**
	 * A reverse hint is not a marker of a guess: exits the player links carry
	 * one too, so treating it as one would delete real connections when reading
	 * a map written before the flag existed.
	 */
	@Test
	public void aReverseHintDoesNotImplyAGuess() {
		MapExit playerLinked = new MapExit("a", "b", "w", false, "e");
		assertFalse(playerLinked.isGuessed());
	}

	@Test
	public void theFlagSurvivesBeingSet() {
		MapExit e = new MapExit("a", "b", "n", false, "s");
		e.setGuessed(true);
		assertTrue(e.isGuessed());
		e.setGuessed(false);
		assertFalse(e.isGuessed());
	}
}
