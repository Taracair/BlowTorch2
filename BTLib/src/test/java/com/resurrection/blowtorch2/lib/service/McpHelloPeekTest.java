package com.resurrection.blowtorch2.lib.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class McpHelloPeekTest {

	@Test
	public void helloWithSpaceMatches() {
		byte[] line = "#$#mcp version: 2.1 to: 2.1\n".getBytes();
		assertTrue(McpEngine.looksLikeHello(line));
	}

	@Test
	public void negotiateCanDoesNotMatch() {
		byte[] line = "#$#mcp-negotiate-can dns-org-hellmoo-status 1.0\n".getBytes();
		assertFalse(McpEngine.looksLikeHello(line));
	}

	@Test
	public void shortOrNullDoesNotMatch() {
		assertFalse(McpEngine.looksLikeHello(null));
		assertFalse(McpEngine.looksLikeHello("#$#mc".getBytes()));
	}
}
