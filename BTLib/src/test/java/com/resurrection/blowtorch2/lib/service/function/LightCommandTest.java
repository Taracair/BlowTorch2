package com.resurrection.blowtorch2.lib.service.function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LightCommandTest {

	@Test
	public void usageNamesTheOptionAndTheToggle() {
		String u = LightCommand.usage();
		assertTrue(u.contains(".light"));
		assertTrue(u.contains("toggle"));
	}

	@Test
	public void reusesOnOffWords() {
		assertEquals(Boolean.TRUE, DimRepeatCommand.parseOnOff("on"));
		assertEquals(Boolean.FALSE, DimRepeatCommand.parseOnOff("off"));
		assertNull(DimRepeatCommand.parseOnOff("paper"));
	}
}
