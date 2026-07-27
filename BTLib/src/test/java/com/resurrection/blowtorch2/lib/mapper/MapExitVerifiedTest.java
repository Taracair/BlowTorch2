package com.resurrection.blowtorch2.lib.mapper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * A newly built exit is a claim until somebody walks it. The default has to be
 * that way round, or exits invented by the mapper would count as checked the
 * moment they were invented.
 */
public class MapExitVerifiedTest {

	@Test
	public void aNewExitIsNotVerified() {
		assertFalse(new MapExit("a", "b", "e").isVerified());
		assertFalse(new MapExit("a", "b", "e", false, "w").isVerified());
	}

	@Test
	public void walkingItSetsTheFlag() {
		MapExit e = new MapExit("a", "b", "e");
		e.setVerified(true);
		assertTrue(e.isVerified());
	}

	/**
	 * The two directions are separate claims. Proving e out of a room says
	 * nothing about w coming back, which is the whole point of recording them
	 * one at a time.
	 */
	@Test
	public void verifyingOneDirectionLeavesTheOther() {
		MapExit out = new MapExit("beehives", "herbgarden", "e");
		MapExit back = new MapExit("herbgarden", "beehives", "w");
		out.setVerified(true);
		assertTrue(out.isVerified());
		assertFalse(back.isVerified());
	}

	/** A guess that turns out to be walkable stops being a guess. */
	@Test
	public void aWalkedGuessIsNoLongerAGuess() {
		MapExit guess = new MapExit("a", "b", "n", false, "s");
		guess.setGuessed(true);
		guess.setVerified(true);
		guess.setGuessed(false);
		assertTrue(guess.isVerified());
		assertFalse(guess.isGuessed());
	}
}
