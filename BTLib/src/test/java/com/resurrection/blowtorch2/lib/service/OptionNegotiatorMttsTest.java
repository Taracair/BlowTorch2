package com.resurrection.blowtorch2.lib.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

/** MTTS / TTYPE cycle — https://mudstandards.org/mud/mtts and GitHub issue #2. */
public class OptionNegotiatorMttsTest {

	private static final byte[] TTYPE_SEND = new byte[] {
			TC.IAC, TC.SB, TC.TERM, TC.SEND, TC.IAC, TC.SE
	};

	private static String extractIsPayload(final byte[] response) {
		assertTrue(response != null && response.length > 6);
		assertEquals(TC.IAC, response[0]);
		assertEquals(TC.SB, response[1]);
		assertEquals(TC.TERM, response[2]);
		assertEquals(0, response[3]); // IS
		assertEquals(TC.IAC, response[response.length - 2]);
		assertEquals(TC.SE, response[response.length - 1]);
		return new String(response, 4, response.length - 6, StandardCharsets.ISO_8859_1);
	}

	@Test
	public void mttsOn_returnsNameAnsiMtts13_thenRepeats() {
		OptionNegotiator neg = new OptionNegotiator("BlowTorch");
		neg.setUseMTTS(true);

		assertEquals("BlowTorch", extractIsPayload(neg.getSubnegotiationResponse(TTYPE_SEND)));
		assertEquals("ANSI", extractIsPayload(neg.getSubnegotiationResponse(TTYPE_SEND)));
		assertEquals("MTTS 13", extractIsPayload(neg.getSubnegotiationResponse(TTYPE_SEND)));
		assertEquals("MTTS 13", extractIsPayload(neg.getSubnegotiationResponse(TTYPE_SEND)));
		assertEquals("MTTS 13", extractIsPayload(neg.getSubnegotiationResponse(TTYPE_SEND)));
	}

	@Test
	public void mttsOff_stillStandardsCompliant_ansiOnlyBits() {
		OptionNegotiator neg = new OptionNegotiator("BlowTorch");
		neg.setUseMTTS(false);

		assertEquals("BlowTorch", extractIsPayload(neg.getSubnegotiationResponse(TTYPE_SEND)));
		assertEquals("ANSI", extractIsPayload(neg.getSubnegotiationResponse(TTYPE_SEND)));
		assertEquals("MTTS 1", extractIsPayload(neg.getSubnegotiationResponse(TTYPE_SEND)));
		assertEquals("MTTS 1", extractIsPayload(neg.getSubnegotiationResponse(TTYPE_SEND)));
	}

	@Test
	public void neverReturnsLegacyBlowTorch256color() {
		OptionNegotiator neg = new OptionNegotiator("BlowTorch");
		neg.setUseMTTS(false);
		for (int i = 0; i < 5; i++) {
			String payload = extractIsPayload(neg.getSubnegotiationResponse(TTYPE_SEND));
			assertTrue("legacy reply leaked: " + payload,
					!payload.equalsIgnoreCase("BlowTorch-256color")
							&& !payload.equals("ansi"));
		}
	}

	@Test
	public void mttsBitsMatchStandardAnsiUtf8256() {
		// ANSI=1, UTF-8=4, 256 COLORS=8 → full announcement used when Use MTTS? is on
		assertEquals(13, 1 | 4 | 8);
	}
}
