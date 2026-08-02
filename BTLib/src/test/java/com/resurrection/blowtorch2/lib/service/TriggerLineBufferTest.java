package com.resurrection.blowtorch2.lib.service;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Characterisation of the holdover that keeps trigger matching on complete lines.
 */
public class TriggerLineBufferTest {

	@Test
	public void completeChunkPassesThrough() {
		TriggerLineBuffer.Slice s = TriggerLineBuffer.take("",
				"7:15 pm [chatnet] hi\n");
		assertEquals("7:15 pm [chatnet] hi\n", s.ready);
		assertEquals("", s.holdover);
	}

	@Test
	public void partialChunkIsHeld() {
		TriggerLineBuffer.Slice s = TriggerLineBuffer.take("",
				"7:15 pm [chatnet] Cuddles says, \"You want it ");
		assertEquals("", s.ready);
		assertEquals("7:15 pm [chatnet] Cuddles says, \"You want it ", s.holdover);
	}

	@Test
	public void secondChunkCompletesTheLine() {
		String held = "7:15 pm [chatnet] Cuddles says, \"You want it ";
		TriggerLineBuffer.Slice s = TriggerLineBuffer.take(held,
				"off or you're going to follow twice\"\n");
		assertEquals(
				"7:15 pm [chatnet] Cuddles says, \"You want it off or you're going to follow twice\"\n",
				s.ready);
		assertEquals("", s.holdover);
	}

	@Test
	public void multipleLinesLeaveAPartialTail() {
		TriggerLineBuffer.Slice s = TriggerLineBuffer.take("",
				"one\ntwo\nthree without nl");
		assertEquals("one\ntwo\n", s.ready);
		assertEquals("three without nl", s.holdover);
	}

	@Test
	public void nullsAreEmpty() {
		TriggerLineBuffer.Slice s = TriggerLineBuffer.take(null, null);
		assertEquals("", s.ready);
		assertEquals("", s.holdover);
	}
}
