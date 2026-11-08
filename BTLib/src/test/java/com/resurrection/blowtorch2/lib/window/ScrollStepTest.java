package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.resurrection.blowtorch2.lib.service.function.CanvasWidthCommand;

/**
 * {@code .scrollv} and {@code .scrollh}. The number is the player's, including
 * the sign, and it is added to the current view. A missing number is not
 * filled in. Sideways scrolling starts above the same 100% floor as
 * {@code .width}.
 */
public class ScrollStepTest {

	@Test
	public void scrollVNeedsANumberAndDoesNotInventOne() {
		ScrollStep.Decision d = ScrollStep.decide("scrollv", "", 100);
		assertEquals(ScrollStep.Kind.MISSING_NUMBER, d.kind);
		assertEquals(0, d.amount);
		assertNull(d.toAction());
		assertTrue(d.message().contains("needs a number"));
		assertTrue(d.message().contains(".scrollv 10"));
	}

	@Test
	public void scrollHNeedsANumberWhenTheCanvasIsWide() {
		ScrollStep.Decision d = ScrollStep.decide(".scrollh", "   ", 150);
		assertEquals(ScrollStep.Kind.MISSING_NUMBER, d.kind);
		assertEquals(0, d.amount);
		assertTrue(d.message().contains(".scrollh 10"));
	}

	@Test
	public void scrollVTenLinesUp() {
		ScrollStep.Decision d = ScrollStep.decide("scrollv", "10", 100);
		assertEquals(ScrollStep.Kind.MOVE, d.kind);
		assertEquals(ScrollStep.Axis.VERTICAL, d.axis);
		assertEquals(10, d.amount);
		assertEquals("scroll:v:10", d.toAction());
		assertNull(d.message());
	}

	@Test
	public void scrollVTenLinesDown() {
		ScrollStep.Decision d = ScrollStep.decide("scrollv", "-10", 200);
		assertEquals(ScrollStep.Kind.MOVE, d.kind);
		assertEquals(ScrollStep.Axis.VERTICAL, d.axis);
		assertEquals(-10, d.amount);
		assertEquals("scroll:v:-10", d.toAction());
	}

	@Test
	public void verticalIgnoresWidth() {
		ScrollStep.Decision d = ScrollStep.decide("scrollv", "-3", 100);
		assertEquals(ScrollStep.Kind.MOVE, d.kind);
		assertEquals(-3, d.amount);
	}

	@Test
	public void badNumbersDoNotMove() {
		assertBad("scrollv", "0");
		assertBad("scrollv", "+0");
		assertBad("scrollv", "-0");
		assertBad("scrollv", "10.5");
		assertBad("scrollv", "10 extra");
		assertBad("scrollv", "ten");
		assertBad("scrollv", "--10");
		assertBad("scrollv", "9999999999");
		assertBad("scrollh", "0", 150);
	}

	@Test
	public void paddedNumberIsThePlayersNumber() {
		ScrollStep.Decision d = ScrollStep.decide("scrollv", "  -10\n", 100);
		assertEquals(ScrollStep.Kind.MOVE, d.kind);
		assertEquals(-10, d.amount);
	}

	@Test
	public void leadingPlusIsPositive() {
		ScrollStep.Decision d = ScrollStep.decide("scrollh", "+8", 150);
		assertEquals(ScrollStep.Kind.MOVE, d.kind);
		assertEquals(8, d.amount);
	}

