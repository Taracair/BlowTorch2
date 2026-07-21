package com.resurrection.blowtorch2.lib.service.function;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SearchCommandTest {

	@Test
	public void stripQuotesRemovesMatchingQuotes() {
		assertEquals("hello world", SearchCommand.stripQuotes("'hello world'"));
		assertEquals("hello world", SearchCommand.stripQuotes("\"hello world\""));
		assertEquals("plain", SearchCommand.stripQuotes("plain"));
		assertEquals("", SearchCommand.stripQuotes(null));
	}

	@Test
	public void argumentFromSlashCommandStripsPrefixAndQuotes() {
		assertEquals("dragon", SearchCommand.argumentFromSlashCommand("/search dragon"));
		assertEquals("red dragon", SearchCommand.argumentFromSlashCommand("/search 'red dragon'"));
		assertEquals("foo", SearchCommand.argumentFromSlashCommand("/SEARCH \"foo\""));
		assertEquals("", SearchCommand.argumentFromSlashCommand(null));
	}
}
