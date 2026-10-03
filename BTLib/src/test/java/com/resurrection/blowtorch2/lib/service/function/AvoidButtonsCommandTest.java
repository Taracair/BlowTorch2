package com.resurrection.blowtorch2.lib.service.function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.resurrection.blowtorch2.lib.service.WindowToken;

public class AvoidButtonsCommandTest {

	@Test
	public void usageNamesTheOptionAndToggle() {
		String u = AvoidButtonsCommand.usage();
		assertTrue(u.contains(".avoidbuttons"));
		assertTrue(u.contains("toggle"));
		assertTrue(u.contains("letters"));
		assertTrue(u.contains("words"));
		assertTrue(u.contains("grid pad"));
		assertTrue(u.contains("floating"));
	}

	@Test
	public void keyMatchesTheWindowOption() {
		assertEquals("text_avoid_buttons", AvoidButtonsCommand.OPTION_KEY);
		assertEquals("text_avoid_buttons_break", AvoidButtonsCommand.OPTION_BREAK_KEY);
		assertEquals("avoidbuttons", new AvoidButtonsCommand().commandName);
	}

	@Test
	public void reusesOnOffWords() {
		assertEquals(Boolean.TRUE, DimRepeatCommand.parseOnOff("on"));
		assertEquals(Boolean.FALSE, DimRepeatCommand.parseOnOff("off"));
	}

	@Test
	public void parseBreakTokenMapsLettersAndWords() {
		assertEquals(Integer.valueOf(WindowToken.AVOID_BUTTONS_BREAK_LETTERS),
				AvoidButtonsCommand.parseBreakToken("letters"));
		assertEquals(Integer.valueOf(WindowToken.AVOID_BUTTONS_BREAK_WORDS),
				AvoidButtonsCommand.parseBreakToken("words"));
		assertNull(AvoidButtonsCommand.parseBreakToken("toggle"));
		assertNull(AvoidButtonsCommand.parseBreakToken("on"));
	}

	@Test
	public void defaultBreakIsLettersSoExistingProfilesStayPut() {
		assertEquals(WindowToken.AVOID_BUTTONS_BREAK_LETTERS,
				WindowToken.DEFAULT_AVOID_BUTTONS_BREAK);
		assertEquals("letters", AvoidButtonsCommand.breakLabel(
				WindowToken.DEFAULT_AVOID_BUTTONS_BREAK));
		assertEquals("words", AvoidButtonsCommand.breakLabel(
				WindowToken.AVOID_BUTTONS_BREAK_WORDS));
	}
}
