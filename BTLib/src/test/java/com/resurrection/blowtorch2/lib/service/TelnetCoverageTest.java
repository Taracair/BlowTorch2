package com.resurrection.blowtorch2.lib.service;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.Test;

/**
 * NAWS, CHARSET, MSDP and MSSP had no JVM test — they were only ever exercised
 * by hand against eden / samsaramoo. These pin the wire format so a refactor
 * cannot quietly change what we put on the socket.
 */
public class TelnetCoverageTest {

	private static byte[] sb(final byte option, final byte... payload) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(TC.IAC);
		out.write(TC.SB);
		out.write(option);
		out.write(payload, 0, payload.length);
		out.write(TC.IAC);
		out.write(TC.SE);
		return out.toByteArray();
	}

	private static byte[] latin1(final String s) {
		return s.getBytes(StandardCharsets.ISO_8859_1);
	}

	// ---- NAWS (RFC 1073) ----------------------------------------------------

	/** NAWS only produces bytes once the server has asked for it with DO NAWS. */
	private static OptionNegotiator nawsNegotiated() {
		OptionNegotiator neg = new OptionNegotiator("BlowTorch");
		neg.processCommand(TC.IAC, TC.DO, TC.NAWS);
		return neg;
	}

	@Test
	public void naws_sendsWidthThenHeight_highByteFirst() {
		OptionNegotiator neg = nawsNegotiated();
		neg.setColumns(80);
		neg.setRows(24);

		assertArrayEquals(
				new byte[] { TC.IAC, TC.SB, TC.NAWS, 0, 80, 0, 24, TC.IAC, TC.SE },
				neg.getNawsString());
	}

	@Test
	public void naws_isNotResentUntilTheSizeChanges() {
		OptionNegotiator neg = nawsNegotiated();
		neg.setColumns(80);
		neg.setRows(24);

		assertTrue(neg.getNawsString() != null);
		assertNull("same size must not be announced twice", neg.getNawsString());

		neg.setColumns(100);
		assertTrue("a new size must be announced", neg.getNawsString() != null);
	}

	@Test
	public void naws_widthOverAByte_isSplitCorrectly() {
		OptionNegotiator neg = nawsNegotiated();
		neg.setColumns(300); // 0x012C
		neg.setRows(50);

		byte[] naws = neg.getNawsString();
		assertEquals(0x01, naws[3]);
		assertEquals(0x2C, naws[4] & 0xFF);
		assertEquals(0, naws[5]);
		assertEquals(50, naws[6]);
	}

	@Test
	public void naws_zeroAndNegativeAreRefused_notSentAsGarbage() {
		OptionNegotiator neg = new OptionNegotiator("BlowTorch");
		neg.setColumns(100);
		neg.setColumns(0);
		neg.setColumns(-5);

		assertEquals("a bogus size must not overwrite a good one", 100, neg.getColumns());
	}

	// ---- CHARSET (RFC 2066) ------------------------------------------------

	@Test
	public void charset_serverRequest_isAnsweredWithAcceptedAndRemembered() {
		OptionNegotiator neg = new OptionNegotiator("BlowTorch");

		ByteArrayOutputStream payload = new ByteArrayOutputStream();
		payload.write(TC.CHARSET_REQUEST);
		byte[] names = latin1(";UTF-8;ISO-8859-1");
		payload.write(names, 0, names.length);

		byte[] resp = neg.getSubnegotiationResponse(sb(TC.CHARSET, payload.toByteArray()));

		assertEquals(TC.IAC, resp[0]);
		assertEquals(TC.SB, resp[1]);
		assertEquals(TC.CHARSET, resp[2]);
		assertEquals(TC.CHARSET_ACCEPTED, resp[3]);
		assertEquals("UTF-8", new String(resp, 4, resp.length - 6, StandardCharsets.US_ASCII));
		assertEquals("UTF-8", neg.consumePendingCharset());
		assertNull("consuming clears it", neg.consumePendingCharset());
	}

	@Test
	public void charset_ourOwnRequestIsWellFormed() {
		OptionNegotiator neg = new OptionNegotiator("BlowTorch");

		byte[] req = neg.getCharsetRequestUtf8();

		assertEquals(TC.IAC, req[0]);
		assertEquals(TC.SB, req[1]);
		assertEquals(TC.CHARSET, req[2]);
		assertEquals(TC.CHARSET_REQUEST, req[3]);
		assertEquals(';', req[4]);
		assertEquals("UTF-8", new String(req, 5, req.length - 7, StandardCharsets.US_ASCII));
		assertEquals(TC.IAC, req[req.length - 2]);
		assertEquals(TC.SE, req[req.length - 1]);
	}

	// ---- ECHO (RFC 857) ----------------------------------------------------

	@Test
	public void echo_serverTakingOverIsAccepted_andReleasedAgain() {
		// Measured 1 Aug 2026 / re-checked 11 Aug 2026 on eden-test.rpgframework.de:4000:
		// WILL ECHO at connect (held through terminal probing), WONT ECHO in the
		// same packet as "What is your name?", WILL ECHO again with the password
		// prompt. Achaea never uses it. The negotiator must accept WILL; the UI
		// must apply that state *during* rawProcess, not via a queued handler
		// message behind the current dispatch — otherwise the prompt text lands
		// before the mask flips (nickname still dotted, password still clear).
		OptionNegotiator neg = new OptionNegotiator("BlowTorch");
		assertFalse(neg.isServerEcho());

		byte[] accept = neg.processCommand(TC.IAC, (byte) 251 /* WILL */, TC.ECHO);
		assertEquals((byte) 253 /* DO */, accept[1]);
		assertEquals(TC.ECHO, accept[2]);
		assertTrue("input bar must be masked while the server echoes", neg.isServerEcho());

		byte[] release = neg.processCommand(TC.IAC, (byte) 252 /* WONT */, TC.ECHO);
		assertEquals((byte) 254 /* DONT */, release[1]);
		assertFalse("and unmasked again afterwards", neg.isServerEcho());
	}

	@Test
	public void echo_weNeverAgreeToEchoForTheServer() {
		OptionNegotiator neg = new OptionNegotiator("BlowTorch");

		byte[] resp = neg.processCommand(TC.IAC, (byte) 253 /* DO */, TC.ECHO);

		assertEquals((byte) 252 /* WONT */, resp[1]);
		assertFalse("that is the other direction; it must not mask anything",
				neg.isServerEcho());
	}

	@Test
	public void eor_willIsAnsweredWithDo() {
		OptionNegotiator neg = new OptionNegotiator("BlowTorch");

		byte[] resp = neg.processCommand(TC.IAC, TC.WILL, TC.TELOPT_EOR);

		assertEquals(TC.IAC, resp[0]);
		assertEquals(TC.DO, resp[1]);
		assertEquals(TC.TELOPT_EOR, resp[2]);
	}

	@Test
	public void eor_unknownWillStillRefused() {
		OptionNegotiator neg = new OptionNegotiator("BlowTorch");
		byte opt = 99;
		byte[] resp = neg.processCommand(TC.IAC, TC.WILL, opt);
		assertEquals(TC.DONT, resp[1]);
		assertEquals(opt, resp[2]);
	}

	// ---- MSSP (one-way: server announces, we cache) -------------------------

	@Test
	public void mssp_flatVarValPairsAreCached() {
		MudProtocolData data = new MudProtocolData();

		ByteArrayOutputStream p = new ByteArrayOutputStream();
		p.write(TC.MSDP_VAR);
		p.write(latin1("NAME"), 0, 4);
		p.write(TC.MSDP_VAL);
		p.write(latin1("Achaea"), 0, 6);
		p.write(TC.MSDP_VAR);
		p.write(latin1("PLAYERS"), 0, 7);
		p.write(TC.MSDP_VAL);
		p.write(latin1("81"), 0, 2);

		data.absorbMssp(p.toByteArray());

		Map<String, String> snap = data.msspSnapshot();
		assertEquals("Achaea", snap.get("NAME"));
		assertEquals("81", snap.get("PLAYERS"));
		assertTrue(data.msspStatusLine().contains("Achaea"));
	}

	// ---- MSDP (two-way) -----------------------------------------------------

	@Test
	public void msdp_nestedTableIsFlattenedRatherThanLost() {
		MudProtocolData data = new MudProtocolData();

		ByteArrayOutputStream p = new ByteArrayOutputStream();
		p.write(TC.MSDP_VAR);
		p.write(latin1("ROOM"), 0, 4);
		p.write(TC.MSDP_VAL);
		p.write(TC.MSDP_TABLE_OPEN);
		p.write(TC.MSDP_VAR);
		p.write(latin1("NUM"), 0, 3);
		p.write(TC.MSDP_VAL);
		p.write(latin1("1234"), 0, 4);
		p.write(TC.MSDP_TABLE_CLOSE);

		data.absorbMsdp(p.toByteArray());

		String room = data.msdpSnapshot().get("ROOM");
		assertTrue("table kept its shape: " + room, room.startsWith("{") && room.endsWith("}"));
		assertTrue("nested var survived: " + room, room.contains("NUM") && room.contains("1234"));
	}

	@Test
	public void msdp_corruptPayloadIsIgnored_notThrown() {
		MudProtocolData data = new MudProtocolData();

		// Truncated: VAR with no VAL, then a stray VAL with no VAR.
		data.absorbMsdp(new byte[] { TC.MSDP_VAR, 'A', TC.MSDP_VAL });
		data.absorbMsdp(new byte[] { TC.MSDP_VAL, 'x' });
		data.absorbMsdp(new byte[] { 0x00, (byte) 0xFF, 0x7F });
		data.absorbMssp(null);
		data.absorbMssp(new byte[0]);

		// Reaching here without an exception is the assertion; the cache may hold
		// whatever it salvaged.
		assertTrue(data.msdpStatusLine() != null);
	}

	@Test
	public void msdp_clearingDropsTheCache() {
		MudProtocolData data = new MudProtocolData();
		ByteArrayOutputStream p = new ByteArrayOutputStream();
		p.write(TC.MSDP_VAR);
		p.write(latin1("HEALTH"), 0, 6);
		p.write(TC.MSDP_VAL);
		p.write(latin1("100"), 0, 3);
		data.absorbMsdp(p.toByteArray());
		assertEquals("100", data.msdpSnapshot().get("HEALTH"));

		data.clearMsdp();

		assertTrue(data.msdpSnapshot().isEmpty());
		assertEquals("(empty)", data.msdpStatusLine());
	}
}
