package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

/** Completing from what the game just said. */
public class WordSuggestionsTest {

	@Test
	public void theCaseThisExistsFor() {
		// Gboard would offer grid / grim / grip. The mob is grizzled.
		WordSuggestions w = new WordSuggestions();
		w.learn("A grizzled cave troll lumbers in.\n");
		assertEquals(java.util.Arrays.asList("grizzled"), w.suggest("gri", 5));
	}

	@Test
	public void newestFirst() {
		WordSuggestions w = new WordSuggestions();
		w.learn("trollop\n");
		w.learn("trolley\n");
		w.learn("trollface\n");
		assertEquals(java.util.Arrays.asList("trollface", "trolley", "trollop"),
				w.suggest("troll", 5));
	}

	@Test
	public void seeingAWordAgainMakesItRecentAgain() {
		WordSuggestions w = new WordSuggestions();
		w.learn("grizzled\n");
		w.learn("grimoire\n");
		w.learn("grizzled\n");
		assertEquals("grizzled", w.suggest("gri", 5).get(0));
	}

	@Test
	public void spellingIsKeptButMatchingIgnoresCase() {
		WordSuggestions w = new WordSuggestions();
		w.learn("Tonkatsu says hello\n");
		assertEquals(java.util.Arrays.asList("Tonkatsu"), w.suggest("tonk", 5));
		assertEquals(java.util.Arrays.asList("Tonkatsu"), w.suggest("TONK", 5));
	}

	@Test
	public void aWordAlreadyTypedInFullIsNotOfferedBack() {
		WordSuggestions w = new WordSuggestions();
		w.learn("grizzled\n");
		assertTrue(w.suggest("grizzled", 5).isEmpty());
	}

	@Test
	public void shortWordsAndShortPrefixesAreIgnored() {
		WordSuggestions w = new WordSuggestions();
		w.learn("the cat sat on a mat\n");
		assertEquals(0, w.size());
		w.learn("grizzled\n");
		assertTrue("one letter matches everything and helps nobody",
				w.suggest("g", 5).isEmpty());
	}

	@Test
	public void numbersAreNotVocabulary() {
		WordSuggestions w = new WordSuggestions();
		w.learn("12345 6789\n");
		assertEquals(0, w.size());
		w.learn("450hp\n");
		assertEquals(1, w.size());
	}

	@Test
	public void apostrophesAndHyphensStayInsideAWord() {
		WordSuggestions w = new WordSuggestions();
		w.learn("the gnarled oaken staff of Y'sarn-kel\n");
		assertEquals(java.util.Arrays.asList("Y'sarn-kel"), w.suggest("y'sa", 5));
	}

	@Test
	public void theStoreIsBounded() {
		WordSuggestions w = new WordSuggestions(3);
		w.learn("aaaa bbbb cccc dddd\n");
		assertEquals(3, w.size());
		assertTrue("the oldest fell out", w.suggest("aaa", 5).isEmpty());
		assertEquals(java.util.Arrays.asList("dddd"), w.suggest("ddd", 5));
	}

	@Test
	public void aMistypedWordStillFindsItself() {
		WordSuggestions w = new WordSuggestions();
		w.setLooseMatching(true);
		w.learn("A grizzled cave troll lumbers in.\n");
		assertEquals(java.util.Arrays.asList("grizzled"), w.suggest("grzld", 5));
	}

	@Test
	public void looseMatchingIsOffUntilAskedFor() {
		WordSuggestions w = new WordSuggestions();
		w.learn("A grizzled cave troll lumbers in.\n");
		assertTrue(w.suggest("grzld", 5).isEmpty());
	}

	@Test
	public void anExactPrefixIsNeverDisplacedByALooseMatch() {
		WordSuggestions w = new WordSuggestions();
		w.setLooseMatching(true);
		// "grim" is an exact prefix of grimoire; it is also a subsequence of
		// "granite-marked". The accurate typist must not see the second one.
		w.learn("grimoire granite-marked\n");
		assertEquals(java.util.Arrays.asList("grimoire"), w.suggest("grim", 5));
	}

	@Test
	public void aLooseMatchNeedsTheFirstLetterAndSomeLength() {
		WordSuggestions w = new WordSuggestions();
		w.setLooseMatching(true);
		w.learn("grizzled\n");
		assertTrue("wrong first letter is not a typo, it is a different word",
				w.suggest("rzld", 5).isEmpty());
		assertTrue("three letters match half the vocabulary",
				w.suggest("gzl", 5).isEmpty());
	}

	@Test
	public void aWordOlderThanTheWindowIsGone() {
		WordSuggestions w = new WordSuggestions();
		w.setMaxLines(3);
		w.learn("grizzled\n");
		w.learn("filler\nfiller\nfiller\n");
		assertTrue("three lines have gone by", w.suggest("griz", 5).isEmpty());
	}

