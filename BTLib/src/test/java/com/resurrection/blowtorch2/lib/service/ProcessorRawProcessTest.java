package com.resurrection.blowtorch2.lib.service;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/**
 * IAC NOP is a 2-byte command. Copying the command byte into the text buffer
 * makes UTF-8 decode it as U+FFFD, so the next line is prefixed with �.
 */
public class ProcessorRawProcessTest {

	private static byte[] ascii(final String s) {
		return s.getBytes(StandardCharsets.US_ASCII);
	}

	private static byte[] cat(final byte[]... parts) {
		int n = 0;
		for (byte[] part : parts) {
			if (part != null) {
				n += part.length;
			}
		}
		byte[] out = new byte[n];
		int at = 0;
		for (byte[] part : parts) {
			if (part == null) {
				continue;
			}
			System.arraycopy(part, 0, out, at, part.length);
			at += part.length;
		}
		return out;
	}

	private static boolean contains(final byte[] data, final byte b) {
		if (data == null) {
			return false;
		}
		for (byte v : data) {
			if (v == b) {
				return true;
			}
		}
		return false;
	}

	private final List<Integer> messages = new ArrayList<Integer>();

	private Processor processor() {
		return new Processor("UTF-8", messages);
	}

	@Test
	public void nopBeforeGhostlyIsNotCopiedIntoTheText() {
		byte[] out = processor().rawProcess(cat(
				new byte[] { TC.IAC, TC.NOP },
				ascii("Ghostly\n")));

		assertArrayEquals(ascii("Ghostly\n"), out);
		assertTrue(messages.isEmpty());
	}

	@Test
	public void twoNopsInOneChunkDisappear() {
		byte[] out = processor().rawProcess(cat(
				new byte[] { TC.IAC, TC.NOP, TC.IAC, TC.NOP },
				ascii("Ghostly\n")));

		assertArrayEquals(ascii("Ghostly\n"), out);
		assertFalse(contains(out, TC.NOP));
	}

	@Test
	public void nopSplitAfterTheLeadingIacDisappears() {
		Processor p = processor();

		assertNull(p.rawProcess(new byte[] { TC.IAC }));
		byte[] out = p.rawProcess(cat(new byte[] { TC.NOP }, ascii("Ghostly\n")));

		assertArrayEquals(ascii("Ghostly\n"), out);
	}

	@Test
	public void twoNopsSplitAcrossCallsDisappear() {
		Processor p = processor();

		byte[] first = p.rawProcess(cat(
				ascii("Ghostly"),
				new byte[] { TC.IAC, TC.NOP, TC.IAC }));
		byte[] second = p.rawProcess(cat(new byte[] { TC.NOP }, ascii("\n")));

		assertArrayEquals(ascii("Ghostly"), first);
		assertArrayEquals(ascii("\n"), second);
		assertFalse(contains(first, TC.NOP));
		assertFalse(contains(second, TC.NOP));
	}

	@Test
	public void dataMarkAndOtherTwoByteCommandsAreNotText() {
		Processor p = processor();

		assertArrayEquals(ascii("Ghostly\n"), p.rawProcess(cat(
				new byte[] { TC.IAC, TC.DM },
				ascii("Ghostly\n"))));
		assertArrayEquals(ascii("Ghostly\n"), p.rawProcess(cat(
				new byte[] { TC.IAC, TC.SE },
				ascii("Ghostly\n"))));
		assertArrayEquals(ascii("Ghostly\n"), p.rawProcess(cat(
				new byte[] { TC.IAC, 0x01 },
				ascii("Ghostly\n"))));
	}

	@Test
	public void doubledIacIsOneLiteralFf() {
		byte[] out = processor().rawProcess(cat(
				new byte[] { TC.IAC, TC.IAC },
				ascii("Ghostly\n")));

		assertArrayEquals(cat(new byte[] { TC.IAC }, ascii("Ghostly\n")), out);
	}

	@Test
	public void doubledIacSplitAcrossCallsIsOneLiteralFf() {
		Processor p = processor();

		assertNull(p.rawProcess(new byte[] { TC.IAC }));
		byte[] out = p.rawProcess(cat(new byte[] { TC.IAC }, ascii("Ghostly\n")));

		assertArrayEquals(cat(new byte[] { TC.IAC }, ascii("Ghostly\n")), out);
	}

	@Test
	public void goAheadFlushesTheLineHoldoverAndIsNotText() {
		byte[] out = processor().rawProcess(cat(
				ascii("Ghostly"),
				new byte[] { TC.IAC, TC.GOAHEAD }));

		assertArrayEquals(ascii("Ghostly"), out);
		assertEquals(Arrays.asList(Connection.MESSAGE_FLUSH_LINE_HOLDOVER), messages);
	}

	@Test
	public void eorFlushesTheLineHoldoverAndIsNotText() {
		byte[] out = processor().rawProcess(cat(
				ascii("Ghostly"),
				new byte[] { TC.IAC, TC.EOR }));

		assertArrayEquals(ascii("Ghostly"), out);
		assertEquals(Arrays.asList(Connection.MESSAGE_FLUSH_LINE_HOLDOVER), messages);
	}
}
