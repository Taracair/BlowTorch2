package com.resurrection.blowtorch2.lib.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

/**
 * Default-off path must be identity; enabled path folds Latin diacritics only.
 */
public class CommandUnaccentTest {

	@Test
	public void foldForSendOffIsIdentity() {
		final String sit = "usiądź przy stole";
		assertSame(sit, CommandUnaccent.foldForSend(sit, false, true));
		assertEquals("say Hello", CommandUnaccent.foldForSend("say Hello", false, true));
	}

	@Test
	public void passwordEchoSkipsEvenWhenEnabled() {
		assertEquals("usiądź", CommandUnaccent.foldForSend("usiądź", true, false));
		assertEquals("Secret", CommandUnaccent.foldForSend("Secret", true, false));
	}

	@Test
	public void polishWorkedExample() {
		assertEquals("usiadz przy stole", CommandUnaccent.fold("usiądź przy stole"));
		assertEquals("zolc", CommandUnaccent.fold("żółć"));
	}

	@Test
	public void extraLettersThatDoNotNfdToAscii() {
		assertEquals("ss", CommandUnaccent.fold("ß"));
		assertEquals("ae", CommandUnaccent.fold("æ"));
		assertEquals("AE", CommandUnaccent.fold("Æ"));
		assertEquals("o", CommandUnaccent.fold("ø"));
		assertEquals("O", CommandUnaccent.fold("Ø"));
		assertEquals("d", CommandUnaccent.fold("ð"));
		assertEquals("D", CommandUnaccent.fold("Ð"));
	}

	@Test
	public void asciiUnchanged() {
		final String look = "Look";
		assertSame(look, CommandUnaccent.fold(look));
		final String hello = "say Hello";
		assertSame(hello, CommandUnaccent.fold(hello));
	}

	@Test
	public void cyrillicUnchanged() {
		final String word = "тест";
		assertSame(word, CommandUnaccent.fold(word));
	}

	@Test
	public void combiningAcuteOnEBecomesE() {
		assertEquals("e", CommandUnaccent.fold("e\u0301"));
	}

	@Test
	public void enabledViaFoldForSend() {
		assertEquals("usiadz przy stole",
				CommandUnaccent.foldForSend("usiądź przy stole", true, true));
		assertEquals("Look", CommandUnaccent.foldForSend("Look", true, true));
	}

	@Test
	public void localEchoLineFoldsTheSameAsTheWire() {
		assertEquals("usiadz\r\n",
				CommandUnaccent.foldForSend("usiądź\r\n", true, true));
	}

	@Test
	public void nullAndEmptyAreIdentity() {
		assertEquals(null, CommandUnaccent.fold(null));
		assertEquals("", CommandUnaccent.fold(""));
		assertEquals(null, CommandUnaccent.foldForSend(null, true, true));
	}
}
