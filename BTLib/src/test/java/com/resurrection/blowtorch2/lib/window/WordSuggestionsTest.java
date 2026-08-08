package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

	// --- Phrases (C1). Off by default, so every test above still describes
	// --- exactly what a player who has not asked for this gets.

	@Test
	public void phrasesAreOffUntilAskedFor() {
		WordSuggestions w = new WordSuggestions();
		assertFalse(w.isPhrases());
		w.learn("A grizzled cave troll lumbers in.\n");
		assertEquals(java.util.Arrays.asList("grizzled"), w.suggest("gri", 5));
	}

	@Test
	public void aPhraseOffersTheWholeNameAndThenTheWordAlone() {
		WordSuggestions w = new WordSuggestions();
		w.setPhrases(true);
		w.learn("A grizzled cave troll lumbers in.\n");
		// The phrase first: that is the part which is slow to type on a phone.
		// Capped at three words, so the verb after the name is not swallowed.
		assertEquals(java.util.Arrays.asList("grizzled cave troll", "grizzled"),
				w.suggest("gri", 5));
	}

	@Test
	public void aPhraseNeverRunsPastTheEndOfALine() {
		WordSuggestions w = new WordSuggestions();
		w.setPhrases(true);
		w.learn("gnarled oaken\nstaff of power\n");
		assertEquals(java.util.Arrays.asList("gnarled oaken", "gnarled"),
				w.suggest("gnar", 5));
	}

	@Test
	public void aDroppedShortWordBreaksThePhraseRatherThanBeingSkipped() {
		WordSuggestions w = new WordSuggestions();
		w.setPhrases(true);
		// "of" is below MIN_WORD_LENGTH and is not stored. Joining across it
		// would offer "sword power", which the world never said.
		w.learn("a sword of power\n");
		assertEquals(java.util.Arrays.asList("sword"), w.suggest("swo", 5));
	}

	@Test
	public void aWordCutInHalfByThePacketBoundaryIsStillLearnedWhole() {
		WordSuggestions w = new WordSuggestions();
		// Text arrives as TCP chunks, so this is one line split in two.
		w.learn("A griz");
		w.learn("zled cave troll lumbers in.\n");
		// Not "griz", which is what learning each chunk on its own would teach.
		assertEquals(java.util.Arrays.asList("grizzled"), w.suggest("gri", 5));
	}

	@Test
	public void aPhraseSurvivesThePacketBoundaryToo() {
		WordSuggestions w = new WordSuggestions();
		w.setPhrases(true);
		w.learn("A grizzled ca");
		w.learn("ve troll lumbers in.\n");
		assertEquals(java.util.Arrays.asList("grizzled cave troll", "grizzled"),
				w.suggest("gri", 5));
	}

	@Test
	public void aWordSeenAgainElsewhereTakesItsNewNeighbour() {
		WordSuggestions w = new WordSuggestions();
		w.setPhrases(true);
		w.learn("gnarled oaken staff\n");
		w.learn("gnarled iron gate\n");
		assertEquals(java.util.Arrays.asList("gnarled iron gate", "gnarled"),
				w.suggest("gnar", 5));
	}

	@Test
	public void aWordSeenAgainForgetsWhatUsedToFollowIt() {
		WordSuggestions w = new WordSuggestions();
		w.setPhrases(true);
		// "mirror" is seen twice on this line and the second sighting is the
		// end of it, so nothing follows it any more. The old successor is not
		// kept: it would offer "mirror image" for a mirror that is now on its
		// own, and it is also what would let a phrase loop back into itself.
		w.learn("mirror image mirror\n");
		assertEquals(java.util.Arrays.asList("mirror"), w.suggest("mirr", 5));
		// Say it again with something after it and the phrase comes back.
		w.learn("mirror shield\n");
		assertEquals("mirror shield", w.suggest("mirr", 5).get(0));
	}

	@Test
	public void thePhraseIsWhatGoesIntoTheInputBar() {
		// complete() takes whatever was picked, so a phrase lands as one piece.
		WordSuggestions.Completion c =
				WordSuggestions.complete("k gri", 5, "grizzled cave troll");
		assertEquals("k grizzled cave troll ", c.text());
	}

	@Test
	public void clearingForgetsThePhrasesToo() {
		WordSuggestions w = new WordSuggestions();
		w.setPhrases(true);
		w.learn("grizzled cave troll\n");
		w.clear();
		w.learn("grizzled\n");
		assertEquals(java.util.Arrays.asList("grizzled"), w.suggest("gri", 5));
	}

	@Test
	public void rankingIsOffUntilAskedFor() {
		WordSuggestions w = new WordSuggestions();
		w.learn("kill the grizzled troll\n");
		w.learnCommand("kill troll");
		// Same answer at both ends of the line: the option is what changes it.
		assertEquals(w.suggest("k", 5), w.suggest("k", 5, true));
	}

	@Test
	public void atTheStartOfALineAWordUsedAsACommandComesFirst() {
		WordSuggestions w = new WordSuggestions();
		w.setRankByPosition(true);
		// The world said both. "kindle" is newer, so it leads without ranking.
		w.learn("You kill the troll.\nYou kindle a torch.\n");
		assertEquals("kindle", w.suggest("ki", 5).get(0));
		// The player has only ever typed "kill" as a command.
		w.learnCommand("kill troll");
		assertEquals("kill", w.suggest("ki", 5, true).get(0));
	}

	@Test
	public void awayFromTheStartAWordUsedAsATargetComesFirst() {
		WordSuggestions w = new WordSuggestions();
		w.setRankByPosition(true);
		w.learn("A troll waits.\nA trophy hangs here.\n");
		assertEquals("trophy", w.suggest("tro", 5).get(0));
		w.learnCommand("kill troll");
		// Mid-line the player is naming a thing, and "troll" is the word they
		// name things with.
		assertEquals("troll", w.suggest("tro", 5, false).get(0));
	}

	@Test
	public void rankingMovesSuggestionsAndNeverRemovesThem() {
		WordSuggestions w = new WordSuggestions();
		w.setRankByPosition(true);
		w.learn("kill kindle kitten kite\n");
		w.learnCommand("kill things");
		List<String> plain = w.suggest("ki", 10);
		List<String> ranked = w.suggest("ki", 10, true);
		assertEquals("kill", ranked.get(0));
		// Same set, different order — that is the whole contract.
		assertEquals(new java.util.HashSet<String>(plain),
				new java.util.HashSet<String>(ranked));
	}

	@Test
	public void whatFollowsSayIsProseAndNotATarget() {
		WordSuggestions w = new WordSuggestions();
		w.setRankByPosition(true);
		w.learn("A troll waits.\nA trophy hangs here.\n");
		// Chat fills the object store with ordinary English if it is let through.
		w.learnCommand("say we should kill the troll");
		assertEquals("trophy", w.suggest("tro", 5, false).get(0));
	}

	@Test
	public void aSpeechVerbIsStillAVerb() {
		WordSuggestions w = new WordSuggestions();
		w.setRankByPosition(true);
		w.learn("tell them.\nYou teleport away.\n");
		assertEquals("teleport", w.suggest("te", 5).get(0));
		w.learnCommand("tell bob meet me at the gate");
		// Cutting the line short at a speech verb must not cost the verb itself.
		assertEquals("tell", w.suggest("te", 5, true).get(0));
	}

	@Test
	public void aVerbShorterThanTheVocabularyKeepsIsStillRecognisedAsSpeech() {
		WordSuggestions w = new WordSuggestions();
		w.setRankByPosition(true);
		w.learn("A troll waits.\nA trophy hangs here.\n");
		// "say" is three letters, below what the vocabulary stores. If the
		// command side applied that same floor it would miss the speech verb
		// and learn the whole sentence as things.
		w.learnCommand("say we should kill the troll");
		assertEquals("trophy", w.suggest("tro", 5, false).get(0));
	}

	@Test
	public void aCommandTeachesNothingToCompleteWith() {
		WordSuggestions w = new WordSuggestions();
		w.setRankByPosition(true);
		// Only the world's words are offered back. Typing a name the world never
		// used must not make it completable.
		w.learnCommand("kill grizzled");
		assertTrue(w.suggest("gri", 5, false).isEmpty());
	}

	@Test
	public void punctuationAroundACommandWordIsNotPartOfIt() {
		WordSuggestions w = new WordSuggestions();
		w.setRankByPosition(true);
		w.learn("You kill the troll.\nYou kindle a torch.\n");
		w.learnCommand("kill, troll!");
		assertEquals("kill", w.suggest("ki", 5, true).get(0));
	}

	@Test
	public void aNewWorldForgetsHowThisOneWasPlayed() {
		WordSuggestions w = new WordSuggestions();
		w.setRankByPosition(true);
		w.learnCommand("kill troll");
		w.clear();
		w.learn("You kill the troll.\nYou kindle a torch.\n");
		// Nothing known about commands here, so newest-first stands.
		assertEquals("kindle", w.suggest("ki", 5, true).get(0));
	}

	@Test
	public void aSeedThatDoesNotEndInANewlineGluesItselfToWhatComesNext() {
		// Why Connection.seedVocabularyFromHistory terminates its seed. The
		// buffer dump handed over after a UI process death stops wherever the
		// world stopped talking, which is a prompt with no newline. Learned as
		// it stands, the tail waits in `pending` for a continuation that is not
		// coming from the same sentence at all.
		WordSuggestions glued = new WordSuggestions();
		glued.learn("You see a grizzled cave troll.\n> hp:100 man");
		glued.learn("ticore roars.\n");
		// "manticore" is what the two halves spell together. Nobody wrote it.
		assertEquals(java.util.Arrays.asList("manticore"), glued.suggest("mant", 5));

		WordSuggestions ended = new WordSuggestions();
		ended.learn("You see a grizzled cave troll.\n> hp:100 man\n");
		ended.learn("ticore roars.\n");
		assertTrue(ended.suggest("mant", 5).isEmpty());
	}

	@Test
	public void aSeedDoesNotStartAPhraseIntoTheFirstLiveLine() {
		// Same reason, for phrases: the last word of the seeded screen must not
		// become the head of a phrase that runs into the next thing the world
		// says minutes later.
		WordSuggestions w = new WordSuggestions();
		w.setPhrases(true);
		w.learn("a grizzled cave troll\n");
		w.learn("lumbers northward\n");
		assertEquals(java.util.Arrays.asList("troll"), w.suggest("trol", 5));
	}
}