	@Test
	public void widthAt100DoesNotScrollSideways() {
		ScrollStep.Decision left = ScrollStep.decide("scrollh", "-8", 100);
		assertEquals(ScrollStep.Kind.NOTHING_SIDEWAYS, left.kind);
		assertEquals(0, left.amount);
		assertNull(left.toAction());
		assertTrue(left.message().contains("nothing to scroll sideways"));
		assertTrue(left.message().contains("100%"));

		ScrollStep.Decision bare = ScrollStep.decide("scrollh", "", 100);
		assertEquals(ScrollStep.Kind.NOTHING_SIDEWAYS, bare.kind);
		ScrollStep.Decision junk = ScrollStep.decide("scrollh", "nope", 100);
		assertEquals(ScrollStep.Kind.NOTHING_SIDEWAYS, junk.kind);
		ScrollStep.Decision below = ScrollStep.decide("scrollh", "4", 99);
		assertEquals(ScrollStep.Kind.NOTHING_SIDEWAYS, below.kind);
	}

	@Test
	public void sidewaysUsesThePlayersColumnCount() {
		ScrollStep.Decision left = ScrollStep.decide("scrollh", "-8", 150);
		assertEquals(ScrollStep.Kind.MOVE, left.kind);
		assertEquals(ScrollStep.Axis.HORIZONTAL, left.axis);
		assertEquals(-8, left.amount);
		assertTrue(left.horizontal());
		assertEquals("scroll:h:-8", left.toAction());

		ScrollStep.Decision right = ScrollStep.decide("scrollh", "1", 101);
		assertEquals(ScrollStep.Kind.MOVE, right.kind);
		assertEquals(1, right.amount);
		assertEquals("scroll:h:1", right.toAction());

		ScrollStep.Decision wide = ScrollStep.decide("scrollh", "20", 200);
		assertEquals(20, wide.amount);
	}

	@Test
	public void unknownCommandIsNotAScroll() {
		assertNull(ScrollStep.decide("scroll", "10", 150));
		assertNull(ScrollStep.decide("scrollup", "10", 150));
		assertNull(ScrollStep.decide("pageup", "10", 150));
		assertNull(ScrollStep.decide(null, "10", 150));
	}

	@Test
	public void actionRoundTrip() {
		ScrollStep.Decision d = ScrollStep.decide("scrollv", "-10", 100);
		ScrollStep.Decision back = ScrollStep.fromAction(d.toAction());
		assertEquals(ScrollStep.Kind.MOVE, back.kind);
		assertEquals(ScrollStep.Axis.VERTICAL, back.axis);
		assertEquals(-10, back.amount);
		assertEquals(d.toAction(), back.toAction());
	}

	@Test
	public void actionRejectsAnythingElse() {
		assertNull(ScrollStep.fromAction(null));
		assertNull(ScrollStep.fromAction("scroll:v"));
		assertNull(ScrollStep.fromAction("scroll:v:"));
		assertNull(ScrollStep.fromAction("scroll:v:0"));
		assertNull(ScrollStep.fromAction("scroll:up:10"));
		assertNull(ScrollStep.fromAction("scroll:page:1"));
		assertNull(ScrollStep.fromAction("last:10"));
		assertNull(ScrollStep.fromAction("scroll:v:10:1"));
		assertNull(ScrollStep.fromAction("scroll:h:-"));
	}

	@Test
	public void aSecondStepAddsToTheFirst() {
		double min = 800;
		double max = 50000;
		int step = ScrollStep.verticalPixels(10, false, 40);
		assertEquals(400, step);
		double at = ScrollStep.clampScrollback(min + step, min, max);
		assertEquals(1200d, at, 0.001);
		at = ScrollStep.clampScrollback(at + step, min, max);
		assertEquals(1600d, at, 0.001);
	}

	@Test
	public void negativeMovesBackTowardTheLiveEdge() {
		assertEquals(-400, ScrollStep.verticalPixels(-10, false, 40));
		assertEquals(-400, ScrollStep.verticalPixels(10, true, 40));
		assertEquals(200, ScrollStep.verticalPixels(-5, true, 40));
		assertEquals(0, ScrollStep.verticalPixels(0, false, 40));
		assertEquals(0, ScrollStep.verticalPixels(4, false, 0));
	}

