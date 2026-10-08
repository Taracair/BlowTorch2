package com.resurrection.blowtorch2.lib.service.function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PickCommandTest {

	@Test
	public void emptyIsOnce() {
		assertEquals(PickCommand.MODE_ONCE, PickCommand.parseMode(""));
		assertEquals(PickCommand.MODE_ONCE, PickCommand.parseMode("once"));
		assertEquals(PickCommand.MODE_ONCE, PickCommand.parseMode("one"));
	}

	@Test
	public void holdAliases() {
		assertEquals(PickCommand.MODE_HOLD, PickCommand.parseMode("hold"));
		assertEquals(PickCommand.MODE_HOLD, PickCommand.parseMode("on"));
		assertEquals(PickCommand.MODE_HOLD, PickCommand.parseMode("persist"));
		assertEquals(PickCommand.MODE_HOLD, PickCommand.parseMode("sticky"));
	}

	@Test
	public void tapAndOff() {
		assertEquals(PickCommand.MODE_TAP, PickCommand.parseMode("tap"));
		assertEquals(PickCommand.MODE_TAP, PickCommand.parseMode("gesture"));
		assertEquals(PickCommand.MODE_OFF, PickCommand.parseMode("off"));
		assertEquals(-1, PickCommand.parseMode("banana"));
	}

	@Test
	public void buttonModes() {
		assertEquals(PickCommand.MODE_BUTTON, PickCommand.parseMode("button"));
		assertEquals(PickCommand.MODE_BUTTON, PickCommand.parseMode("slide"));
		assertEquals(PickCommand.MODE_BUTTON_DOUBLE,
				PickCommand.parseMode("button-double"));
		assertEquals(PickCommand.MODE_BUTTON_DOUBLE,
				PickCommand.parseMode("button double"));
	}

	@Test
	public void insertKeepsTheMode() {
		assertEquals(PickCommand.MODE_ONCE, PickCommand.parseArgs("insert").mode);
		assertTrue(PickCommand.parseArgs("insert").insert);
		assertEquals(PickCommand.MODE_HOLD, PickCommand.parseArgs("hold insert").mode);
		assertTrue(PickCommand.parseArgs("tap insert").insert);
		assertTrue(PickCommand.parseArgs("insert button").insert);
		assertEquals(PickCommand.MODE_BUTTON, PickCommand.parseArgs("insert button").mode);
		assertEquals(PickCommand.MODE_BUTTON_DOUBLE,
				PickCommand.parseArgs("button-double insert").mode);
		assertTrue(PickCommand.parseArgs("button double insert").insert);
		assertEquals(PickCommand.MODE_ONCE | PickCommand.INSERT_FLAG,
				PickCommand.parseArgs("insert").packed());
		assertFalse(PickCommand.parseArgs("hold").insert);
		assertEquals(-1, PickCommand.parseArgs("off insert").mode);
		assertEquals(-1, PickCommand.parseArgs("button hold").mode);
		assertEquals(-1, PickCommand.parseArgs("button banana").mode);
	}

	@Test
	public void loupeIsNotAPickMode() {
		assertEquals(-1, PickCommand.parseMode("loupe"));
		assertEquals(-1, PickCommand.parseMode("size"));
		assertEquals(-1, PickCommand.parseMode("zoom"));
		assertTrue(PickCommand.isLoupeCommand("loupe"));
		assertTrue(PickCommand.isLoupeCommand("loupe size 118"));
		assertTrue(PickCommand.isLoupeCommand("size 130"));
		assertTrue(PickCommand.isLoupeCommand("zoom 250"));
		assertFalse(PickCommand.isLoupeCommand("hold"));
		assertFalse(PickCommand.isLoupeCommand(""));
	}

	@Test
	public void loupePercentParses() {
		assertEquals(Integer.valueOf(118), PickCommand.parsePercent("118"));
		assertEquals(null, PickCommand.parsePercent("x"));
	}
}
