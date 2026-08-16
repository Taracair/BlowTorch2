package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.UnsupportedEncodingException;

import org.junit.Test;

public class RepeatedLineDimmerTest {

	private static final String TEMPLE =
			"The temple of the sun god is vast and silent.";

	@Test
	public void firstLongLineIsNotDimmed() {
		RepeatedLineDimmer d = new RepeatedLineDimmer();
		assertFalse(d.rememberAndShouldDim(TEMPLE));
	}

	@Test
	public void sameLongLineAgainIsDimmed() {
		RepeatedLineDimmer d = new RepeatedLineDimmer();
		assertFalse(d.rememberAndShouldDim(TEMPLE));
		assertTrue(d.rememberAndShouldDim(TEMPLE));
	}

	@Test
	public void aDifferentLongLineIsNotDimmed() {
		RepeatedLineDimmer d = new RepeatedLineDimmer();
		assertFalse(d.rememberAndShouldDim(TEMPLE));
		assertFalse(d.rememberAndShouldDim(
				"A marble colonnade runs east toward the inner sanctum."));
	}

	@Test
	public void shortOkIsNeverDimmed() {
		RepeatedLineDimmer d = new RepeatedLineDimmer();
		assertFalse(d.rememberAndShouldDim("Ok."));
		assertFalse(d.rememberAndShouldDim("Ok."));
	}

	@Test
	public void afterDefaultWindowOtherLongLinesTheFirstHasFallenOut() {
		RepeatedLineDimmer d = new RepeatedLineDimmer();
		assertFalse(d.rememberAndShouldDim(TEMPLE));
		for (int i = 0; i < RepeatedLineDimmer.DEFAULT_WINDOW; i++) {
			assertFalse(d.rememberAndShouldDim(uniqueLongLine(i)));
		}
		assertFalse(d.rememberAndShouldDim(TEMPLE));
	}

	@Test
	public void aDuplicateOccupiesASlotSoCombatFlushesTheOldRoom() {
		RepeatedLineDimmer d = new RepeatedLineDimmer(2);
		assertFalse(d.rememberAndShouldDim(TEMPLE));
		assertTrue(d.rememberAndShouldDim(TEMPLE));
		assertFalse(d.rememberAndShouldDim(uniqueLongLine(0)));
		assertTrue(d.rememberAndShouldDim(uniqueLongLine(0)));
		assertFalse(d.rememberAndShouldDim(TEMPLE));
	}

	@Test
	public void turningOffForgetsTheWindow() throws UnsupportedEncodingException {
		TextTree tree = new TextTree();
		tree.setDimRepeatedLines(true);
		tree.addBytesImpl((TEMPLE + "\n").getBytes("UTF-8"));
		tree.setDimRepeatedLines(false);
		tree.setDimRepeatedLines(true);
		tree.addBytesImpl((TEMPLE + "\n").getBytes("UTF-8"));
		java.util.LinkedList<TextTree.Line> lines = tree.getLines();
		assertFalse(lines.get(0).isDimRepeated());
	}

	@Test
	public void shrinkingTheWindowDropsTheOldestLine() {
		RepeatedLineDimmer d = new RepeatedLineDimmer(3);
		assertFalse(d.rememberAndShouldDim(TEMPLE));
		assertFalse(d.rememberAndShouldDim(uniqueLongLine(0)));
		assertFalse(d.rememberAndShouldDim(uniqueLongLine(1)));
		d.setWindowSize(1);
		assertFalse(d.rememberAndShouldDim(TEMPLE));
	}

	@Test
	public void customWindowOfTwoForgetsAfterTwoOthers() {
		RepeatedLineDimmer d = new RepeatedLineDimmer(2);
		assertFalse(d.rememberAndShouldDim(TEMPLE));
		assertFalse(d.rememberAndShouldDim(uniqueLongLine(0)));
		assertFalse(d.rememberAndShouldDim(uniqueLongLine(1)));
		assertFalse(d.rememberAndShouldDim(TEMPLE));
	}

	@Test
	public void defaultStrengthHalvesWhiteRgbAndKeepsAlpha() {
		assertEquals(0x807F7F7F, RepeatedLineDimmer.dimForeground(
				0x80FFFFFF, RepeatedLineDimmer.DEFAULT_STRENGTH));
	}

	@Test
	public void higherStrengthIsDarker() {
		int half = RepeatedLineDimmer.dimForeground(0xFFFFFFFF, 50);
		int darker = RepeatedLineDimmer.dimForeground(0xFFFFFFFF, 80);
		int rHalf = (half >> 16) & 0xFF;
		int rDark = (darker >> 16) & 0xFF;
		assertTrue(rDark < rHalf);
	}

