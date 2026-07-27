package com.resurrection.blowtorch2.lib.service;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.UnsupportedEncodingException;

import org.junit.Test;

/**
 * The replay handed to a reopened extra text window travels over a binder transaction
 * with a hard size limit, and going over it throws a RemoteException that would leave
 * the window blank — exactly the bug the replay is there to fix. So the trim has to
 * hold the budget for every shape of input, including the awkward ones.
 */
public class ExtraTextReplayTrimTest {

	private static byte[] bytes(final String s) {
		try {
			return s.getBytes("UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	public void shortHistoryIsHandedOverWhole() {
		byte[] in = bytes("one\ntwo\nthree\n");
		assertArrayEquals(in, Connection.trimToNewestLines(in, 1024));
	}

	@Test
	public void nullSurvives() {
		assertNull(Connection.trimToNewestLines(null, 1024));
	}

	@Test
	public void longHistoryKeepsTheNewestEndNotTheOldest() {
		byte[] in = bytes("oldest line\nmiddle line\nnewest line\n");
		byte[] out = Connection.trimToNewestLines(in, 20);
		String s = new String(out);
		assertTrue("the tail is the part worth keeping: " + s, s.contains("newest line"));
		assertTrue("the oldest text should have been dropped: " + s, !s.contains("oldest"));
	}

	@Test
	public void trimStartsOnALineBoundary() {
		// 33 bytes of three 10-character lines. A 25 byte budget cuts inside the "a"
		// line, so aligning forward keeps "b" and "c" whole — 22 bytes, under budget.
		byte[] in = bytes("aaaaaaaaaa\nbbbbbbbbbb\ncccccccccc\n");
		byte[] out = Connection.trimToNewestLines(in, 25);
		assertEquals("should not open on half a line", 'b', (char) out[0]);
		assertTrue(out.length <= 25);
		assertTrue("only whole lines survive", !new String(out).contains("a"));
	}

	@Test
	public void aWindowWithNoNewlineStillHoldsTheBudget() {
		StringBuilder sb = new StringBuilder("line one\n");
		for (int i = 0; i < 200; i++) {
			sb.append('x');
		}
		byte[] out = Connection.trimToNewestLines(bytes(sb.toString()), 50);
		assertEquals("no newline to align to, so the budget is the only rule", 50, out.length);
	}

	@Test
	public void aTrailingNewlineDoesNotBlankTheWindow() {
		// Aligning here would consume the whole window and hand back nothing, which
		// looks exactly like the blank panel this replay exists to prevent.
		byte[] in = bytes("older text that gets dropped\nkept\n");
		byte[] out = Connection.trimToNewestLines(in, 5);
		assertTrue("replay must not come back empty", out.length > 0);
		assertTrue(out.length <= 5);
	}
}
