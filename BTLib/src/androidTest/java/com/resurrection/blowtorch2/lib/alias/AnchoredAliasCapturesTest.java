package com.resurrection.blowtorch2.lib.alias;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.resurrection.blowtorch2.lib.responder.ack.AckResponder;

import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

@RunWith(AndroidJUnit4.class)
public class AnchoredAliasCapturesTest {

	@Test
	public void anchoredAliasCapturesRegexGroups() {
		assertEquals("fireball", AnchoredAliasCaptures.fromMatch("^cast (.+)$", "cast fireball").get("1"));
	}

	@Test
	public void ackCommandsRouteThroughAliasPipelineUnlessScriptPrefixed() {
		assertFalse(AckResponder.shouldSendAckAsLua("say hi"));
		assertFalse(AckResponder.shouldSendAckAsLua("n"));
		assertFalse(AckResponder.shouldSendAckAsLua("cast bob"));
		assertFalse(AckResponder.shouldSendAckAsLua("heal $1"));
		assertTrue(AckResponder.shouldSendAckAsLua("/sendCommand()"));
		assertFalse(AckResponder.shouldSendAckAsLua("/"));
	}
}
