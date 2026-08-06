package com.resurrection.blowtorch2.lib.service;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.UnsupportedEncodingException;

import org.junit.Test;

/**
 * The half-line buffer, pinned before it goes anywhere near the incoming path.
 *
 * <p>The case that matters is the reported one: a `[chatnet]` line arriving in
 * two packets used to be gagged in half, leaving its tail on screen.
 */
public class IncomingLineHoldoverTest {

	private static byte[] b(final String s) {
		try {
			return s.getBytes("ISO-8859-1");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}

	private static String s(final byte[] bytes) {
		try {
			return new String(bytes, "ISO-8859-1");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	public void aChunkOfWholeLinesPassesStraightThrough() {
		IncomingLineHoldover h = new IncomingLineHoldover();
		assertEquals("one\ntwo\n", s(h.accept(b("one\ntwo\n"))));
		assertFalse(h.hasHeld());
	}

	@Test
	public void theReportedSplit() {
		// Packet 1 ends mid-line; packet 2 brings the rest.
		IncomingLineHoldover h = new IncomingLineHoldover();
		String first = "1:39 pm [chatnet] Tonkatsu sa";
		assertEquals("", s(h.accept(b(first))));
		assertTrue("the fragment must be held, not emitted", h.hasHeld());

		String second = "ys, \"sleeby\"\n";
		assertEquals("1:39 pm [chatnet] Tonkatsu says, \"sleeby\"\n",
				s(h.accept(b(second))));
		assertFalse(h.hasHeld());
	}

	@Test
	public void whatPrecedesTheFragmentIsNotDelayed() {
		IncomingLineHoldover h = new IncomingLineHoldover();
		assertEquals("done\n", s(h.accept(b("done\nhalf"))));
		assertTrue(h.hasHeld());
	}

	@Test
	public void aFragmentSpanningThreePacketsStillJoins() {
		IncomingLineHoldover h = new IncomingLineHoldover();
		assertEquals("", s(h.accept(b("a"))));
		assertEquals("", s(h.accept(b("b"))));
		assertEquals("abc\n", s(h.accept(b("c\n"))));
		assertFalse(h.hasHeld());
	}

	@Test
	public void theHeldFragmentKeepsItsPlaceAheadOfLaterLines() {
		IncomingLineHoldover h = new IncomingLineHoldover();
		h.accept(b("tail"));
		assertEquals("tail joined\nnext line\n",
				s(h.accept(b(" joined\nnext line\n"))));
	}

	@Test
	public void aPromptIsHeldAndThenFlushed() {
		// Nothing completes a prompt, which is why the caller has a timer.
		IncomingLineHoldover h = new IncomingLineHoldover();
		assertEquals("", s(h.accept(b("[HP 450/500] > "))));
		assertTrue(h.hasHeld());
		assertEquals("[HP 450/500] > ", s(h.flush()));
		assertFalse(h.hasHeld());
	}

	@Test
	public void flushingTwiceGivesNothingTheSecondTime() {
		IncomingLineHoldover h = new IncomingLineHoldover();
		h.accept(b("prompt> "));
		h.flush();
		assertEquals(0, h.flush().length);
	}

	@Test
	public void aFlushedPromptIsNotRepeatedWhenMoreArrives() {
		IncomingLineHoldover h = new IncomingLineHoldover();
		h.accept(b("prompt> "));
		assertEquals("prompt> ", s(h.flush()));
		assertEquals("later\n", s(h.accept(b("later\n"))));
	}

	@Test
	public void trailingNewlineHoldsNothing() {
		IncomingLineHoldover h = new IncomingLineHoldover();
		h.accept(b("line\n"));
		assertFalse(h.hasHeld());
	}

	@Test
	public void aBareNewlineIsAWholeLine() {
		IncomingLineHoldover h = new IncomingLineHoldover();
		assertEquals("\n", s(h.accept(b("\n"))));
		assertFalse(h.hasHeld());
	}

	@Test
	public void carriageReturnsRideAlongUntouched() {
		// The wire uses CRLF; splitting on LF keeps the CR with its own line.
		IncomingLineHoldover h = new IncomingLineHoldover();
		assertEquals("one\r\n", s(h.accept(b("one\r\ntwo\r"))));
		assertEquals("two\r\n", s(h.accept(b("\n"))));
	}

	@Test
	public void anEscapeSequenceSplitAcrossPacketsIsRejoinedNotCut() {
		IncomingLineHoldover h = new IncomingLineHoldover();
		assertEquals("", s(h.accept(b("text [0"))));
		assertEquals("text [0;36mmore\n", s(h.accept(b(";36mmore\n"))));
	}

	@Test
	public void anAbsurdlyLongLineIsReleasedRatherThanBufferedForever() {
		IncomingLineHoldover h = new IncomingLineHoldover();
		StringBuilder big = new StringBuilder();
		while (big.length() < IncomingLineHoldover.MAX_HELD_BYTES + 10) {
			big.append('x');
		}
		byte[] out = h.accept(b(big.toString()));
		assertTrue("past the cap it must come out", out.length > 0);
		assertFalse(h.hasHeld());
	}

	@Test
	public void nullAndEmptyChunksAreHarmless() {
		IncomingLineHoldover h = new IncomingLineHoldover();
		assertEquals(0, h.accept(null).length);
		assertEquals(0, h.accept(new byte[0]).length);
		h.accept(b("half"));
		// An empty chunk must not lose what is held.
		assertEquals(0, h.accept(new byte[0]).length);
		assertTrue(h.hasHeld());
		assertEquals("half", s(h.flush()));
	}

	@Test
	public void clearDropsTheFragmentWithoutEmittingIt() {
		IncomingLineHoldover h = new IncomingLineHoldover();
		h.accept(b("half"));
		h.clear();
		assertFalse(h.hasHeld());
		assertEquals(0, h.flush().length);
	}

	@Test
	public void theIncomingArrayIsNeverModified() {
		IncomingLineHoldover h = new IncomingLineHoldover();
		byte[] in = b("one\ntwo");
		byte[] copy = in.clone();
		h.accept(in);
		assertArrayEquals("the caller's buffer must come back untouched", copy, in);
	}
}
