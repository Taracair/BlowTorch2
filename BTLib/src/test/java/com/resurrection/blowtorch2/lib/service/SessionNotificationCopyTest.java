package com.resurrection.blowtorch2.lib.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

import com.resurrection.blowtorch2.lib.service.SessionNotificationCopy.Model;
import com.resurrection.blowtorch2.lib.service.SessionNotificationCopy.World;

public class SessionNotificationCopyTest {

	@Test
	public void emptyHasNoActions() {
		Model m = SessionNotificationCopy.build(Collections.<World>emptyList());
		assertEquals(0, m.count);
		assertEquals("", m.tapDisplay);
		assertFalse(m.expand());
		assertTrue(m.actionDisplays.isEmpty());
		assertTrue(m.inboxLines.isEmpty());
	}

	@Test
	public void oneWorldIsCollapsedStatusOnly() {
		Model m = SessionNotificationCopy.build(Arrays.asList(
				new World("world-a", "Connected · a.example:4000 · 12m 00s", true)));
		assertEquals(1, m.count);
		assertEquals("world-a", m.tapDisplay);
		assertEquals("Connected · a.example:4000 · 12m 00s", m.collapsed);
		assertFalse(m.expand());
		assertTrue(m.actionDisplays.isEmpty());
		assertTrue(m.inboxLines.isEmpty());
	}

	@Test
	public void twoWorldsExpandWithCurrentFirst() {
		Model m = SessionNotificationCopy.build(Arrays.asList(
				new World("world-a", "Connected · a.example:4000 · 1h 00m", false),
				new World("world-b", "Connected · b.example:4001 · 3m 00s", true)));
		assertEquals(2, m.count);
		assertTrue(m.expand());
		assertEquals("world-b", m.tapDisplay);
		assertEquals("world-b · world-a", m.collapsed);
		assertEquals(Arrays.asList(
				"▸ world-b · Connected · b.example:4001 · 3m 00s",
				"world-a · Connected · a.example:4000 · 1h 00m"), m.inboxLines);
		assertEquals(Arrays.asList("world-b", "world-a"), m.actionDisplays);
	}

	@Test
	public void fourWorldsCapActionsAtThree() {
		Model m = SessionNotificationCopy.build(Arrays.asList(
				new World("d", "s", false),
				new World("c", "s", false),
				new World("b", "s", false),
				new World("a", "s", true)));
		assertEquals(4, m.count);
		assertEquals("a", m.tapDisplay);
		assertEquals(3, m.actionDisplays.size());
		assertEquals("a", m.actionDisplays.get(0));
		assertEquals(4, m.inboxLines.size());
		assertTrue(m.inboxLines.get(0).startsWith("▸ a"));
	}
}
