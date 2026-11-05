package com.resurrection.blowtorch2.lib.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class ConnectedSendHoldTest {

	@Test
	public void takeReturnsCallOrderAndEmptiesTheHold() {
		ConnectedSendHold hold = new ConnectedSendHold();
		hold.add(null);
		hold.add("\u001bhello 1\n");
		hold.add("look");
		List<String> lines = hold.take();
		assertEquals(2, lines.size());
		assertEquals("\u001bhello 1\n", lines.get(0));
		assertEquals("look", lines.get(1));
		assertTrue(hold.take().isEmpty());
	}

	@Test
	public void clearDropsLinesFromAConnectThatNeverCameUp() {
		ConnectedSendHold hold = new ConnectedSendHold();
		hold.add("look");
		hold.clear();
		hold.add("version");
		List<String> lines = hold.take();
		assertEquals(1, lines.size());
		assertEquals("version", lines.get(0));
	}
}
