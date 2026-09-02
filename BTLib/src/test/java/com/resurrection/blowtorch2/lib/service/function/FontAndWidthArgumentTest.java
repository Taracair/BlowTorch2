package com.resurrection.blowtorch2.lib.service.function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * Argument parsing for .font and .width. Both take a number, a signed step from
 * where you are, or a word — and both have to clamp, because a button sending
 * ".font +2" twenty times must not end up at a font size that fits four words
 * on the screen with no way back except the options menu.
 */
public class FontAndWidthArgumentTest {

	@Test
	public void fontTakesAnAbsoluteSize() {
		assertEquals(Integer.valueOf(18), FontCommand.resolve("18", 20));
	}

	@Test
	public void fontStepsFromTheCurrentSize() {
		assertEquals(Integer.valueOf(22), FontCommand.resolve("+2", 20));
		assertEquals(Integer.valueOf(18), FontCommand.resolve("-2", 20));
	}

	@Test
	public void fontKeepsATabletSize() {
		assertEquals(Integer.valueOf(50), FontCommand.resolve("50", 20));
		assertEquals(50, FontCommand.clamp(50));
		assertEquals(30, FontCommand.clamp(30));
	}

	@Test
	public void fontClampsBothWays() {
		assertEquals(Integer.valueOf(FontCommand.MAX_SIZE), FontCommand.resolve("+99", 20));
		assertEquals(Integer.valueOf(FontCommand.MIN_SIZE), FontCommand.resolve("-99", 20));
		assertEquals(Integer.valueOf(FontCommand.MAX_SIZE), FontCommand.resolve("500", 20));
	}

	@Test
	public void fontKnowsTheWordDefault() {
		assertEquals(Integer.valueOf(FontCommand.DEFAULT_SIZE), FontCommand.resolve("default", 33));
	}

	@Test
	public void fontRejectsAnythingElse() {
		assertNull(FontCommand.resolve("big", 20));
		assertNull(FontCommand.resolve("", 20));
		assertNull(FontCommand.resolve(null, 20));
	}

	@Test
	public void widthTakesPercentsAndSteps() {
		assertEquals(Integer.valueOf(150), CanvasWidthCommand.resolve("150", 100));
		assertEquals(Integer.valueOf(125), CanvasWidthCommand.resolve("+25", 100));
		assertEquals(Integer.valueOf(125), CanvasWidthCommand.resolve("-25", 150));
	}

	/** 100 is "fits the screen"; below it there is nothing to scroll to. */
	@Test
	public void widthNeverGoesUnderAHundredOrOverTwoHundred() {
		assertEquals(Integer.valueOf(100), CanvasWidthCommand.resolve("50", 150));
		assertEquals(Integer.valueOf(100), CanvasWidthCommand.resolve("-99", 150));
		assertEquals(Integer.valueOf(200), CanvasWidthCommand.resolve("400", 100));
	}

	@Test
	public void widthRejectsAnythingElse() {
		assertNull(CanvasWidthCommand.resolve("wide", 100));
		assertNull(CanvasWidthCommand.resolve("", 100));
	}
}
