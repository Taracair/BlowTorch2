package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.resurrection.blowtorch2.lib.service.function.CanvasWidthCommand;

/**
 * The four scroll commands. Counts come from the command the player typed.
 * A missing number is not filled in. Sideways scrolling starts above the
 * same 100% floor as {@code .width}.
 */
public class ScrollStepTest {

	@Test
	public void scrollUpNeedsANumberAndDoesNotInventOne() {
		ScrollStep.Decision d = ScrollStep.decide("scrollup", "", 100);
		assertEquals(ScrollStep.Kind.MISSING_NUMBER, d.kind);
		assertEquals(0, d.amount);
		assertNull(d.toAction());
		assertTrue(d.message().contains("needs a number"));
		assertTrue(d.message().contains(".scrollup 10"));
	}

	@Test
	public void scrollDownNeedsANumber() {
		ScrollStep.Decision d = ScrollStep.decide(".scrolldown", "   ", 150);
		assertEquals(ScrollStep.Kind.MISSING_NUMBER, d.kind);
		assertEquals(0, d.amount);
		assertTrue(d.message().contains(".scrolldown 5"));
	}

	@Test
	public void scrollUpTenLines() {
		ScrollStep.Decision d = ScrollStep.decide("scrollup", "10", 100);
		assertEquals(ScrollStep.Kind.MOVE, d.kind);
		assertEquals(ScrollStep.Way.UP, d.way);
		assertEquals(10, d.amount);
		assertEquals("scroll:up:10", d.toAction());
		assertNull(d.message());
	}

	@Test
	public void scrollDownFiveLines() {
		ScrollStep.Decision d = ScrollStep.decide("scrolldown", "5", 200);
		assertEquals(ScrollStep.Kind.MOVE, d.kind);
		assertEquals(ScrollStep.Way.DOWN, d.way);
		assertEquals(5, d.amount);
		assertEquals("scroll:down:5", d.toAction());
	}

	@Test
	public void verticalIgnoresWidth() {
		ScrollStep.Decision d = ScrollStep.decide("scrollup", "3", 100);
		assertEquals(ScrollStep.Kind.MOVE, d.kind);
		assertEquals(3, d.amount);
	}

	@Test
	public void badNumbersDoNotMove() {
		assertBad("scrollup", "0");
		assertBad("scrollup", "-3");
		assertBad("scrollup", "+10");
		assertBad("scrollup", "10.5");
		assertBad("scrollup", "10 extra");
		assertBad("scrollup", "ten");
		assertBad("scrolldown", "9999999999");
		assertBad("scrollleft", "0", 150);
	}

	@Test
	public void paddedNumberIsThePlayersNumber() {
		ScrollStep.Decision d = ScrollStep.decide("scrollup", "  10\n", 100);
		assertEquals(ScrollStep.Kind.MOVE, d.kind);
		assertEquals(10, d.amount);
	}

	@Test
	public void widthAt100DoesNotScrollSideways() {
		ScrollStep.Decision left = ScrollStep.decide("scrollleft", "8", 100);
		assertEquals(ScrollStep.Kind.NOTHING_SIDEWAYS, left.kind);
		assertEquals(0, left.amount);
		assertNull(left.toAction());
		assertTrue(left.message().contains("nothing to scroll sideways"));
		assertTrue(left.message().contains("100%"));

		ScrollStep.Decision bare = ScrollStep.decide("scrollright", "", 100);
		assertEquals(ScrollStep.Kind.NOTHING_SIDEWAYS, bare.kind);
		ScrollStep.Decision junk = ScrollStep.decide("scrollleft", "nope", 100);
		assertEquals(ScrollStep.Kind.NOTHING_SIDEWAYS, junk.kind);
		ScrollStep.Decision below = ScrollStep.decide("scrollright", "4", 99);
		assertEquals(ScrollStep.Kind.NOTHING_SIDEWAYS, below.kind);
	}

	@Test
	public void sidewaysUsesThePlayersColumnCount() {
		ScrollStep.Decision left = ScrollStep.decide("scrollleft", "8", 150);
		assertEquals(ScrollStep.Kind.MOVE, left.kind);
		assertEquals(ScrollStep.Way.LEFT, left.way);
		assertEquals(8, left.amount);
		assertTrue(left.columns());
		assertEquals("scroll:left:8", left.toAction());

		ScrollStep.Decision right = ScrollStep.decide("scrollright", "1", 101);
		assertEquals(ScrollStep.Kind.MOVE, right.kind);
		assertEquals(ScrollStep.Way.RIGHT, right.way);
		assertEquals(1, right.amount);
		assertEquals("scroll:right:1", right.toAction());

		ScrollStep.Decision wide = ScrollStep.decide("scrollright", "20", 200);
		assertEquals(20, wide.amount);
	}

