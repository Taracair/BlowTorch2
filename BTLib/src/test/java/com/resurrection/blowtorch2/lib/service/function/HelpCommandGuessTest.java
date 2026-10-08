package com.resurrection.blowtorch2.lib.service.function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;

import org.junit.Test;

public class HelpCommandGuessTest {

	@Test
	public void levenshteinCountsOneInsertForLastlst() {
		assertEquals(3, HelpCommandGuess.distance("kitten", "sitting"));
		assertEquals(1, HelpCommandGuess.distance("lastlst", "lastlist"));
		assertEquals(1, HelpCommandGuess.distance("lastlist", "lastlst"));
		assertEquals(0, HelpCommandGuess.distance("lastlist", "lastlist"));
	}

	@Test
	public void lastlstSuggestsLastlist() {
		assertEquals("lastlist", HelpCommandGuess.pick("lastlst"));
		assertEquals("Did you mean \".lastlist\"?\n",
				HelpCommandGuess.didYouMean(HelpCommandGuess.pick("lastlst")));
	}

	@Test
	public void zzzzSuggestsNothing() {
		assertNull(HelpCommandGuess.pick("zzzz"));
		assertNull(HelpCommandGuess.didYouMean(null));
	}

	@Test
	public void distanceOneAlwaysWinsEvenForAShortName() {
		assertEquals("run", HelpCommandGuess.pick("rn", Arrays.asList("run")));
		assertEquals("kb", HelpCommandGuess.pick("kbb"));
	}

	@Test
	public void distanceTwoNeedsANameOfAtLeastFiveCharacters() {
		assertEquals(2, HelpCommandGuess.distance("hxyp", "help"));
		assertNull(HelpCommandGuess.pick("hxyp", Arrays.asList("help")));
		assertNull(HelpCommandGuess.pick("ab", Arrays.asList("abcd")));
		assertEquals("abcde", HelpCommandGuess.pick("abc", Arrays.asList("abcde")));
		assertEquals("commands", HelpCommandGuess.pick("comand"));
	}

	@Test
	public void equalDistanceDoesNotGuess() {
		assertNull(HelpCommandGuess.pick("caz", Arrays.asList("cat", "car")));
		assertNull(HelpCommandGuess.pick("abc", Arrays.asList("abcde", "abcfg")));
		assertNull(HelpCommandGuess.pick("suggeston"));
	}

	@Test
	public void smallerDistanceBeatsAFartherAcceptedName() {
		assertEquals("abcde",
				HelpCommandGuess.pick("abcd", Arrays.asList("abcze", "abcde")));
		assertEquals("protocols", HelpCommandGuess.pick("protcol"));
		assertEquals("protocols", HelpCommandGuess.pick("protocoll"));
	}

	@Test
	public void exactNameIsNotAGuess() {
		assertNull(HelpCommandGuess.pick("help", Arrays.asList("help")));
		assertEquals("cat", HelpCommandGuess.pick("caz", Arrays.asList("cat", "cat")));
	}

	@Test
	public void blankOrMissingInputSuggestsNothing() {
		assertNull(HelpCommandGuess.pick(null));
		assertNull(HelpCommandGuess.pick(""));
		assertNull(HelpCommandGuess.pick("lastlst", null));
	}

	@Test
	public void adjacentSwapCountsAsOneEdit() {
		assertEquals(1, HelpCommandGuess.distance("hlep", "help"));
		assertEquals(1, HelpCommandGuess.distance("latslist", "lastlist"));
		assertEquals("help", HelpCommandGuess.pick("hlep", Arrays.asList("help")));
		assertEquals("lastlist", HelpCommandGuess.pick("latslist"));
	}

	@Test
	public void aLongNameAllowsTwoAndThreeEditsButNotFour() {
		assertEquals(2, HelpCommandGuess.distance("lxstlisx", "lastlist"));
		assertEquals(3, HelpCommandGuess.distance("lxstlixx", "lastlist"));
		assertEquals(5, HelpCommandGuess.distance("lxxxlixx", "lastlist"));
		assertEquals("lastlist",
				HelpCommandGuess.pick("lxstlisx", Arrays.asList("lastlist")));
		assertEquals("lastlist",
				HelpCommandGuess.pick("lxstlixx", Arrays.asList("lastlist")));
		assertNull(HelpCommandGuess.pick("lxxxlixx", Arrays.asList("lastlist")));
	}

	@Test
	public void oneExtraLetterOnAShortCommandBeatsALongerName() {
		assertEquals("last", HelpCommandGuess.pick("lastl"));
	}

	@Test
	public void lettersPastThatShortCommandStillFindTheLongName() {
		assertEquals("lastlist", HelpCommandGuess.pick("lastls"));
		assertEquals("lastlist", HelpCommandGuess.pick("lastli"));
	}
}
