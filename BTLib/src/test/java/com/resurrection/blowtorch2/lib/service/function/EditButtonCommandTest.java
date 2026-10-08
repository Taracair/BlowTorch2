package com.resurrection.blowtorch2.lib.service.function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class EditButtonCommandTest {

	@Test
	public void onOffOnly() {
		assertEquals(Boolean.TRUE, EditButtonCommand.parseOnOff("on"));
		assertEquals(Boolean.FALSE, EditButtonCommand.parseOnOff("off"));
	}

	@Test
	public void trailingWordIsNotOn() {
		assertNull(EditButtonCommand.parseArgument("on please"));
		assertNull(EditButtonCommand.parseArgument("off now"));
		assertEquals(Boolean.TRUE, EditButtonCommand.parseArgument("on"));
		assertEquals(Boolean.FALSE, EditButtonCommand.parseArgument("  OFF "));
	}

	@Test
	public void synonymsRejected() {
		assertNull(EditButtonCommand.parseOnOff("yes"));
		assertNull(EditButtonCommand.parseOnOff("1"));
		assertNull(EditButtonCommand.parseOnOff("show"));
		assertNull(EditButtonCommand.parseOnOff("hide"));
		assertNull(EditButtonCommand.parseOnOff(null));
		assertNull(EditButtonCommand.parseOnOff(""));
	}
}
