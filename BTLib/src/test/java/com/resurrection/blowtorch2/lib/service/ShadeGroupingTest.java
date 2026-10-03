package com.resurrection.blowtorch2.lib.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ShadeGroupingTest {

	@Test
	public void defaultIndexIsNested() {
		assertEquals(ShadeGrouping.NESTED, ShadeGrouping.layoutFromIndex(null));
		assertEquals(ShadeGrouping.NESTED, ShadeGrouping.layoutFromIndex(Integer.valueOf(0)));
		assertEquals(ShadeGrouping.SEPARATE, ShadeGrouping.layoutFromIndex(Integer.valueOf(1)));
	}

	@Test
	public void nestedPutsConnectionAlertsAndChatInOneGroup() {
		assertEquals(ShadeGrouping.GROUP_STACK, ShadeGrouping.groupKey(
				ShadeGrouping.NESTED, ShadeGrouping.KIND_CONNECTION, false));
		assertEquals(ShadeGrouping.GROUP_STACK, ShadeGrouping.groupKey(
				ShadeGrouping.NESTED, ShadeGrouping.KIND_ALERT, false));
		assertEquals(ShadeGrouping.GROUP_STACK, ShadeGrouping.groupKey(
				ShadeGrouping.NESTED, ShadeGrouping.KIND_CHAT, false));
	}

	@Test
	public void separateUsesOneGroupPerType() {
		assertEquals(ShadeGrouping.GROUP_CONNECTION, ShadeGrouping.groupKey(
				ShadeGrouping.SEPARATE, ShadeGrouping.KIND_CONNECTION, false));
		assertEquals(ShadeGrouping.GROUP_ALERTS, ShadeGrouping.groupKey(
				ShadeGrouping.SEPARATE, ShadeGrouping.KIND_ALERT, false));
		assertEquals(ShadeGrouping.GROUP_CHAT, ShadeGrouping.groupKey(
				ShadeGrouping.SEPARATE, ShadeGrouping.KIND_CHAT, false));
	}

	@Test
	public void spawnNewIsOwnBarEvenWhenNested() {
		assertNull(ShadeGrouping.groupKey(
				ShadeGrouping.NESTED, ShadeGrouping.KIND_ALERT, true));
		assertNull(ShadeGrouping.groupKey(
				ShadeGrouping.SEPARATE, ShadeGrouping.KIND_ALERT, true));
		assertEquals(ShadeGrouping.GROUP_STACK, ShadeGrouping.groupKey(
				ShadeGrouping.NESTED, ShadeGrouping.KIND_CONNECTION, true));
	}

	@Test
	public void chatChannelIsChatKind() {
		assertEquals(ShadeGrouping.KIND_CHAT,
				ShadeGrouping.kindForChannel("BlowTorch2_chat_tells"));
		assertEquals(ShadeGrouping.KIND_ALERT,
				ShadeGrouping.kindForChannel("BlowTorch2_alerts"));
		assertEquals(ShadeGrouping.KIND_ALERT, ShadeGrouping.kindForChannel(null));
		assertTrue(ShadeGrouping.isManagedGroup(ShadeGrouping.GROUP_STACK));
		assertFalse(ShadeGrouping.isManagedGroup(null));
		assertFalse(ShadeGrouping.isManagedGroup("other"));
	}
}
