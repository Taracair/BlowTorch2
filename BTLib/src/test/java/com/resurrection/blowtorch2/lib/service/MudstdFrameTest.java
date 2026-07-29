package com.resurrection.blowtorch2.lib.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The mudstd.frame vocabulary and the exact bytes we put on the wire.
 *
 * <p>These are pinned character for character on purpose. The other end is
 * someone else's parser, being written right now against what we send, so a
 * change here is a change to an agreement rather than a detail.
 */
public class MudstdFrameTest {

	/** Every value the package page defines, and nothing else. */
	@Test
	public void knowsTheSpecifiedTypes() {
		assertTrue(MudstdFrame.isKnownType("external"));
		assertTrue(MudstdFrame.isKnownType("docked"));
		assertTrue(MudstdFrame.isKnownType("floating"));
		assertTrue(MudstdFrame.isKnownType("child"));
		assertTrue(MudstdFrame.isKnownType("tab"));
		assertFalse(MudstdFrame.isKnownType("popup"));
		assertFalse(MudstdFrame.isKnownType(null));
	}

	@Test
	public void knowsTheSpecifiedContentTypes() {
		assertTrue(MudstdFrame.isKnownContent("terminal"));
		assertTrue(MudstdFrame.isKnownContent("webview"));
		assertTrue(MudstdFrame.isKnownContent("image"));
		assertFalse(MudstdFrame.isKnownContent("video"));
	}

	/** Case and stray spaces come off the wire; they must not decide anything. */
	@Test
	public void vocabularyIsCaseAndSpaceInsensitive() {
		assertTrue(MudstdFrame.isKnownType("  FLOATING "));
		assertTrue(MudstdFrame.canHost("Floating", "TERMINAL"));
	}

	/** What we announce is exactly what we can host — no more. */
	@Test
	public void announcementMatchesWhatWeAccept() {
		assertEquals("mudstd.frame.support {\"type\": [\"floating\"], "
				+ "\"content\": [\"terminal\"]}", MudstdFrame.supportMessage());
		assertTrue(MudstdFrame.canHost("floating", "terminal"));
	}

	@Test
	public void hostsFloatingTerminalOnly() {
		assertTrue(MudstdFrame.canHost("floating", "terminal"));
		assertFalse(MudstdFrame.canHost("docked", "terminal"));
		assertFalse(MudstdFrame.canHost("floating", "image"));
	}

	/** A refusal has to say which half was the problem. */
	@Test
	public void refusalNamesTheReason() {
		assertNull(MudstdFrame.refusalFor("floating", "terminal"));
		assertTrue(MudstdFrame.refusalFor("docked", "terminal").contains("floating"));
		assertTrue(MudstdFrame.refusalFor("floating", "image").contains("terminal"));
		assertTrue(MudstdFrame.refusalFor("popup", "terminal").contains("unknown frame type"));
		assertTrue(MudstdFrame.refusalFor("floating", "video").contains("unknown content type"));
	}

	@Test
	public void openedEventHasBothSizes() {
		assertEquals("mudstd.frame.opened {\"id\": \"stats\", "
				+ "\"sizeChar\": {\"width\": 40, \"height\": 12}, "
				+ "\"sizePixel\": {\"width\": 480, \"height\": 240}}",
				MudstdFrame.openedEvent("stats", 40, 12, 480, 240));
	}

	@Test
	public void resizedEventMatchesOpenedShape() {
		assertEquals("mudstd.frame.resized {\"id\": \"stats\", "
				+ "\"sizeChar\": {\"width\": 1, \"height\": 2}, "
				+ "\"sizePixel\": {\"width\": 3, \"height\": 4}}",
				MudstdFrame.resizedEvent("stats", 1, 2, 3, 4));
	}

	@Test
	public void closedEventCarriesTheReason() {
		assertEquals("mudstd.frame.closed {\"id\": \"map\", \"reason\": \"system\"}",
				MudstdFrame.closedEvent("map", "system"));
		assertEquals("mudstd.frame.closed {\"id\": \"map\", \"reason\": \"user\"}",
				MudstdFrame.closedEvent("map", "user"));
	}

	/** Anything that is not "user" is the client's own doing. */
	@Test
	public void unknownReasonBecomesSystem() {
		assertTrue(MudstdFrame.closedEvent("map", "banana").contains("\"system\""));
		assertTrue(MudstdFrame.closedEvent("map", null).contains("\"system\""));
	}

	/**
	 * Frame ids come from the server. An id holding a quote would otherwise
	 * produce malformed JSON and leave the server author debugging their parser
	 * over our bug.
	 */
	@Test
	public void frameIdsAreEscaped() {
		assertEquals("a\\\"b", MudstdFrame.escape("a\"b"));
		assertEquals("a\\\\b", MudstdFrame.escape("a\\b"));
		assertEquals("a\\nb", MudstdFrame.escape("a\nb"));
		assertEquals("", MudstdFrame.escape(null));
		assertTrue(MudstdFrame.closedEvent("we\"ird", "user")
				.startsWith("mudstd.frame.closed {\"id\": \"we\\\"ird\""));
	}

	/** Control characters have to become escapes, not raw bytes. */
	@Test
	public void controlCharactersAreEscaped() {
		assertEquals("\\u0001", MudstdFrame.escape(""));
	}
}
