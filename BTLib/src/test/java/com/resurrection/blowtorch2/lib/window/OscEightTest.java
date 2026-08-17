package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class OscEightTest {

	@Test
	public void parseOpenBelStyle() {
		OscEight.Result r = OscEight.parse("8;;https://example.com/path");
		assertNotNull(r);
		assertFalse(r.isClose());
		assertEquals("https://example.com/path", r.uri);
		assertNull(r.id);
	}

	@Test
	public void parseIdParam() {
		OscEight.Result r = OscEight.parse("8;id=foo:bar;https://example.org/");
		assertNotNull(r);
		assertEquals("https://example.org/", r.uri);
		assertEquals("foo", r.id);
	}

	@Test
	public void emptyUriIsClose() {
		OscEight.Result r = OscEight.parse("8;;");
		assertNotNull(r);
		assertTrue(r.isClose());
		assertNull(r.uri);
	}

	@Test
	public void darkwindCloserIsCloseNotHref() {
		OscEight.Result r = OscEight.parse("8;;)");
		assertNotNull(r);
		assertTrue(r.isClose());
	}

	@Test
	public void javascriptIsCloseNotHref() {
		OscEight.Result r = OscEight.parse("8;;javascript:alert(1)");
		assertNotNull(r);
		assertTrue(r.isClose());
	}

	@Test
	public void dataAndFileRejected() {
		assertTrue(OscEight.parse("8;;data:text/html,x").isClose());
		assertTrue(OscEight.parse("8;;file:///etc/passwd").isClose());
		assertTrue(OscEight.parse("8;;vbscript:msg").isClose());
	}

	@Test
	public void mailtoAllowed() {
		OscEight.Result r = OscEight.parse("8;;mailto:nobody@example.com");
		assertNotNull(r);
		assertEquals("mailto:nobody@example.com", r.uri);
	}

	@Test
	public void notOscEightReturnsNull() {
		assertNull(OscEight.parse("0;title"));
		assertNull(OscEight.parse("BTIMG;key;2"));
		assertNull(OscEight.parse("8"));
		assertNull(OscEight.parse(null));
	}

	@Test
	public void isSafeUriSchemes() {
		assertTrue(OscEight.isSafeUri("https://example.com"));
		assertTrue(OscEight.isSafeUri("http://example.com"));
		assertTrue(OscEight.isSafeUri("mailto:a@b.c"));
		assertTrue(OscEight.isSafeUri("ftp://files.example.com"));
		assertFalse(OscEight.isSafeUri("javascript:alert(1)"));
		assertFalse(OscEight.isSafeUri("example.com"));
		assertFalse(OscEight.isSafeUri("preset:danger"));
		assertTrue(OscEight.isSafeUri("mxp-send:look"));
		assertTrue(OscEight.isSafeUri("mxp-menu:a"));
		assertTrue(OscEight.isSafeUri("mxp-prompt:say"));
	}

	@Test
	public void sendAndPromptAreSafe() {
		assertTrue(OscEight.isSafeUri("send:look"));
		assertTrue(OscEight.isSafeUri("prompt:cast fireball"));
		assertTrue(OscEight.isSend("send:look"));
		assertTrue(OscEight.isPrompt("PROMPT:say hi"));
		assertTrue(OscEight.tapWordOverrides("send:look"));
		assertFalse(OscEight.tapWordOverrides("https://example.com"));
	}

	@Test
	public void parseSendIsOpenNotClose() {
		OscEight.Result r = OscEight.parse("8;;send:look");
		assertNotNull(r);
		assertFalse("StickMUD send: must stamp, not close", r.isClose());
		assertEquals("send:look", r.uri);
	}

	@Test
	public void sendCommandStripsQueryAndDecodes() {
		assertEquals("look", OscEight.sendCommand("send:look"));
		assertEquals("look north", OscEight.sendCommand("send:look north"));
		assertEquals("cast fireball", OscEight.sendCommand("send:cast%20fireball"));
		assertEquals("attack", OscEight.sendCommand(
				"send:attack?config=%7B%22style%22%3A%7B%22color%22%3A%22red%22%7D%7D"));
		assertNull(OscEight.sendCommand("https://example.com"));
	}

	@Test
	public void promptCommandDecodesSpaces() {
		assertEquals("cast fireball", OscEight.promptCommand("prompt:cast%20fireball"));
		assertEquals("say hi", OscEight.promptCommand("prompt:say hi?preset=btn"));
	}

	@Test
	public void jsonParamsDoNotStealTheUri() {
		OscEight.Result r = OscEight.parse(
				"8;{\"style\":{\"color\":\"red\"}};send:look");
		assertNotNull(r);
		assertFalse(r.isClose());
		assertEquals("send:look", r.uri);
	}

	@Test
	public void jsonParamsWithEmbeddedSemicolonStillSplit() {
		OscEight.Result r = OscEight.parse(
				"8;{\"tooltip\":\"hit; run\"};send:flee");
		assertNotNull(r);
		assertEquals("send:flee", r.uri);
	}
}
