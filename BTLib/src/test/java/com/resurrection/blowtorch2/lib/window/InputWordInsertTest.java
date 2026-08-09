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
}
