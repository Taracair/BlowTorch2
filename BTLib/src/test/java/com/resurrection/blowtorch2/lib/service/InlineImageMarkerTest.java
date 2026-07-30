package com.resurrection.blowtorch2.lib.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The marker that puts a server's picture into the game text.
 *
 * <p>What is worth pinning here is the arithmetic and the refusals, because
 * both are invisible on screen: a marker that reserves the wrong number of
 * lines leaves a gap or overlaps the text under it, and a marker that parses
 * when it should not would let a server's own OSC traffic move pictures around.
 */
public class InlineImageMarkerTest {

	private static final char ESC = 0x1B;
	private static final char BEL = 0x07;

	/** The reserved block is exactly as many lines as were asked for. */
	@Test
	public void encodeReservesExactlyTheLinesAsked() {
		String s = InlineImageMarker.encode("btimg-1-map", 5);
		int newlines = 0;
		for (int i = 0; i < s.length(); i++) {
			if (s.charAt(i) == '\n') {
				newlines++;
			}
		}
		// One before the marker to get it onto a line of its own, then one per
		// line of the block: the marker's own line plus four blank ones.
		assertEquals(6, newlines);
		assertTrue(s.indexOf(ESC + "]" + InlineImageMarker.PREFIX) >= 0);
		assertTrue(s.indexOf(BEL) > 0);
	}

	/** What was encoded is what comes back. */
	@Test
	public void roundTrip() {
		String s = InlineImageMarker.encode("btimg-7-map", 9);
		int start = s.indexOf(']') + 1;
		int end = s.indexOf(BEL);
		InlineImageMarker.Parsed p = InlineImageMarker.parse(s.substring(start, end));
		assertNotNull(p);
		assertEquals("btimg-7-map", p.key);
		assertEquals(9, p.lines);
	}

	/** A height out of range is pulled into it rather than refused. */
	@Test
	public void heightIsClamped() {
		assertEquals(InlineImageMarker.MIN_LINES, InlineImageMarker.clampLines(0));
		assertEquals(InlineImageMarker.MIN_LINES, InlineImageMarker.clampLines(-4));
		assertEquals(InlineImageMarker.MAX_LINES, InlineImageMarker.clampLines(9999));
		assertEquals(12, InlineImageMarker.clampLines(12));
	}

	/** Anything that is not one of ours is left for the OSC skipper. */
	@Test
	public void otherOscSequencesAreNotOurs() {
		assertNull(InlineImageMarker.parse("0;some window title"));
		assertNull(InlineImageMarker.parse(""));
		assertNull(InlineImageMarker.parse(null));
		assertNull(InlineImageMarker.parse("8;;http://example.org/"));
	}

	/** A malformed marker draws nothing rather than guessing. */
	@Test
	public void malformedMarkersAreRefused() {
		assertNull(InlineImageMarker.parse("BTIMG;"));
		assertNull(InlineImageMarker.parse("BTIMG;key"));
		assertNull(InlineImageMarker.parse("BTIMG;key;notanumber"));
		assertNull(InlineImageMarker.parse("BTIMG;;8"));
		assertNull(InlineImageMarker.parse("BTIMG;key;1"));
	}

	/**
	 * The key is ours, not the server's.
	 *
	 * <p>Frame ids are chosen by the server and one already tested here contains
	 * a double quote. A server id dropped into a semicolon-delimited marker is a
	 * parser bug waiting to happen, so everything but letters and digits is
	 * dropped and the counter carries the uniqueness.
	 */
	@Test
	public void keysAreSafeAndUnique() {
		String a = InlineImageMarker.keyFor("ma\"p;x", 1);
		String b = InlineImageMarker.keyFor("ma\"p;x", 2);
		assertTrue(a.indexOf(';') < 0);
		assertTrue(a.indexOf('"') < 0);
		assertTrue(!a.equals(b));
		assertNotNull(InlineImageMarker.parse(
				InlineImageMarker.PREFIX + a + ";4"));
	}

	/** A frame id of nothing at all still produces a usable key. */
	@Test
	public void anEmptyFrameIdStillGivesAKey() {
		String k = InlineImageMarker.keyFor("", 3);
		assertTrue(k.length() > 0);
		assertNotNull(InlineImageMarker.parse(InlineImageMarker.PREFIX + k + ";6"));
	}
}
