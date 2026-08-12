package com.resurrection.blowtorch2.lib.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

/**
 * Default-off path must be identity; enabled path only lowers the first letter.
 */
public class CommandCaseTest {

	@Test
	public void softenForSendOffIsIdentity() {
		final String look = "Look";
		assertSame(look, CommandCase.softenForSend(look, false, true));
		assertEquals("say Hello", CommandCase.softenForSend("say Hello", false, true));
	}

	@Test
	public void passwordEchoSkipsEvenWhenEnabled() {
		assertEquals("Secret", CommandCase.softenForSend("Secret", true, false));
	}

	@Test
	public void enabledLowersOnlyFirstLetter() {
		assertEquals("look", CommandCase.softenFirstLetter("Look"));
		assertEquals("say Hello", CommandCase.softenFirstLetter("say Hello"));
		assertEquals("look north", CommandCase.softenFirstLetter("Look north"));
		assertEquals(".Echo", CommandCase.softenFirstLetter(".Echo"));
		assertEquals("\"Hello\"", CommandCase.softenFirstLetter("\"Hello\""));
		assertEquals("", CommandCase.softenFirstLetter(""));
		assertEquals(null, CommandCase.softenFirstLetter(null));
		assertEquals("5 north", CommandCase.softenFirstLetter("5 north"));
	}

	@Test
	public void enabledViaSoftenForSend() {
		assertEquals("look", CommandCase.softenForSend("Look", true, true));
		assertEquals("north", CommandCase.softenForSend("North", true, true));
	}
}
