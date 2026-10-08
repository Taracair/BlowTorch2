package com.resurrection.blowtorch2.lib.service.function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LastRecallTest {

	@Test
	public void dottedLastIsTheNewest() {
		assertEquals(1, LastRecall.parse(".last", 100));
		assertEquals(1, LastRecall.parse(".last ", 100));
		assertEquals(1, LastRecall.parse(".1last", 100));
	}

	@Test
	public void dottedNumberIsThatFarBack() {
		assertEquals(2, LastRecall.parse(".2last", 100));
		assertEquals(3, LastRecall.parse(".3last", 100));
		assertEquals(75, LastRecall.parse(".75last", 75));
		assertEquals(100, LastRecall.parse(".100last", 100));
		assertEquals(10, LastRecall.parse(".10last", 10));
	}

	@Test
	public void undottedLinesAreNotLocal() {
		assertEquals(LastRecall.NOT_RECALL, LastRecall.parse("last", 100));
		assertEquals(LastRecall.NOT_RECALL, LastRecall.parse("2last", 100));
		assertEquals(LastRecall.NOT_RECALL, LastRecall.parse("3last", 100));
		assertEquals(LastRecall.NOT_RECALL, LastRecall.parse("0last", 100));
		assertEquals(LastRecall.NOT_RECALL, LastRecall.parse("-1last", 100));
		assertFalse(LastRecall.embeds("2last", 100));
		assertFalse(LastRecall.embeds("look;2last", 100));
	}

	@Test
	public void rejectedShapesAreNotAnIndex() {
		assertEquals(LastRecall.NOT_RECALL, LastRecall.parse(".last 3", 100));
		assertEquals(LastRecall.NOT_RECALL, LastRecall.parse(".lasts", 100));
		assertEquals(LastRecall.NOT_RECALL, LastRecall.parse(".3lasts", 100));
		assertEquals(LastRecall.NOT_RECALL, LastRecall.parse(".-1last", 100));
		assertEquals(LastRecall.NOT_RECALL, LastRecall.parse(null, 100));
		assertEquals(LastRecall.NOT_RECALL, LastRecall.parse("", 100));
		assertEquals(LastRecall.NOT_RECALL, LastRecall.parse(".Last", 100));
		assertTrue(LastRecall.isMisused(".last 3"));
		assertFalse(LastRecall.isMisused(".last"));
		assertFalse(LastRecall.isMisused(".lasts"));
	}

	@Test
	public void zeroAndPastTheCapAreLocalErrors() {
		assertEquals(LastRecall.BAD_INDEX, LastRecall.parse(".0last", 100));
		assertEquals(LastRecall.BAD_INDEX, LastRecall.parse(".00last", 100));
		assertEquals(LastRecall.BAD_INDEX, LastRecall.parse(".11last", 10));
		assertEquals(LastRecall.BAD_INDEX, LastRecall.parse(".76last", 75));
		assertEquals(LastRecall.BAD_INDEX, LastRecall.parse(".101last", 100));
		assertEquals(LastRecall.BAD_INDEX, LastRecall.parse(".99999last", 100));
		assertEquals(LastRecall.BAD_INDEX, LastRecall.parse(".9999999999last", 100));
		// Caller passed a value outside the keeper clamp (10–100).
		assertEquals(LastRecall.BAD_INDEX, LastRecall.parse(".101last", 200));
		assertEquals(10, LastRecall.parse(".10last", 1));
	}

	@Test
	public void aSegmentInsideTheLineStillCounts() {
		assertTrue(LastRecall.embeds(".last", 100));
		assertTrue(LastRecall.embeds(".3last", 100));
		assertTrue(LastRecall.embeds(".99999last", 75));
		assertTrue(LastRecall.embeds(".last 3", 100));
		assertTrue(LastRecall.embeds("look;.last", 100));
		assertTrue(LastRecall.embeds("north;.2last", 100));
		assertFalse(LastRecall.embeds("north", 100));
		assertFalse(LastRecall.embeds("look;north", 100));
	}

	@Test
	public void inputPutsThatCommandInTheBar() {
		assertEquals(1, LastRecall.parseFill(".last input", 100));
		assertEquals(1, LastRecall.parseFill(".last input ", 100));
		assertEquals(1, LastRecall.parseFill(".1last input", 100));
		assertEquals(2, LastRecall.parseFill(".2last input", 100));
		assertEquals(3, LastRecall.parseFill(".3last input", 75));
		assertEquals(75, LastRecall.parseFill(".75last input", 75));
		assertEquals(LastRecall.NOT_RECALL, LastRecall.parse(".last input", 100));
		assertEquals(LastRecall.NOT_RECALL, LastRecall.parse(".2last input", 100));
		assertFalse(LastRecall.isMisused(".last input"));
		assertFalse(LastRecall.isMisused(".2last input"));
	}

	@Test
	public void inputWithoutTheDotOrTheSpaceIsNotLocal() {
		assertEquals(LastRecall.NOT_RECALL, LastRecall.parseFill("last input", 100));
		assertEquals(LastRecall.NOT_RECALL, LastRecall.parseFill("2last input", 100));
		assertEquals(LastRecall.NOT_RECALL, LastRecall.parseFill(".lastinput", 100));
		assertEquals(LastRecall.NOT_RECALL, LastRecall.parseFill(".2lastinput", 100));
		assertEquals(LastRecall.NOT_RECALL, LastRecall.parseFill(".last  input", 100));
		assertEquals(LastRecall.NOT_RECALL, LastRecall.parseFill(".2last  input", 100));
		assertEquals(LastRecall.NOT_RECALL, LastRecall.parseFill(".last input extra", 100));
		assertTrue(LastRecall.isMisused(".last  input"));
		assertFalse(LastRecall.isMisused(".lastinput"));
		assertFalse(LastRecall.isMisused("last input"));
		assertFalse(LastRecall.embeds("last input", 100));
		assertFalse(LastRecall.embeds(".lastinput", 100));
		assertFalse(LastRecall.embeds("2last input", 100));
	}

	@Test
	public void inputPastTheCapIsALocalError() {
		assertEquals(LastRecall.BAD_INDEX, LastRecall.parseFill(".0last input", 100));
		assertEquals(LastRecall.BAD_INDEX, LastRecall.parseFill(".101last input", 100));
		assertEquals(LastRecall.BAD_INDEX, LastRecall.parseFill(".99999last input", 75));
		assertTrue(LastRecall.embeds(".last input", 100));
		assertTrue(LastRecall.embeds(".2last input", 100));
		assertTrue(LastRecall.embeds(".99999last input", 75));
		assertTrue(LastRecall.embeds("look;.last input", 100));
		assertTrue(LastRecall.embeds("north;.2last input", 100));
	}
}
