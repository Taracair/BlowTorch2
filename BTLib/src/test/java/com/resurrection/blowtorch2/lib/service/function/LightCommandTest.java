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
		assertTrue(u.contains("1-5"));
		assertTrue(u.contains("shade"));
	}

	@Test
	public void reusesOnOffWords() {
		assertEquals(Boolean.TRUE, DimRepeatCommand.parseOnOff("on"));
		assertEquals(Boolean.FALSE, DimRepeatCommand.parseOnOff("off"));
		assertNull(DimRepeatCommand.parseOnOff("paper"));
	}

	@Test
	public void shadeTokensAreOneThroughFive() {
		assertEquals(Integer.valueOf(1), LightCommand.parseShadeToken("1"));
		assertEquals(Integer.valueOf(5), LightCommand.parseShadeToken("5"));
		assertNull(LightCommand.parseShadeToken("0"));
		assertNull(LightCommand.parseShadeToken("6"));
		assertNull(LightCommand.parseShadeToken("on"));
		assertEquals("warm", LightCommand.shadeLabel(2));
		assertEquals("near-white", LightCommand.shadeLabel(5));
	}
}