	@Test
	public void clampStaysAtTheLiveEdgeWhenTheBufferFits() {
		assertEquals(800d, ScrollStep.clampScrollback(1200, 800, 400), 0.001);
		assertEquals(800d, ScrollStep.clampScrollback(100, 800, 50000), 0.001);
		assertEquals(50000d, ScrollStep.clampScrollback(90000, 800, 50000), 0.001);
	}

	@Test
	public void aScrollLineDoesNotJumpHome() {
		assertTrue(ScrollStep.keepsPlace(".scrollv 10"));
		assertTrue(ScrollStep.keepsPlace("  .scrollv -10  "));
		assertTrue(ScrollStep.keepsPlace(".scrollh -4"));
		assertTrue(ScrollStep.keepsPlace(".scrollv 10;.scrollh -2"));
		assertTrue(ScrollStep.keepsPlace(".ScrollV 3"));
		assertFalse(ScrollStep.keepsPlace("look"));
		assertFalse(ScrollStep.keepsPlace(".scrollv 10;look"));
		assertFalse(ScrollStep.keepsPlace("scrollv 10"));
		assertFalse(ScrollStep.keepsPlace(".scrollup 10"));
		assertFalse(ScrollStep.keepsPlace(null));
		assertFalse(ScrollStep.keepsPlace(""));
		assertFalse(ScrollStep.keepsPlace(";;"));
	}

	@Test
	public void sidewaysFloorMatchesWidth() {
		assertEquals(CanvasWidthCommand.MIN_PERCENT, ScrollStep.FIT_WIDTH_PERCENT);
	}

	@Test
	public void helpNamesTheExamplesAndTheWidthFloor() {
		String help = ScrollStep.helpText();
		assertTrue(help.contains(".scrollv <N>"));
		assertTrue(help.contains(".scrollh <N>"));
		assertTrue(help.contains("-10"));
		assertTrue(help.contains("nothing to scroll sideways"));
		assertTrue(help.contains("does not jump"));
		assertTrue(ScrollStep.summary("scrollv").startsWith("needs a number of lines"));
		assertTrue(ScrollStep.summary("scrollh").contains("columns"));
		assertTrue(ScrollStep.isScrollHelp("scroll"));
		assertTrue(ScrollStep.isScrollHelp("scrollv"));
		assertTrue(ScrollStep.isScrollHelp("scrollh"));
		assertTrue(!ScrollStep.isScrollHelp("width"));
		assertTrue(!ScrollStep.isScrollHelp("scrollup"));
		assertTrue(help.contains(".scrollnew"));
		assertTrue(ScrollStep.summary("scrollnew").contains("newest"));
		assertTrue(ScrollStep.isScrollHelp("scrollnew"));
	}

	@Test
	public void scrollnewJumpsAndTakesNoNumber() {
		ScrollStep.Decision jump = ScrollStep.decide("scrollnew", "", 100);
		assertEquals(ScrollStep.Kind.JUMP, jump.kind);
		assertEquals("scroll:new", jump.toAction());
		assertEquals(ScrollStep.Kind.JUMP, ScrollStep.fromAction("scroll:new").kind);
		ScrollStep.Decision extra = ScrollStep.decide(".scrollnew", "10", 100);
		assertEquals(ScrollStep.Kind.EXTRA, extra.kind);
		assertNull(extra.toAction());
		assertTrue(extra.message().contains("no number"));
		assertTrue(!ScrollStep.keepsPlace(".scrollnew"));
		assertTrue(!ScrollStep.keepsPlace(".scrollnew;.scrollv 10"));
	}

	private static void assertBad(final String command, final String argument) {
		assertBad(command, argument, 100);
	}

	private static void assertBad(final String command, final String argument, final int width) {
		ScrollStep.Decision d = ScrollStep.decide(command, argument, width);
		assertEquals(command, ScrollStep.Kind.BAD_NUMBER, d.kind);
		assertEquals(0, d.amount);
		assertNull(d.toAction());
		assertTrue(d.message().contains("other than 0"));
	}
}
