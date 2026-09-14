package com.resurrection.blowtorch2.lib.service.function;

import static org.junit.Assert.assertEquals;

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
}
