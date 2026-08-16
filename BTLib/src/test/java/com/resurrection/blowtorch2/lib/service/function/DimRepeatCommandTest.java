package com.resurrection.blowtorch2.lib.service.function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.resurrection.blowtorch2.lib.window.RepeatedLineDimmer;

public class DimRepeatCommandTest {

	@Test
	public void onOffAndToggleWords() {
		assertEquals(Boolean.TRUE, DimRepeatCommand.parseOnOff("on"));
		assertEquals(Boolean.FALSE, DimRepeatCommand.parseOnOff("off"));
		assertNull(DimRepeatCommand.parseOnOff("lines"));
		assertNull(DimRepeatCommand.parseOnOff("strength"));
	}

	@Test
	public void linesAndStrengthWords() {
		assertTrue(DimRepeatCommand.isLinesWord("lines"));
		assertTrue(DimRepeatCommand.isLinesWord("remember"));
		assertTrue(DimRepeatCommand.isStrengthWord("strength"));
		assertTrue(DimRepeatCommand.isStrengthWord("power"));
		assertFalse(DimRepeatCommand.isLinesWord("on"));
		assertFalse(DimRepeatCommand.isStrengthWord("lines"));
	}

	@Test
	public void parseIntRejectsJunk() {
		assertEquals(Integer.valueOf(12), DimRepeatCommand.parseInt("12"));
		assertNull(DimRepeatCommand.parseInt("twelve"));
		assertNull(DimRepeatCommand.parseInt(""));
		assertNull(DimRepeatCommand.parseInt(null));
	}

	@Test
	public void statusMentionsAllThreeKnobs() {
		String s = DimRepeatCommand.statusLine(true, 8, 70);
		assertTrue(s.contains("on"));
		assertTrue(s.contains("8"));
		assertTrue(s.contains("70"));
		assertTrue(s.contains("30% brightness"));
	}

	@Test
	public void defaultStrengthKeepsHalf() {
		assertEquals(0.5f, RepeatedLineDimmer.keepFactor(
				RepeatedLineDimmer.DEFAULT_STRENGTH), 0.001f);
	}
}
