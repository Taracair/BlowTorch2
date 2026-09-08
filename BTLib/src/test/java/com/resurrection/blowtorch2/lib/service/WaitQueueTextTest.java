package com.resurrection.blowtorch2.lib.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class WaitQueueTextTest {

	@Test
	public void emptyQueue() {
		assertEquals("\n[wait queue empty]\n",
				WaitQueueText.format(Collections.<WaitQueueText.Item>emptyList()));
		assertEquals("\n[wait queue empty]\n", WaitQueueText.format(null));
	}

	@Test
	public void dueNowAndRemaining() {
		ArrayList<WaitQueueText.Item> items = new ArrayList<WaitQueueText.Item>();
		items.add(new WaitQueueText.Item(0L, "south"));
		items.add(new WaitQueueText.Item(1500L, "look"));
		assertEquals("\n[wait queue]\n"
				+ "1) due now — south\n"
				+ "2) in 1s500ms — look\n",
				WaitQueueText.format(items));
	}

	@Test
	public void remainderJoinsSegmentsAndHoldover() {
		assertEquals("get all;i",
				WaitQueueText.remainderPreview(
						Arrays.asList("get all", "i"), ""));
		assertEquals("say hi;",
				WaitQueueText.remainderPreview(Collections.<String>emptyList(),
						"say hi;"));
		assertEquals("say hi;wave",
				WaitQueueText.remainderPreview(Arrays.asList("wave"), "say hi;"));
	}

	@Test
	public void remainderTruncates() {
		StringBuilder longCmd = new StringBuilder();
		for (int i = 0; i < 40; i++) {
			longCmd.append("north;");
		}
		String preview = WaitQueueText.remainderPreview(
				Arrays.asList(longCmd.toString()), "");
		assertTrue(preview.endsWith("..."));
		assertEquals(80, preview.length());
	}

	@Test
	public void resolveSlotArmedThenJustPaused() {
		assertEquals(0, WaitQueueText.resolveSlot(2, false, 1));
		assertEquals(1, WaitQueueText.resolveSlot(2, false, 2));
		assertEquals(WaitQueueText.INVALID, WaitQueueText.resolveSlot(2, false, 3));
		assertEquals(WaitQueueText.JUST_PAUSED, WaitQueueText.resolveSlot(1, true, 2));
		assertEquals(WaitQueueText.JUST_PAUSED, WaitQueueText.resolveSlot(0, true, 1));
		assertEquals(WaitQueueText.INVALID, WaitQueueText.resolveSlot(0, false, 1));
		assertEquals(WaitQueueText.INVALID, WaitQueueText.resolveSlot(1, true, 0));
	}
}
