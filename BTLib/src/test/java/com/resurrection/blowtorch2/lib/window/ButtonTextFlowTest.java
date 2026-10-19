package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class ButtonTextFlowTest {

	private static ButtonTextFlow.RectPx rect(final int l, final int t, final int r, final int b) {
		return new ButtonTextFlow.RectPx(l, t, r, b);
	}

	private static void assertRun(final ButtonTextFlow.Run run, final int visualRow,
			final int destCol, final int srcStart, final int srcEnd) {
		assertNotNull(run);
		assertEquals("visualRow", visualRow, run.visualRow);
		assertEquals("destCol", destCol, run.destCol);
		assertEquals("srcStart", srcStart, run.srcStart);
		assertEquals("srcEnd", srcEnd, run.srcEnd);
	}

	@Test
	public void noButtonsYieldsFullWidthFree() {
		int[][] free = ButtonTextFlow.freeColumns(null, 80);
		assertEquals(1, free.length);
		assertEquals(0, free[0][0]);
		assertEquals(80, free[0][1]);
		assertNull(ButtonTextFlow.mergedBlobPx(new ArrayList<ButtonTextFlow.RectPx>(), 0, 20));
	}

	@Test
	public void oneButtonOnTheRightLeavesLeftRemnantOnly() {
		List<ButtonTextFlow.RectPx> buttons = new ArrayList<ButtonTextFlow.RectPx>();
		buttons.add(rect(600, 0, 800, 40));
		ButtonTextFlow.RectPx blob = ButtonTextFlow.mergedBlobPx(buttons, 0, 20);
		assertNotNull(blob);
		assertEquals(600, blob.left);
		assertEquals(800, blob.right);
		int[] cols = ButtonTextFlow.blobColumns(blob, 10, 0, 80);
		assertNotNull(cols);
		assertEquals(60, cols[0]);
		assertEquals(80, cols[1]);
		int[][] free = ButtonTextFlow.freeColumns(cols, 80);
		assertEquals(1, free.length);
		assertEquals(0, free[0][0]);
		assertEquals(60, free[0][1]);
	}

	@Test
	public void twoButtonsOnTheSameRowMergeIntoOneBlob() {
		List<ButtonTextFlow.RectPx> buttons = new ArrayList<ButtonTextFlow.RectPx>();
		buttons.add(rect(0, 0, 80, 40));
		buttons.add(rect(720, 0, 800, 40));
		ButtonTextFlow.RectPx blob = ButtonTextFlow.mergedBlobPx(buttons, 0, 20);
		assertNotNull(blob);
		assertEquals(0, blob.left);
		assertEquals(800, blob.right);
		int[] cols = ButtonTextFlow.blobColumns(blob, 10, 0, 80);
		int[][] free = ButtonTextFlow.freeColumns(cols, 80);
		assertEquals("never text between two buttons on one row", 0, free.length);
	}

	@Test
	public void buttonsOnDifferentRowsStayIndependent() {
		List<ButtonTextFlow.RectPx> buttons = new ArrayList<ButtonTextFlow.RectPx>();
		buttons.add(rect(0, 0, 100, 20));
		buttons.add(rect(700, 40, 800, 60));
		ButtonTextFlow.RectPx top = ButtonTextFlow.mergedBlobPx(buttons, 0, 20);
		assertNotNull(top);
		assertEquals(0, top.left);
		assertEquals(100, top.right);
		ButtonTextFlow.RectPx bottom = ButtonTextFlow.mergedBlobPx(buttons, 40, 60);
		assertNotNull(bottom);
		assertEquals(700, bottom.left);
		assertEquals(800, bottom.right);
		assertNull(ButtonTextFlow.mergedBlobPx(buttons, 20, 40));
	}

	@Test
	public void tinyGapBetweenTwoButtonsStillMerges() {
		List<ButtonTextFlow.RectPx> buttons = new ArrayList<ButtonTextFlow.RectPx>();
		buttons.add(rect(0, 0, 390, 20));
		buttons.add(rect(400, 0, 800, 20));
		ButtonTextFlow.RectPx blob = ButtonTextFlow.mergedBlobPx(buttons, 0, 20);
		assertNotNull(blob);
		assertEquals(0, blob.left);
		assertEquals(800, blob.right);
	}

	@Test
	public void layoutOverflowCreatesASecondVisualRow() {
		int[][] free = new int[][] { { 0, 10 } };
		List<ButtonTextFlow.Run> runs = ButtonTextFlow.layout(24, free, false, true);
		assertEquals(3, runs.size());
		assertRun(runs.get(0), 0, 0, 0, 10);
		assertRun(runs.get(1), 1, 0, 10, 20);
		assertRun(runs.get(2), 2, 0, 20, 24);
	}

	@Test
	public void wordWrapBreaksAtLastSpaceHardBreakDoesNot() {
		int[][] free = new int[][] { { 0, 8 } };
		String line = "hello world extra";
		List<ButtonTextFlow.Run> wrapped = ButtonTextFlow.layout(line.length(), free, true, false,
				line);
		assertTrue(wrapped.size() >= 2);
		assertRun(wrapped.get(0), 0, 0, 0, 6);
		assertEquals("hello ", line.substring(wrapped.get(0).srcStart, wrapped.get(0).srcEnd));

		List<ButtonTextFlow.Run> hard = ButtonTextFlow.layout(line.length(), free, false, true,
				line);
		assertRun(hard.get(0), 0, 0, 0, 8);
		assertEquals("hello wo", line.substring(hard.get(0).srcStart, hard.get(0).srcEnd));
	}

	@Test
	public void leftThenRightRemnantOnTheSameVisualRow() {
		int[][] free = new int[][] { { 0, 10 }, { 20, 30 } };
		List<ButtonTextFlow.Run> runs = ButtonTextFlow.layout(15, free, false, true);
		assertEquals(2, runs.size());
		assertRun(runs.get(0), 0, 0, 0, 10);
		assertRun(runs.get(1), 0, 20, 10, 15);
	}

	@Test
	public void scrollXShiftsBlobColumns() {
		ButtonTextFlow.RectPx blob = rect(100, 0, 200, 20);
		int[] atRest = ButtonTextFlow.blobColumns(blob, 10, 0, 80);
		assertNotNull(atRest);
		assertEquals(10, atRest[0]);
		assertEquals(20, atRest[1]);
		int[] panned = ButtonTextFlow.blobColumns(blob, 10, 50, 80);
		assertNotNull(panned);
		assertEquals(15, panned[0]);
		assertEquals(25, panned[1]);
	}

	@Test
	public void emptyFreeStillSwallowsInLayout() {
		List<ButtonTextFlow.Run> runs = ButtonTextFlow.layout(10, new int[0][], false, true,
				"0123456789");
		assertEquals(0, runs.size());
	}

	@Test
	public void layoutInRowLetterBreakSplitsAWordAcrossTheHole() {
		// .avoidbuttons letters (default): hardBreak packs one character at a time.
		List<ButtonTextFlow.RectPx> buttons = new ArrayList<ButtonTextFlow.RectPx>();
		buttons.add(rect(80, 0, 160, 20));
		String text = "hello wonderful day";
		List<ButtonTextFlow.Run> split = ButtonTextFlow.layoutInRow(text.length(), buttons,
				0, 20, 10, 40, false, true, text);
		assertTrue(split.size() >= 2);
		assertEquals("hello wo", text.substring(split.get(0).srcStart, split.get(0).srcEnd));
		assertTrue(text.substring(split.get(1).srcStart, split.get(1).srcEnd).startsWith("nder"));
	}

	@Test
	public void layoutInRowWordBreakKeepsWholeWordsOffTheHole() {
		// .avoidbuttons words: do not split a word across the button hole.
		List<ButtonTextFlow.RectPx> buttons = new ArrayList<ButtonTextFlow.RectPx>();
		buttons.add(rect(80, 0, 160, 20));
		String text = "hello wonderful day";
		List<ButtonTextFlow.Run> wrapped = ButtonTextFlow.layoutInRow(text.length(), buttons,
				0, 20, 10, 40, true, false, text);
		assertTrue(wrapped.size() >= 2);
		assertEquals("hello ", text.substring(wrapped.get(0).srcStart, wrapped.get(0).srcEnd));
		assertEquals("wonderful", text.substring(wrapped.get(1).srcStart,
				Math.min(text.length(), wrapped.get(1).srcStart + 9)));
	}

	@Test
	public void layoutInRowSplitsAWordInsteadOfThrowingIt() {
		List<ButtonTextFlow.RectPx> buttons = new ArrayList<ButtonTextFlow.RectPx>();
		buttons.add(rect(80, 0, 160, 20));
		String text = "hello wonderful day";
		List<ButtonTextFlow.Run> wrapped = ButtonTextFlow.layoutInRow(text.length(), buttons,
				0, 20, 10, 40, true, false, text);
		assertTrue(wrapped.size() >= 2);
		assertEquals("hello ", text.substring(wrapped.get(0).srcStart, wrapped.get(0).srcEnd));
		assertEquals("wonderful", text.substring(wrapped.get(1).srcStart,
				Math.min(text.length(), wrapped.get(1).srcStart + 9)));

		List<ButtonTextFlow.Run> split = ButtonTextFlow.layoutInRow(text.length(), buttons,
				0, 20, 10, 40, false, true, text);
		assertTrue(split.size() >= 2);
		assertEquals("hello wo", text.substring(split.get(0).srcStart, split.get(0).srcEnd));
		assertTrue(text.substring(split.get(1).srcStart, split.get(1).srcEnd).startsWith("nder"));
	}

	@Test
	public void layoutInRowDoesNotInsertASecondVisualRow() {
		List<ButtonTextFlow.RectPx> buttons = new ArrayList<ButtonTextFlow.RectPx>();
		buttons.add(rect(500, 0, 800, 20));
		StringBuilder sb = new StringBuilder(70);
		for (int i = 0; i < 70; i++) {
			sb.append('a');
		}
		List<ButtonTextFlow.Run> runs = ButtonTextFlow.layoutInRow(70, buttons, 0, 20, 10, 80,
				false, true, sb);
		assertEquals(1, runs.size());
		assertRun(runs.get(0), 0, 0, 0, 50);
	}

	@Test
	public void layoutInRowSwallowsAFullWidthBlob() {
		List<ButtonTextFlow.RectPx> buttons = new ArrayList<ButtonTextFlow.RectPx>();
		buttons.add(rect(0, 0, 800, 20));
		List<ButtonTextFlow.Run> runs = ButtonTextFlow.layoutInRow(10, buttons, 0, 20, 10, 80,
				false, true, "0123456789");
		assertEquals(0, runs.size());
	}

	@Test
	public void layoutInRowKeepsATallButtonToOneRow() {
		List<ButtonTextFlow.RectPx> buttons = new ArrayList<ButtonTextFlow.RectPx>();
		buttons.add(rect(600, 0, 800, 200));
		List<ButtonTextFlow.Run> runs = ButtonTextFlow.layoutInRow(130, buttons, 0, 20, 10, 80,
				false, true, null);
		assertEquals(1, runs.size());
		assertRun(runs.get(0), 0, 0, 0, 60);
	}

	@Test
	public void layoutInRowIgnoresARowWithNoButton() {
		List<ButtonTextFlow.RectPx> buttons = new ArrayList<ButtonTextFlow.RectPx>();
		buttons.add(rect(500, 0, 800, 20));
		List<ButtonTextFlow.Run> runs = ButtonTextFlow.layoutInRow(10, buttons, 40, 20, 10, 80,
				false, true, "0123456789");
		assertEquals(0, runs.size());
	}

	@Test
	public void panViewXSubtractsTheScrollFraction() {
		assertEquals(0f, ButtonTextFlow.panViewX(0, 0, 10f, 0f), 0.01f);
		assertEquals(50f, ButtonTextFlow.panViewX(5, 0, 10f, 0f), 0.01f);
		assertEquals(-3f, ButtonTextFlow.panViewX(0, 5, 10f, 53f), 0.01f);
		assertEquals(47f, ButtonTextFlow.panViewX(5, 5, 10f, 53f), 0.01f);
		assertEquals(0f, ButtonTextFlow.panViewX(0, 5, 10f, 50f), 0.01f);
	}

	@Test
	public void panDestColIsTheInverseOfPanViewX() {
		assertEquals(0, ButtonTextFlow.panDestCol(0f, 0, 10f, 0f));
		assertEquals(5, ButtonTextFlow.panDestCol(50f, 0, 10f, 0f));
		assertEquals(0, ButtonTextFlow.panDestCol(-3f, 5, 10f, 53f));
		assertEquals(1, ButtonTextFlow.panDestCol(8f, 5, 10f, 53f));
		assertEquals(5, ButtonTextFlow.panDestCol(47f, 5, 10f, 53f));
		assertEquals(1, ButtonTextFlow.panDestCol(
				ButtonTextFlow.panViewX(1, 5, 10f, 53f), 5, 10f, 53f));
	}

	@Test
	public void rightPocketViewXTracksScrollAcrossCellBoundary() {
		final float cellW = 10f;
		// Left edge on a cell boundary, so the shared remainder is 0. The half-cell
		// bias is a constant, so a surviving glyph still moves 1px across the boundary.
		final float blobLeft = 150f;
		final float blobRight = 203f;
		final int holeCol1 = 21;
		final int destBefore = holeCol1 + 2;
		final int destAfter = holeCol1 + 1;
		final float scrollBefore = 29f;
		final float scrollAfter = 30f;
		float x0 = ButtonTextFlow.rightPocketViewX(destBefore, holeCol1, blobLeft, blobRight,
				cellW, scrollBefore);
		float x1 = ButtonTextFlow.rightPocketViewX(destAfter, holeCol1, blobLeft, blobRight,
				cellW, scrollAfter);
		assertEquals("surviving glyph moves ~1px, not ~cellW", -1f, x1 - x0, 0.01f);
		float atFrac0 = ButtonTextFlow.rightPocketViewX(holeCol1, holeCol1, blobLeft, blobRight,
				cellW, scrollAfter);
		assertTrue("frac=0 stays at or right of blob.right", atFrac0 >= blobRight);
		assertEquals(holeCol1 + 2, ButtonTextFlow.rightPocketDestCol(x0, holeCol1, blobLeft,
				blobRight, cellW, scrollBefore));
	}

	@Test
	public void holedRowStartsOnTheUnholedColumn() {
		final int cell = 10;
		final int blobLeft = 104;
		final int blobRight = 153;
		assertEquals(0, ButtonTextFlow.leftPocketShift(blobLeft, blobRight, cell));
		assertEquals(ButtonTextFlow.panViewX(0, 0, cell, 0f),
				ButtonTextFlow.leftPocketViewX(0, 0, blobLeft, blobRight, cell, 0f), 0.01f);
		float scrolled = 4f;
		assertEquals(ButtonTextFlow.panViewX(0, 0, cell, scrolled),
				ButtonTextFlow.leftPocketViewX(0, 0, blobLeft, blobRight, cell, scrolled), 0.01f);

		// Left remainder wider than the right. Still no indent at column 0.
		assertEquals(0, ButtonTextFlow.leftPocketShift(107, 156, cell));
		assertEquals(0f, ButtonTextFlow.leftPocketViewX(0, 0, 107f, 156f, cell, 0f), 0.01f);

		// Phone, 15 Oct 2026: cell 17, button 439..528. The old half-cell put
		// column 0 at 8.
		final float phone = 17f;
		assertEquals(0, ButtonTextFlow.leftPocketShift(439, 528, 17));
		assertEquals(0f, ButtonTextFlow.leftPocketViewX(0, 0, 439f, 528f, phone, 0f), 0.01f);
		assertEquals(ButtonTextFlow.panViewX(3, 0, phone, 5f),
				ButtonTextFlow.leftPocketViewX(3, 0, 439f, 528f, phone, 5f), 0.01f);
	}

	@Test
	public void rightPocketKeepsHalfACellClear() {
		final int cell = 10;
		final int blobLeft = 104;
		final int blobRight = 153;
		assertEquals(2, ButtonTextFlow.rightPocketShift(blobLeft, blobRight, cell));
		int holeCol1 = 16;
		float rightX = ButtonTextFlow.rightPocketViewX(holeCol1, holeCol1, blobLeft, blobRight,
				cell, 0f);
		assertTrue(rightX >= blobRight);
		float mid = cell / 2f;
		float rightMid = ButtonTextFlow.rightPocketViewX(holeCol1, holeCol1, blobLeft, blobRight,
				cell, mid);
		assertEquals(rightX - mid, rightMid, 0.01f);

		assertEquals(5, ButtonTextFlow.rightPocketShift(107, 156, cell));
		float rightMatch = ButtonTextFlow.rightPocketViewX(16, 16, 107f, 156f, cell, 0f);
		assertTrue(rightMatch >= 156f);
	}

	@Test
	public void trailingSpaceKeepsItsColumn() {
		// Moving the letters into that space jumps ~cellW when the pan crosses
		// a column, because the space count changes with the visible slice.
		List<ButtonTextFlow.RectPx> buttons = new ArrayList<ButtonTextFlow.RectPx>();
		buttons.add(rect(60, 0, 200, 20));
		String text = "hello wonderful";
		List<ButtonTextFlow.Run> runs = ButtonTextFlow.layoutInRow(text.length(), buttons, 0, 20,
				10, 40, false, true, text);
		assertTrue(runs.size() >= 2);
		assertEquals("hello ", text.substring(runs.get(0).srcStart, runs.get(0).srcEnd));
		assertEquals(0, runs.get(0).destCol);
	}

	@Test
	public void leftPocketRoundTripIncludesTheShift() {
		float x = ButtonTextFlow.leftPocketViewX(4, 2, 107f, 150f, 10f, 0f);
		assertEquals(4, ButtonTextFlow.leftPocketDestCol(x, 2, 107f, 150f, 10f, 0f));
		assertEquals(60f, x, 0.01f);
	}

	@Test
	public void betweenPutsTextInTheGapAndNotPastTheRightButton() {
		List<ButtonTextFlow.RectPx> buttons = new ArrayList<ButtonTextFlow.RectPx>();
		buttons.add(rect(0, 0, 80, 20));
		buttons.add(rect(720, 0, 800, 20));
		String text = "0123456789abcdef";
		List<ButtonTextFlow.Run> runs = ButtonTextFlow.layoutBetween(text.length(), buttons, 0, 20,
				10, 80, 800, false, true, text);
		assertEquals(1, runs.size());
		assertRun(runs.get(0), 0, 8, 0, 16);
		assertTrue(runs.get(0).clipRight <= 720);
		assertTrue(runs.get(0).destCol + (runs.get(0).srcEnd - runs.get(0).srcStart) <= 72);
	}

	@Test
	public void betweenKeepsThePageBesideASingleLeftButton() {
		List<ButtonTextFlow.RectPx> buttons = new ArrayList<ButtonTextFlow.RectPx>();
		buttons.add(rect(0, 0, 80, 20));
		String text = "hello";
		List<ButtonTextFlow.Run> runs = ButtonTextFlow.layoutBetween(text.length(), buttons, 0, 20,
				10, 80, 800, false, true, text);
		assertEquals(1, runs.size());
		assertRun(runs.get(0), 0, 8, 0, 5);
		assertTrue(runs.get(0).rightPocket);
	}

	@Test
	public void betweenSendsAWordToTheNextOpeningOnTheSameRow() {
		List<ButtonTextFlow.RectPx> buttons = new ArrayList<ButtonTextFlow.RectPx>();
		buttons.add(rect(0, 0, 120, 20));
		buttons.add(rect(200, 0, 400, 20));
		buttons.add(rect(700, 0, 800, 20));
		String text = "hi wonderful";
		List<ButtonTextFlow.Run> runs = ButtonTextFlow.layoutBetween(text.length(), buttons, 0, 20,
				10, 80, 800, true, false, text);
		assertTrue(runs.size() >= 2);
		assertEquals("hi ", text.substring(runs.get(0).srcStart, runs.get(0).srcEnd));
		assertEquals(12, runs.get(0).destCol);
		assertEquals("wonderful", text.substring(runs.get(1).srcStart, runs.get(1).srcEnd));
		assertEquals(40, runs.get(1).destCol);
		assertEquals(0, runs.get(1).visualRow);
	}

	@Test
	public void betweenDropsAWordThatFitsNowhereOnThisRow() {
		List<ButtonTextFlow.RectPx> buttons = new ArrayList<ButtonTextFlow.RectPx>();
		buttons.add(rect(100, 0, 800, 20));
		String text = "hello wonderful";
		List<ButtonTextFlow.Run> runs = ButtonTextFlow.layoutBetween(text.length(), buttons, 0,
				20, 10, 80, 800, true, false, text);
		assertEquals(1, runs.size());
		assertEquals("hello ", text.substring(runs.get(0).srcStart, runs.get(0).srcEnd));
		assertEquals(0, runs.get(0).visualRow);
	}

	@Test
	public void betweenSplitsAWordLongerThanTheOpeningAndDropsTheRest() {
		List<ButtonTextFlow.RectPx> buttons = new ArrayList<ButtonTextFlow.RectPx>();
		buttons.add(rect(40, 0, 800, 20));
		String text = "wonderful";
		List<ButtonTextFlow.Run> runs = ButtonTextFlow.layoutBetween(text.length(), buttons, 0, 20,
				10, 80, 800, true, false, text);
		assertEquals(1, runs.size());
		assertEquals("wond", text.substring(runs.get(0).srcStart, runs.get(0).srcEnd));
		assertEquals(0, runs.get(0).visualRow);
	}
}