	@Test
	public void aWordSeenAgainInsideTheWindowSurvives() {
		WordSuggestions w = new WordSuggestions();
		w.setMaxLines(3);
		w.learn("grizzled\n");
		w.learn("filler\n");
		// Said again, so its stamp moves forward with it.
		w.learn("grizzled\n");
		w.learn("filler\nfiller\n");
		assertEquals(java.util.Arrays.asList("grizzled"), w.suggest("griz", 5));
	}

	@Test
	public void theWindowCountsLinesNotWords() {
		WordSuggestions w = new WordSuggestions();
		w.setMaxLines(2);
		w.learn("grizzled\n");
		// One very wide line. Under a word count this would evict grizzled;
		// under a line window it is one line and does not.
		w.learn("alpha bravo charlie delta echo foxtrot golf hotel india\n");
		assertEquals(java.util.Arrays.asList("grizzled"), w.suggest("griz", 5));
	}

	@Test
	public void wordsOnTheUnfinishedLineAreAlreadyAvailable() {
		// The prompt, and any line the world has not terminated yet.
		WordSuggestions w = new WordSuggestions();
		w.learn("A grizzled cave troll");
		assertEquals(java.util.Arrays.asList("grizzled"), w.suggest("griz", 5));
		assertEquals(0, w.linesSeen());
	}

	@Test
	public void aZeroWindowKeepsEverythingTheSessionSaid() {
		WordSuggestions w = new WordSuggestions();
		w.setMaxLines(0);
		w.learn("grizzled\n");
		for (int i = 0; i < 500; i++) {
			w.learn("filler\n");
		}
		assertEquals(java.util.Arrays.asList("grizzled"), w.suggest("griz", 5));
	}

	@Test
	public void shrinkingTheWindowAppliesAtOnce() {
		WordSuggestions w = new WordSuggestions();
		w.learn("grizzled\n");
		w.learn("filler\nfiller\nfiller\n");
		assertEquals(java.util.Arrays.asList("grizzled"), w.suggest("griz", 5));
		w.setMaxLines(2);
		assertTrue("the player narrowed it while playing", w.suggest("griz", 5).isEmpty());
	}

	@Test
	public void maxLimitsWhatComesBack() {
		WordSuggestions w = new WordSuggestions();
		w.learn("troll1x trollax trollbx trollcx\n");
		List<String> out = w.suggest("troll", 2);
		assertEquals(2, out.size());
	}

	@Test
	public void nothingLearnedMeansNothingOffered() {
		WordSuggestions w = new WordSuggestions();
		assertTrue(w.suggest("gri", 5).isEmpty());
		w.learn(null);
		w.learn("");
		assertEquals(0, w.size());
	}

	@Test
	public void wordBeforeTheCaretIsWhatGetsCompleted() {
		assertEquals("gri", WordSuggestions.wordBefore("k gri", 5));
		assertEquals("", WordSuggestions.wordBefore("k ", 2));
		assertEquals("kill", WordSuggestions.wordBefore("kill", 4));
		// Caret in the middle of a word completes only what is behind it.
		assertEquals("gr", WordSuggestions.wordBefore("k grizzled", 4));
		assertEquals("", WordSuggestions.wordBefore(null, 3));
	}

	@Test
	public void completingReplacesThePartialWordAndSpacesIt() {
		WordSuggestions.Completion c =
				WordSuggestions.complete("k gri", 5, "grizzled");
		assertEquals("k grizzled ", c.text());
		assertEquals("k grizzled ".length(), c.caret());
	}

	@Test
	public void completingInTheMiddleKeepsWhatFollows() {
		WordSuggestions.Completion c =
				WordSuggestions.complete("k gri troll", 5, "grizzled");
		assertEquals("k grizzled troll", c.text());
		// No second space: one was already there, and the caret sits before it.
		assertEquals("k grizzled".length(), c.caret());
	}

	@Test
	public void twoCompletionsBuildOneCommand() {
		WordSuggestions.Completion first =
				WordSuggestions.complete("k gri", 5, "grizzled");
		WordSuggestions.Completion second =
				WordSuggestions.complete(first.text(), first.caret(), "troll");
		assertEquals("k grizzled troll ", second.text());
	}

	@Test
	public void completingNothingChangesNothing() {
		assertEquals("k gri", WordSuggestions.complete("k gri", 5, null).text());
		assertEquals("k gri", WordSuggestions.complete("k gri", 5, "").text());
	}

	@Test
	public void anOutOfRangeCaretIsClamped() {
		assertEquals("kill ", WordSuggestions.complete("k", 99, "kill").text());
		assertEquals("kill k", WordSuggestions.complete("k", -1, "kill").text());
	}
}
