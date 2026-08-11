package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Spacing and caret for a word tapped into the input bar. */
public class InputWordInsertTest {

	private static InputWordInsert.Result at(final String text, final int caret,
			final String word) {
		return InputWordInsert.apply(text, caret, caret, word);
	}

	@Test
	public void intoAnEmptyBarTheWordStandsAloneWithATrailingSpace() {
		InputWordInsert.Result r = at("", 0, "troll");
		assertEquals("troll ", r.text());
		assertEquals("troll ".length(), r.caret());
	}

	@Test
	public void afterATypedPrefixItSpacesItself() {
		// The whole point: "k" + tap troll must not become "ktroll".
		InputWordInsert.Result r = at("k", 1, "troll");
		assertEquals("k troll ", r.text());
		assertEquals("k troll ".length(), r.caret());
	}

	@Test
	public void anExistingTrailingSpaceIsNotDoubled() {
		assertEquals("k troll ", at("k ", 2, "troll").text());
	}

	@Test
	public void twoTapsInARowBuildOneCommand() {
		InputWordInsert.Result first = at("k", 1, "grizzled");
		InputWordInsert.Result second =
				at(first.text(), first.caret(), "troll");
		assertEquals("k grizzled troll ", second.text());
	}

	@Test
	public void insertingInTheMiddleKeepsBothSides() {
		// "kill  troll" with the caret between the two spaces.
		InputWordInsert.Result r = at("kill troll", 5, "big");
		assertEquals("kill big troll", r.text());
		assertEquals("kill big ".length(), r.caret());
	}

	@Test
	public void insertingBeforeExistingTextDoesNotGlueToIt() {
		InputWordInsert.Result r = at("troll", 0, "kill");
		assertEquals("kill troll", r.text());
		assertEquals("kill ".length(), r.caret());
	}

	@Test
	public void aSelectionIsReplacedByTheWord() {
		InputWordInsert.Result r = InputWordInsert.apply("kill rat", 5, 8, "troll");
		assertEquals("kill troll ", r.text());
	}

	@Test
	public void surroundingWhitespaceOnTheWordIsTrimmed() {
		assertEquals("troll ", at("", 0, "  troll  ").text());
	}

	@Test
	public void aBlankOrNullWordChangesNothing() {
		assertEquals("kill ", at("kill ", 5, "   ").text());
		assertEquals("kill ", at("kill ", 5, null).text());
	}

	@Test
	public void nullCurrentTextIsTreatedAsEmpty() {
		assertEquals("troll ", InputWordInsert.apply(null, 0, 0, "troll").text());
	}

	@Test
	public void anOutOfRangeCaretIsClampedRatherThanThrowing() {
		// getSelectionStart() returns -1 when the box has never had focus.
		assertEquals("troll kill", InputWordInsert.apply("kill", -1, -1, "troll").text());
		assertEquals("kill troll ", InputWordInsert.apply("kill", 99, 99, "troll").text());
	}

	@Test
	public void reversedSelectionBoundsStillWork() {
		InputWordInsert.Result r = InputWordInsert.apply("kill rat", 8, 5, "troll");
		assertEquals("kill troll ", r.text());
	}

	@Test
	public void aCommaAttachesToThePrecedingWord() {
		// The report: "slowo" + ".kb insert ," must not become "slowo , ".
		InputWordInsert.Result r = at("slowo", 5, ",");
		assertEquals("slowo, ", r.text());
		assertEquals("slowo, ".length(), r.caret());
	}

	@Test
	public void closingPunctuationAttachesWithoutALeadingSpace() {
		assertEquals("end. ", at("end", 3, ".").text());
		assertEquals("end! ", at("end", 3, "!").text());
		assertEquals("end? ", at("end", 3, "?").text());
		assertEquals("end; ", at("end", 3, ";").text());
		assertEquals("end: ", at("end", 3, ":").text());
		assertEquals("end) ", at("end", 3, ")").text());
		assertEquals("end] ", at("end", 3, "]").text());
		assertEquals("end} ", at("end", 3, "}").text());
		assertEquals("end` ", at("end", 3, "`").text());
		assertEquals("end\u00BB ", at("end", 3, "\u00BB").text());
		assertEquals("end\u201D ", at("end", 3, "\u201D").text());
		assertEquals("end\u2019 ", at("end", 3, "\u2019").text());
		assertEquals("end\u2026 ", at("end", 3, "\u2026").text());
		// ASCII quotes are also openers when trailing, so a quote-only insert
		// gets neither space: end" rather than end"  or end ".
		assertEquals("end'", at("end", 3, "'").text());
		assertEquals("end\"", at("end", 3, "\"").text());
	}