	@Test
	public void sidewaysAbove100StillNeedsANumber() {
		ScrollStep.Decision d = ScrollStep.decide("scrollleft", "", 150);
		assertEquals(ScrollStep.Kind.MISSING_NUMBER, d.kind);
		assertEquals(0, d.amount);
		assertTrue(d.message().contains("needs a number of columns"));
		assertTrue(d.message().contains(".scrollleft 10"));
	}

	@Test
	public void unknownCommandIsNotAScroll() {
		assertNull(ScrollStep.decide("scroll", "10", 150));
		assertNull(ScrollStep.decide("pageup", "10", 150));
		assertNull(ScrollStep.decide(null, "10", 150));
	}

	@Test
	public void actionRoundTrip() {
		ScrollStep.Decision d = ScrollStep.decide("scrollup", "10", 100);
		ScrollStep.Decision back = ScrollStep.fromAction(d.toAction());
		assertEquals(ScrollStep.Kind.MOVE, back.kind);
		assertEquals(ScrollStep.Way.UP, back.way);
		assertEquals(10, back.amount);
		assertEquals(d.toAction(), back.toAction());
	}

	@Test
	public void actionRejectsAnythingElse() {
		assertNull(ScrollStep.fromAction(null));
		assertNull(ScrollStep.fromAction("scroll:up"));
		assertNull(ScrollStep.fromAction("scroll:up:"));
		assertNull(ScrollStep.fromAction("scroll:up:0"));
		assertNull(ScrollStep.fromAction("scroll:up:-1"));
		assertNull(ScrollStep.fromAction("scroll:page:1"));
		assertNull(ScrollStep.fromAction("last:10"));
		assertNull(ScrollStep.fromAction("scroll:up:10:1"));
	}

	@Test
	public void upShowsTheTextThatWasAbove() {
		assertEquals(10, ScrollStep.lineDelta(ScrollStep.Way.UP, 10, false));
		assertEquals(-10, ScrollStep.lineDelta(ScrollStep.Way.UP, 10, true));
		assertEquals(-5, ScrollStep.lineDelta(ScrollStep.Way.DOWN, 5, false));
		assertEquals(5, ScrollStep.lineDelta(ScrollStep.Way.DOWN, 5, true));
		assertEquals(0, ScrollStep.lineDelta(ScrollStep.Way.LEFT, 4, false));
		assertEquals(0, ScrollStep.lineDelta(ScrollStep.Way.UP, 0, false));
	}

	@Test
	public void rightIsPositiveColumns() {
		assertEquals(8, ScrollStep.columnDelta(ScrollStep.Way.RIGHT, 8));
		assertEquals(-8, ScrollStep.columnDelta(ScrollStep.Way.LEFT, 8));
		assertEquals(0, ScrollStep.columnDelta(ScrollStep.Way.UP, 8));
	}

	@Test
	public void sidewaysFloorMatchesWidth() {
		assertEquals(CanvasWidthCommand.MIN_PERCENT, ScrollStep.FIT_WIDTH_PERCENT);
	}

	@Test
	public void helpNamesTheExamplesAndTheWidthFloor() {
		String help = ScrollStep.helpText();
		assertTrue(help.contains(".scrollup 10"));
		assertTrue(help.contains(".scrolldown 5"));
		assertTrue(help.contains("nothing to scroll sideways"));
		assertTrue(help.contains("columns"));
		assertTrue(ScrollStep.summary("scrollup").startsWith("needs a number of lines"));
		assertTrue(ScrollStep.summary("scrolldown").contains("below"));
		assertTrue(ScrollStep.summary("scrollleft").startsWith("nothing to scroll sideways"));
		assertTrue(ScrollStep.summary("scrollright").contains("columns"));
		assertTrue(ScrollStep.isScrollHelp("scroll"));
		assertTrue(ScrollStep.isScrollHelp("scrollup"));
		assertTrue(!ScrollStep.isScrollHelp("width"));
	}

	private static void assertBad(final String command, final String argument) {
		assertBad(command, argument, 100);
	}

	private static void assertBad(final String command, final String argument, final int width) {
		ScrollStep.Decision d = ScrollStep.decide(command, argument, width);
		assertEquals(command, ScrollStep.Kind.BAD_NUMBER, d.kind);
		assertEquals(0, d.amount);
		assertNull(d.toAction());
		assertTrue(d.message().contains("1 or more"));
	}
}
