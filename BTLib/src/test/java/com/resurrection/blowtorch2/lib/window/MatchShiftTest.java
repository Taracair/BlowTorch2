package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MatchShiftTest {

	@Test
	public void replaceInsideALaterMatchLeavesTheStart() {
		String line = "[party] Taracair says, \"note complete mastery hmm\"";
		int rep = line.indexOf("complete mastery");
		MatchShift shift = new MatchShift();
		shift.addFromResponder(rep, rep + "complete mastery".length() - 2, 10);

		int colorPend = line.length() - 2;
		assertEquals(0, shift.currentStart(0));
		assertEquals(line.length() - 1 + 10, shift.currentInclusiveEnd(colorPend));
	}

	@Test
	public void replaceBeforeALaterMatchSlidesBothEnds() {
		String line = "hello says goblin";
		int delta = "hello there".length() - "hello".length();
		MatchShift shift = new MatchShift();
		shift.addFromResponder(0, "hello".length() - 2, delta);

		int says = line.indexOf("says");
		int saysPend = says + "says".length() - 2;
		assertEquals(says + delta, shift.currentStart(says));
		assertEquals(says + "says".length() - 1 + delta,
				shift.currentInclusiveEnd(saysPend));
	}

	@Test
	public void replaceAfterAMatchDoesNotMoveIt() {
		String line = "[party] Taracair says, \"note complete mastery hmm\"";
		int name = line.indexOf("Taracair");
		int rep = line.indexOf("complete mastery");
		MatchShift shift = new MatchShift();
		shift.addFromResponder(rep, rep + "complete mastery".length() - 2, 10);

		int namePend = name + "Taracair".length() - 2;
		assertEquals(name, shift.currentStart(name));
		assertEquals(name + "Taracair".length() - 1,
				shift.currentInclusiveEnd(namePend));
	}

	@Test
	public void aShorterReplaceInsideStillGrowsOnlyTheEnd() {
		String line = "head mastery tail";
		int rep = line.indexOf("mastery");
		MatchShift shift = new MatchShift();
		shift.addFromResponder(rep, rep + "mastery".length() - 2, -4);

		assertEquals(0, shift.currentStart(0));
		assertEquals(line.length() - 1 - 4, shift.currentInclusiveEnd(line.length() - 2));
		assertEquals(-4, shift.totalDelta());
	}

	@Test
	public void zeroDeltaIsNotStored() {
		MatchShift shift = new MatchShift();
		shift.addFromResponder(4, 8, 0);
		assertEquals(0, shift.totalDelta());
		assertEquals(4, shift.currentStart(4));
	}
}