	@Test
	public void anOpenerLeavesNoTrailingSpaceForTheNextTap() {
		InputWordInsert.Result open = at("say", 3, "(");
		assertEquals("say (", open.text());
		assertEquals("say (".length(), open.caret());
		InputWordInsert.Result word = at(open.text(), open.caret(), "hello");
		assertEquals("say (hello ", word.text());
	}

	@Test
	public void openingCharactersSuppressTheTrailingSpace() {
		assertEquals("say (", at("say", 3, "(").text());
		assertEquals("say [", at("say", 3, "[").text());
		assertEquals("say {", at("say", 3, "{").text());
		assertEquals("say \u00AB", at("say", 3, "\u00AB").text());
		assertEquals("say \u201C", at("say", 3, "\u201C").text());
		assertEquals("say \u2018", at("say", 3, "\u2018").text());
		// ASCII quotes also attach as closers, so no leading space either.
		assertEquals("say'", at("say", 3, "'").text());
		assertEquals("say\"", at("say", 3, "\"").text());
	}

	@Test
	public void prefixSigilsAlsoSuppressTheTrailingSpace() {
		// @ # $ introduce the next token the way ( does; include them so
		// "@" + tap name builds "@name" without a backspace.
		assertEquals("tell @", at("tell", 4, "@").text());
		assertEquals("chan #", at("chan", 4, "#").text());
		assertEquals("var $", at("var", 3, "$").text());
		InputWordInsert.Result atSign = at("tell", 4, "@");
		assertEquals("tell @bob ",
				at(atSign.text(), atSign.caret(), "bob").text());
	}

	@Test
	public void slashAndPercentKeepTheirSpaces() {
		// Both read as separator and unit far more often than as prefix, and
		// the same code serves tapping words in the game text: welding two
		// deliberately tapped words together is worse than one stray space.
		assertEquals("go / ", at("go", 2, "/").text());
		InputWordInsert.Result slash = at("go", 2, "/");
		assertEquals("go / north ",
				at(slash.text(), slash.caret(), "north").text());
		assertEquals("say %clan ", at("say", 3, "%clan").text());
	}

	@Test
	public void textAfterAnOpenerInTheBarGetsNoLeadingSpace() {
		assertEquals("(foo ", at("(", 1, "foo").text());
		assertEquals("[foo ", at("[", 1, "foo").text());
		assertEquals("{foo ", at("{", 1, "foo").text());
		assertEquals("\u00ABfoo ", at("\u00AB", 1, "foo").text());
		assertEquals("\u201Cfoo ", at("\u201C", 1, "foo").text());
		assertEquals("\u2018foo ", at("\u2018", 1, "foo").text());
		assertEquals("'foo ", at("'", 1, "foo").text());
		assertEquals("\"foo ", at("\"", 1, "foo").text());
		assertEquals("@bob ", at("@", 1, "bob").text());
	}

	@Test
	public void aWordStartingWithPunctuationStillGetsATrailingSpace() {
		assertEquals("slowo, ", at("slowo", 5, ",").text());
		// Leading comma attaches; the rest of the insert is ordinary text.
		assertEquals("kill,please ", at("kill", 4, ",please").text());
	}

	@Test
	public void selectionReplacementStillSpacesOrdinaryWords() {
		InputWordInsert.Result r = InputWordInsert.apply("kill rat", 5, 8, "troll");
		assertEquals("kill troll ", r.text());
	}

	@Test
	public void selectionReplacementAttachesClosingPunctuation() {
		// Selection starts at "rat", so the space after "kill" stays in before.
		InputWordInsert.Result r = InputWordInsert.apply("kill rat", 5, 8, ",");
		assertEquals("kill , ", r.text());
		// Include that space in the selection to attach the comma to the word.
		assertEquals("kill, ",
				InputWordInsert.apply("kill rat", 4, 8, ",").text());
	}

	@Test
	public void literalInsertDoesNotAddSpaces() {
		InputWordInsert.Result r = InputWordInsert.applyLiteral("Munch egg", 9, 9, "s");
		assertEquals("Munch eggs", r.text());
		assertEquals(10, r.caret());
	}

	@Test
	public void literalInsertAtStart() {
		InputWordInsert.Result r = InputWordInsert.applyLiteral("", 0, 0, "look");
		assertEquals("look", r.text());
		assertEquals(4, r.caret());
	}
}
