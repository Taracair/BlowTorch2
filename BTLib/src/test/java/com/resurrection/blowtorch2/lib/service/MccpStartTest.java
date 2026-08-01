package com.resurrection.blowtorch2.lib.service;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.Deflater;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.junit.Test;

/**
 * MCCP2 start-of-stream. Measured against achaea.com:23 on 2026-08-01: the server
 * puts a short {@code IAC WONT …} in the same packet as {@code IAC SB MCCP2 IAC SE}
 * (marker at offset 3), and everything after that packet is zlib.
 *
 * <p>The slice handed to the Inflater used to be allocated one buffer too long —
 * {@code input.length - (j + 2 - i)} for {@code input.length - (j + 2)} bytes written —
 * so it carried {@code i} trailing zeros. Those zeros read as the start of a stored
 * block, so the Inflater threw on the <em>next</em> packet and every packet after it
 * was printed as raw zlib for the rest of the session.
 */
public class MccpStartTest {

	private static final byte COMPRESS2 = (byte) 0x56;

	private static byte[] deflateWithSyncFlush(final String text) {
		Deflater d = new Deflater();
		byte[] out = syncFlush(d, text);
		d.end();
		return out;
	}

	/** One server "packet": deflate {@code text} and flush, keeping the stream open. */
	private static byte[] syncFlush(final Deflater d, final String text) {
		d.setInput(text.getBytes(StandardCharsets.ISO_8859_1));
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] tmp = new byte[256];
		// SYNC_FLUSH, not finish: MUD servers keep one stream open for the session.
		int n;
		while ((n = d.deflate(tmp, 0, tmp.length, Deflater.SYNC_FLUSH)) > 0) {
			out.write(tmp, 0, n);
			if (n < tmp.length) {
				break;
			}
		}
		return out.toByteArray();
	}

	/** banner + IAC WONT MSSP + IAC SB MCCP2 IAC SE + compressed payload, one packet. */
	private static byte[] achaeaShapedPacket(final byte[] compressed) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write("Enter an option or enter your character's name. ".getBytes(StandardCharsets.ISO_8859_1));
		out.write(new byte[] { TC.IAC, (byte) 252, TC.MSSP });
		out.write(new byte[] { TC.IAC, TC.SB, COMPRESS2, TC.IAC, TC.SE });
		out.write(compressed);
		return out.toByteArray();
	}

	/** Where the parser thinks the marker starts — found the way rawProcess does. */
	private static int markerStart(final byte[] packet) {
		for (int k = 0; k + 4 < packet.length; k++) {
			if (packet[k] == TC.IAC && packet[k + 1] == TC.SB && packet[k + 2] == COMPRESS2) {
				return k;
			}
		}
		throw new IllegalStateException("no MCCP2 marker in packet");
	}

	/** The production scan, not a re-implementation of it. */
	private static int markerEnd(final byte[] packet) {
		int j = Processor.findSubnegotiationEnd(packet, markerStart(packet));
		if (j < 0) {
			throw new IllegalStateException("subnegotiation not closed");
		}
		return j;
	}

	@Test
	public void remainderIsByteExact_evenWhenTextPrecedesTheMarker() throws Exception {
		byte[] compressed = deflateWithSyncFlush("You are in a forest.\r\n");
		byte[] packet = achaeaShapedPacket(compressed);

		byte[] remainder = Processor.remainderAfterSubnegotiation(packet, markerEnd(packet));

		assertArrayEquals("no padding, no truncation", compressed, remainder);
	}

	@Test
	public void inflaterSurvivesTheFirstChunk() throws Exception {
		String line = "You are in a forest.\r\n";
		byte[] packet = achaeaShapedPacket(deflateWithSyncFlush(line));

		byte[] remainder = Processor.remainderAfterSubnegotiation(packet, markerEnd(packet));

		Inflater inf = new Inflater(false);
		inf.setInput(remainder);
		byte[] tmp = new byte[1024];
		StringBuilder got = new StringBuilder();
		while (!inf.needsInput()) {
			int n = inf.inflate(tmp, 0, tmp.length);
			if (n <= 0) {
				break;
			}
			got.append(new String(tmp, 0, n, StandardCharsets.ISO_8859_1));
		}
		inf.end();

		assertEquals(line, got.toString());
	}

	@Test
	public void theOldPaddingWouldHaveBrokenThisStream() throws Exception {
		// Drives the real slice against the real scan, then feeds the Inflater the way
		// DataPumper does — first with what we now hand it, then with the old padded
		// shape (allocation `input.length - (j + 2 - i)` for `input.length - (j + 2)`
		// bytes written). The padding does not throw on its own: zeros after a
		// SYNC_FLUSH block look like a stored block whose LEN/NLEN has not arrived,
		// so it only blows up on the next packet — which is why the player saw a
		// clean banner and binary only once the server spoke again.
		Deflater d = new Deflater();
		byte[] first = syncFlush(d, "hello\r\n");
		byte[] second = syncFlush(d, "you are in a forest\r\n");
		d.end();

		byte[] packet = achaeaShapedPacket(first);
		int i = markerStart(packet);
		int j = markerEnd(packet);
		assertTrue("Achaea puts text and a telnet command before the marker", i > 0);

		byte[] fixed = Processor.remainderAfterSubnegotiation(packet, j);
		assertTrue("today's slice survives the next packet", feedsCleanly(fixed, second));

		byte[] oldStyle = new byte[packet.length - (j + 2 - i)]; // the old allocation
		System.arraycopy(packet, j + 2, oldStyle, 0, packet.length - (j + 2));
		assertFalse("the old slice must not survive it", feedsCleanly(oldStyle, second));
	}

	/** @return false when the Inflater throws on either chunk, as DataPumper would. */
	private static boolean feedsCleanly(final byte[] firstChunk, final byte[] secondChunk) {
		Inflater inf = new Inflater(false);
		byte[] tmp = new byte[1024];
		try {
			for (byte[] chunk : new byte[][] { firstChunk, secondChunk }) {
				inf.setInput(chunk);
				// Same loop shape as DataPumper.doDecompress.
				for (int guard = 0; guard < 100 && !inf.needsInput(); guard++) {
					inf.inflate(tmp, 0, tmp.length);
				}
			}
			return true;
		} catch (DataFormatException broken) {
			return false;
		} finally {
			inf.end();
		}
	}

	@Test
	public void escapedIacInsideThePayloadDoesNotEndTheSubnegotiation() {
		// A GMCP-shaped payload carrying a literal 0xFF, doubled per RFC 854. If the
		// scan stopped at the first IAC it saw, the slice would start mid-payload.
		byte[] packet = new byte[] {
				'h', 'i',
				TC.IAC, TC.SB, TC.GMCP, 'a', TC.IAC, TC.IAC, 'b', TC.IAC, TC.SE,
				'r', 'e', 's', 't'
		};

		int j = Processor.findSubnegotiationEnd(packet, 2);

		assertEquals(9, j);
		assertArrayEquals(new byte[] { 'r', 'e', 's', 't' },
				Processor.remainderAfterSubnegotiation(packet, j));
	}

	@Test
	public void unterminatedSubnegotiationIsReportedRatherThanGuessed() {
		byte[] split = new byte[] { TC.IAC, TC.SB, COMPRESS2, 0x01, 0x02 };

		assertEquals(-1, Processor.findSubnegotiationEnd(split, 0));
	}

	@Test
	public void emptyRemainderIsEmptyArrayNotNull() {
		byte[] packet = new byte[] { TC.IAC, TC.SB, COMPRESS2, TC.IAC, TC.SE };
		assertArrayEquals(new byte[0], Processor.remainderAfterSubnegotiation(packet, 3));
	}



	@Test
	public void weNeverOfferToCompressOurOwnOutput() {
		OptionNegotiator neg = new OptionNegotiator("BlowTorch");
		byte[] resp = neg.processCommand(TC.IAC, (byte) 253 /* DO */, COMPRESS2);
		assertEquals((byte) 252 /* WONT */, resp[1]);
	}
}
