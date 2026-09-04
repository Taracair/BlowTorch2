package com.resurrection.blowtorch2.lib.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.UnsupportedEncodingException;

import org.junit.Test;

/**
 * Bare CR used to be dropped, so a five-tile flying map sent as CR-separated
 * rows became one 30-character line. CRLF must stay a single newline, including
 * when the pair is split across packets.
 */
public class CrToNewlineTest {

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
	public void crlfIsOneNewline() {
		assertEquals("one\ntwo\n", s(new CrToNewline().apply(b("one\r\ntwo\r\n"))));
	}

	@Test
	public void lfUnchanged() {
		assertEquals("one\ntwo\n", s(new CrToNewline().apply(b("one\ntwo\n"))));
	}

	@Test
	public void bareCrBetweenMapRowsBecomesNewline() {
		// Five 2-char tiles = 10 cells; three rows joined by CR used to be 30.
		String glued = "oOoOoOoOoO\roOoOoOoOoO\roOoOoOoOoO\n";
		assertEquals("oOoOoOoOoO\noOoOoOoOoO\noOoOoOoOoO\n",
				s(new CrToNewline().apply(b(glued))));
	}

	@Test
	public void crlfSplitAcrossPacketsIsStillOneNewline() {
		CrToNewline cr = new CrToNewline();
		assertEquals("hello", s(cr.apply(b("hello\r"))));
		assertTrue(cr.hasPendingCr());
		assertEquals("\nworld\n", s(cr.apply(b("\nworld\n"))));
		assertFalse(cr.hasPendingCr());
	}

	@Test
	public void trailingCrThenMoreTextIsANewline() {
		CrToNewline cr = new CrToNewline();
		assertEquals("row1", s(cr.apply(b("row1\r"))));
		assertEquals("\nrow2\n", s(cr.apply(b("row2\n"))));
	}

	@Test
	public void emptyKeepsAPendingCrHeld() {
		CrToNewline cr = new CrToNewline();
		cr.apply(b("x\r"));
		assertEquals(0, cr.apply(new byte[0]).length);
		assertTrue(cr.hasPendingCr());
	}

	@Test
	public void holdoverThenSeesSeparateMapRows() {
		CrToNewline cr = new CrToNewline();
		IncomingLineHoldover h = new IncomingLineHoldover();
		byte[] ready = h.accept(cr.apply(b("oOoOoOoOoO\roOoOoOoOoO\roOoOoOoOoO\n")));
		assertEquals("oOoOoOoOoO\noOoOoOoOoO\noOoOoOoOoO\n", s(ready));
		assertFalse(h.hasHeld());
	}
}
