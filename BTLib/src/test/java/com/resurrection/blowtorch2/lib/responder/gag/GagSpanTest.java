package com.resurrection.blowtorch2.lib.responder.gag;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** How many lines a gag takes when its pattern spans a block. */
public class GagSpanTest {

	@Test
	public void aSingleLineMatchTakesOneLine() {
		assertEquals(1, GagAction.linesSpanned("[chatnet] Tonkatsu says, \"sleeby\""));
	}

	@Test
	public void aTwoLineMatchTakesTwo() {
		assertEquals(2, GagAction.linesSpanned("gold: 12\nsilver: 40"));
	}

	@Test
	public void aThreeLineBlockTakesThree() {
		assertEquals(3, GagAction.linesSpanned("+------+\n| SALE |\n+------+"));
	}

	@Test
	public void aMatchEndingOnItsNewlineStillCountsThatLineOnce() {
		// The match may or may not include the closing newline depending on how
		// the pattern was written; a trailing one must not conjure an extra line
		// to swallow.
		assertEquals(2, GagAction.linesSpanned("first\nsecond"));
		assertEquals(2, GagAction.linesSpanned("first\n"));
	}

	@Test
	public void nothingMatchedStillMeansOneLine() {
		assertEquals(1, GagAction.linesSpanned(""));
		assertEquals(1, GagAction.linesSpanned(null));
	}
}
