package com.resurrection.blowtorch2.lib.service.function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LastBarRequestTest {

	@Test
	public void countOnOffAndEmpty() {
		LastBarRequest five = LastBarRequest.parse("5");
		assertEquals(LastBarRequest.COUNT, five.kind);
		assertEquals(5, five.number);
		assertEquals(LastBarRequest.ON, LastBarRequest.parse("on").kind);
		assertEquals(LastBarRequest.OFF, LastBarRequest.parse("off").kind);
		assertEquals(LastBarRequest.STATUS, LastBarRequest.parse(null).kind);
		assertEquals(LastBarRequest.STATUS, LastBarRequest.parse("").kind);
		assertEquals(LastBarRequest.STATUS, LastBarRequest.parse("   ").kind);
	}

	@Test
	public void lengthAndOrder() {
		LastBarRequest length = LastBarRequest.parse("length 12");
		assertEquals(LastBarRequest.LENGTH, length.kind);
		assertEquals(12, length.number);
		assertEquals(LastBarRequest.MISUSE, LastBarRequest.parse("length 2").kind);
		assertEquals(LastBarRequest.MISUSE, LastBarRequest.parse("length 41").kind);
		LastBarRequest right = LastBarRequest.parse("order right");
		assertEquals(LastBarRequest.ORDER, right.kind);
		assertTrue(right.right);
		LastBarRequest left = LastBarRequest.parse("order left");
		assertEquals(LastBarRequest.ORDER, left.kind);
		assertFalse(left.right);
		assertEquals(LastBarRequest.ORDER_STATUS, LastBarRequest.parse("order").kind);
	}

	@Test
	public void junkIsLocalUsage() {
		assertEquals(LastBarRequest.MISUSE, LastBarRequest.parse("junk").kind);
		assertEquals(LastBarRequest.MISUSE, LastBarRequest.parse("length").kind);
		assertEquals(LastBarRequest.MISUSE, LastBarRequest.parse("order up").kind);
		assertEquals(LastBarRequest.MISUSE, LastBarRequest.parse("0").kind);
		assertEquals(LastBarRequest.MISUSE, LastBarRequest.parse("101").kind);
	}
}
