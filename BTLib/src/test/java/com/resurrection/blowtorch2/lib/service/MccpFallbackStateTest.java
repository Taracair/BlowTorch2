package com.resurrection.blowtorch2.lib.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The reconnect after an MCCP failure must go out with compression refused.
 * Both reviewers of the first cut found the same hole: {@code initSettings()}
 * replays every profile option through the same setter on every
 * {@code doStartup()}, so a fallback that cleared itself whenever it was told
 * "use_mccp is true" re-armed compression on its own retry — an unbounded
 * reconnect loop, printing a warning each pass.
 */
public class MccpFallbackStateTest {

	private static final byte COMPRESS2 = (byte) 0x56;

	@Test
	public void onByDefault() {
		assertTrue(new MccpFallbackState().isEnabled());
	}

	@Test
	public void profileReplayDuringTheRetryDoesNotReArmCompression() {
		MccpFallbackState state = new MccpFallbackState();

		assertTrue("first failure asks for the reconnect", state.onFailure());
		assertFalse(state.isEnabled());

		// doStartup() → initSettings() → updateSetting("use_mccp", "true")
		state.applyProfileValue(true);

		assertFalse("the retry must still refuse COMPRESS2", state.isEnabled());
		assertTrue(state.isFallbackEngaged());
	}

	@Test
	public void aSecondFailureDoesNotAskForAnotherReconnect() {
		MccpFallbackState state = new MccpFallbackState();
		state.onFailure();

		assertFalse("no second reconnect — that is the loop", state.onFailure());
	}

	@Test
	public void thePlayerTurningItBackOnClearsTheFallback() {
		MccpFallbackState state = new MccpFallbackState();
		state.onFailure();

		state.applyPlayerToggle(true);

		assertTrue(state.isEnabled());
		assertFalse(state.isFallbackEngaged());
		assertTrue("and the fallback can fire again later", state.onFailure());
	}

	@Test
	public void profileOffKeepsItOffEvenWithoutAFailure() {
		MccpFallbackState state = new MccpFallbackState();

		state.applyProfileValue(false);

		assertFalse(state.isEnabled());
		assertFalse("that is a profile choice, not the fallback", state.isFallbackEngaged());
	}

	@Test
	public void playerTurningItOffIsNotMistakenForAFailure() {
		MccpFallbackState state = new MccpFallbackState();

		state.applyPlayerToggle(false);

		assertFalse(state.isEnabled());
		assertFalse(state.isFallbackEngaged());
	}

	@Test
	public void mccpOff_alsoRefusesTheSubnegotiationThatFlipsTheStream() {
		// The DONT is advisory. A server that sends IAC SB MCCP2 IAC SE anyway must
		// not switch us into zlib, or the fallback's own retry ends in a live socket
		// feeding a dead screen.
		OptionNegotiator neg = new OptionNegotiator("BlowTorch");
		byte[] marker = new byte[] { TC.IAC, TC.SB, COMPRESS2, TC.IAC, TC.SE };

		neg.setUseMCCP(true);
		byte[] armed = neg.getSubnegotiationResponse(marker);
		assertEquals("with MCCP on this is the start-compression signal",
				COMPRESS2, armed[0]);

		neg.setUseMCCP(false);
		assertNull("with MCCP off nothing may start compression",
				neg.getSubnegotiationResponse(marker));
	}

	@Test
	public void mccpOff_answersDont_soTheStreamStaysPlain() {
		OptionNegotiator neg = new OptionNegotiator("BlowTorch");

		neg.setUseMCCP(true);
		byte[] on = neg.processCommand(TC.IAC, (byte) 251 /* WILL */, COMPRESS2);
		assertEquals((byte) 253 /* DO */, on[1]);
		assertEquals(COMPRESS2, on[2]);

		neg.setUseMCCP(false);
		byte[] off = neg.processCommand(TC.IAC, (byte) 251 /* WILL */, COMPRESS2);
		assertEquals((byte) 254 /* DONT */, off[1]);
		assertEquals(COMPRESS2, off[2]);
	}
}
