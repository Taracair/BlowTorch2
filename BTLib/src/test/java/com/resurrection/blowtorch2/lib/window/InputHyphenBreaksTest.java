package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public class InputHyphenBreaksTest {

	private static final String WORD = "supercalifragilisticexpialidocious";

	private static final InputHyphenBreaks.Measurer UNIT = new InputHyphenBreaks.Measurer() {
		@Override
		public float width(final String text, final int start, final int end) {
			return end - start;
		}
	};

	@Test
	public void aSpaceThatDoesNotFitLeavesTheNextCopyWhole() {
		// The first copy fills the line. The space stays there. The next
		// copy starts at column 0 and fits, so it is not broken.
		String line = WORD + " " + WORD;
		int[] cuts = InputHyphenBreaks.cuts(line, WORD.length(), 1f, 2, 2, false, UNIT);
		assertEquals(0, cuts.length);
	}

	@Test
	public void secondCopyBreaksWhenTheRemainderHoldsFiveLetters() {
		// 42 columns, 34-letter word, one space. Remainder is 7.
		// "super" is 5 letters plus a hyphen, which fits. The whole word does not.
		String line = WORD + " " + WORD;
		int[] cuts = InputHyphenBreaks.cuts(line, 42f, 1f, 5, 3, false, UNIT);
		assertArrayEquals(new int[] { WORD.length() + 1 + 5 }, cuts);
	}

	@Test
	public void sparingLeavesTheWordWholeWhenFiveLettersDoNotFit() {
		// Remainder after the first word and the space is 5. Five letters
		// plus a hyphen need 6, so the second copy moves to the next line.
		String line = WORD + " " + WORD;
		int[] cuts = InputHyphenBreaks.cuts(line, 40f, 1f, 5, 3, false, UNIT);
		assertEquals(0, cuts.length);
	}

	@Test
	public void fullBreaksThatSameRemainder() {
		String line = WORD + " " + WORD;
		int[] cuts = InputHyphenBreaks.cuts(line, 40f, 1f, 2, 2, false, UNIT);
		assertArrayEquals(new int[] { WORD.length() + 1 + 4 }, cuts);
	}

	@Test
	public void aWordWiderThanTheLineBreaksOnTheLineItself() {
		int[] cuts = InputHyphenBreaks.cuts(WORD, 10f, 1f, 2, 2, false, UNIT);
		assertEquals(9, cuts[0]);
	}

	@Test
	public void digitsThatSpillStillTakeRoomOnTheNextLine() {
		// 15 digits on a line of 10 leave 5 on the next line, plus the space.
		// "abcdef" does not fit in the 4 columns left, so it breaks.
		// Charging the digits as a full-line wrap would leave no break.
		String line = "123456789012345 abcdef";
		int[] cuts = InputHyphenBreaks.cuts(line, 10f, 1f, 2, 2, false, UNIT);
		assertEquals(1, cuts.length);
		assertEquals("123456789012345 ".length() + 3, cuts[0]);
	}

	@Test
	public void deletingBesideAMarkTakesTheWholeEmoji() {
		String line = "ab\uD83D\uDE00cd";
		assertArrayEquals(new int[] { 2, 4 }, InputHyphenBreaks.clusterRange(line, 2));
		assertArrayEquals(new int[] { 2, 4 }, InputHyphenBreaks.clusterRange(line, 3));
		assertArrayEquals(new int[] { 1, 2 }, InputHyphenBreaks.clusterRange(line, 1));
	}

	@Test
	public void aBreakDoesNotSplitAnEmoji() {
		// "ab", a grinning face, then "cd". Room for four columns.
		// The index between the two halves of the face fits, and is not used.
		String line = "ab\uD83D\uDE00cd";
		int[] cuts = InputHyphenBreaks.cuts(line, 4f, 1f, 2, 2, false, UNIT);
		assertArrayEquals(new int[] { 2 }, cuts);
	}

	@Test
	public void digitsAreNotHyphenated() {
		int[] cuts = InputHyphenBreaks.cuts("123456789012345", 10f, 1f, 2, 2, false, UNIT);
		assertEquals(0, cuts.length);
	}

	@Test
	public void aTypedHyphenIsTheBreakAndDoesNotAskForAnother() {
		int[] cuts = InputHyphenBreaks.cuts("iron-helmet", 8f, 1f, 2, 2, false, UNIT);
		assertArrayEquals(new int[] { 5 }, cuts);
	}

	@Test
	public void polishDoesNotBreakInsideSz() {
		int[] english = InputHyphenBreaks.cuts("aaszyy", 4f, 1f, 2, 2, false, UNIT);
		int[] polish = InputHyphenBreaks.cuts("aaszyy", 4f, 1f, 2, 2, true, UNIT);
		assertArrayEquals(new int[] { 3 }, english);
		assertArrayEquals(new int[] { 2 }, polish);
	}

	@Test
	public void reconcileIsStableOnceTheMarkIsInPlace() {
		String line = WORD + " " + WORD;
		int[] cuts = InputHyphenBreaks.cuts(line, 42f, 1f, 5, 3, false, UNIT);
		InputHyphenBreaks.Op[] first = InputHyphenBreaks.reconcile(line, cuts, -1, -1);
		assertEquals(1, first.length);
		assertEquals(true, first[0].insert);
		assertEquals(WORD.length() + 1 + 5, first[0].index);
		String marked = apply(line, first);
		assertEquals(0, InputHyphenBreaks.reconcile(marked, cuts, -1, -1).length);
		assertEquals(line, InputHyphenBreaks.strip(marked));
		assertEquals(WORD.length() + 1 + 5, InputHyphenBreaks.bufferIndex(marked, cuts[0]));
	}

	@Test
	public void aComposingSpanIsNotMarked() {
		String line = WORD + " " + WORD;
		int[] cuts = InputHyphenBreaks.cuts(line, 42f, 1f, 5, 3, false, UNIT);
		int word2 = WORD.length() + 1;
		InputHyphenBreaks.Op[] ops = InputHyphenBreaks.reconcile(line, cuts, word2,
				line.length());
		assertEquals(0, ops.length);
	}

	@Test
	public void logicalIndexSkipsTheMark() {
		String marked = "super" + InputHyphenBreaks.MARK + "cal ";
		assertEquals(5, InputHyphenBreaks.logicalIndex(marked, 5));
		assertEquals(9, InputHyphenBreaks.logicalIndex(marked, marked.length()));
		assertEquals(marked.length(), InputHyphenBreaks.bufferIndex(marked, 9));
	}

	@Test
	public void stripRemovesOnlyTheMark() {
		assertEquals("supercal", InputHyphenBreaks.strip("super" + InputHyphenBreaks.MARK + "cal"));
		assertFalse(InputHyphenBreaks.contains("supercal"));
	}

	private static String apply(final String raw, final InputHyphenBreaks.Op[] ops) {
		StringBuilder out = new StringBuilder(raw);
		for (int i = 0; i < ops.length; i++) {
			InputHyphenBreaks.Op op = ops[i];
			if (op.insert) {
				out.insert(op.index, InputHyphenBreaks.MARK);
			} else {
				out.delete(op.index, op.index + 1);
			}
		}
		return out.toString();
	}
}