	@Test
	public void nullAndEmptyAreNotRemembered() {
		RepeatedLineDimmer d = new RepeatedLineDimmer();
		assertFalse(d.rememberAndShouldDim(null));
		assertFalse(d.rememberAndShouldDim(""));
		assertFalse(d.rememberAndShouldDim("   "));
		assertFalse(d.rememberAndShouldDim(TEMPLE));
	}

	@Test
	public void collapsedWhitespaceStillMatches() {
		RepeatedLineDimmer d = new RepeatedLineDimmer();
		assertFalse(d.rememberAndShouldDim(TEMPLE));
		assertTrue(d.rememberAndShouldDim("  The   temple of the sun god is vast and silent.  "));
	}

	@Test
	public void lineShorterThanMinCharsIsNotRemembered() {
		RepeatedLineDimmer d = new RepeatedLineDimmer();
		String tooShort = "abcd";
		assertTrue(tooShort.length() == RepeatedLineDimmer.MIN_CHARS - 1);
		assertFalse(d.rememberAndShouldDim(tooShort));
		assertFalse(d.rememberAndShouldDim(tooShort));
	}

	@Test
	public void lineAtMinCharsIsRemembered() {
		RepeatedLineDimmer d = new RepeatedLineDimmer();
		String atMin = "abcde";
		assertTrue(atMin.length() == RepeatedLineDimmer.MIN_CHARS);
		assertFalse(d.rememberAndShouldDim(atMin));
		assertTrue(d.rememberAndShouldDim(atMin));
	}

	@Test
	public void wrappedLeftoverWaterIsDimmedTheSecondTime() {
		RepeatedLineDimmer d = new RepeatedLineDimmer();
		assertFalse(d.rememberAndShouldDim("water."));
		assertTrue(d.rememberAndShouldDim("water."));
	}

	@Test
	public void textTreeHonoursASmallerRememberWindow()
			throws UnsupportedEncodingException {
		TextTree tree = new TextTree();
		tree.setDimRepeatedWindow(2);
		tree.setDimRepeatedLines(true);
		tree.addBytesImpl((TEMPLE + "\n").getBytes("UTF-8"));
		tree.addBytesImpl((uniqueLongLine(0) + "\n").getBytes("UTF-8"));
		tree.addBytesImpl((uniqueLongLine(1) + "\n").getBytes("UTF-8"));
		tree.addBytesImpl((TEMPLE + "\n").getBytes("UTF-8"));
		java.util.LinkedList<TextTree.Line> lines = tree.getLines();
		assertFalse(lines.get(0).isDimRepeated());
	}

	@Test
	public void textTreeMarksFinishedLineWhenDimmerIsOn()
			throws UnsupportedEncodingException {
		TextTree tree = new TextTree();
		tree.setDimRepeatedLines(true);
		tree.addBytesImpl((TEMPLE + "\n").getBytes("UTF-8"));
		tree.addBytesImpl((TEMPLE + "\n").getBytes("UTF-8"));
		java.util.LinkedList<TextTree.Line> lines = tree.getLines();
		// newest first
		assertTrue(lines.get(0).isDimRepeated());
		assertFalse(lines.get(1).isDimRepeated());
	}

	@Test
	public void textTreeDoesNotMarkWhenDimmerIsOff()
			throws UnsupportedEncodingException {
		TextTree tree = new TextTree();
		tree.addBytesImpl((TEMPLE + "\n").getBytes("UTF-8"));
		tree.addBytesImpl((TEMPLE + "\n").getBytes("UTF-8"));
		for (TextTree.Line line : tree.getLines()) {
			assertFalse(line.isDimRepeated());
		}
	}

	@Test
	public void ansiColourIsIgnoredWhenMarking() throws UnsupportedEncodingException {
		TextTree tree = new TextTree();
		tree.setDimRepeatedLines(true);
		String esc = "\u001B[32m" + TEMPLE + "\u001B[0m\n";
		tree.addBytesImpl(esc.getBytes("UTF-8"));
		tree.addBytesImpl((TEMPLE + "\n").getBytes("UTF-8"));
		java.util.LinkedList<TextTree.Line> lines = tree.getLines();
		assertTrue(lines.get(0).isDimRepeated());
		assertFalse(lines.get(1).isDimRepeated());
	}

	private static String uniqueLongLine(final int n) {
		return "A different long room description used as filler " + n + ".";
	}
}
