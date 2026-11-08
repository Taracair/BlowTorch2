package com.resurrection.blowtorch2.lib.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.resurrection.blowtorch2.lib.alias.AliasLocalEcho;

public class SendEchoTest {

	@Test
	public void arg1TwoShowsAndArg1OneHides() {
		assertEquals(SendEcho.INHERIT, SendEcho.mode(0));
		assertEquals(SendEcho.HIDE, SendEcho.mode(1));
		assertEquals(SendEcho.SHOW, SendEcho.mode(2));
		assertEquals(SendEcho.INHERIT, SendEcho.mode(3));
		assertEquals(AliasLocalEcho.FORCE_ON, SendEcho.policy(SendEcho.SHOW));
		assertEquals(AliasLocalEcho.FORCE_OFF, SendEcho.policy(SendEcho.HIDE));
		assertEquals(AliasLocalEcho.INHERIT, SendEcho.policy(SendEcho.INHERIT));
	}

	@Test
	public void aCheckedShortcutShowsWhenTheOptionIsOff() {
		assertTrue(AliasLocalEcho.shouldDisplay(false, true,
				SendEcho.shown(AliasLocalEcho.INHERIT, SendEcho.SHOW)));
		assertFalse(AliasLocalEcho.shouldDisplay(true, true,
				SendEcho.shown(AliasLocalEcho.INHERIT, SendEcho.HIDE)));
		assertFalse(AliasLocalEcho.shouldDisplay(false, true,
				SendEcho.shown(AliasLocalEcho.FORCE_OFF, SendEcho.SHOW)));
		assertTrue(AliasLocalEcho.shouldDisplay(false, true,
				SendEcho.shown(AliasLocalEcho.FORCE_ON, SendEcho.HIDE)));
		assertTrue(SendEcho.hideLine(SendEcho.HIDE));
		assertFalse(SendEcho.hideLine(SendEcho.SHOW));
		assertFalse(AliasLocalEcho.shouldDisplay(false, false,
				SendEcho.shown(null, SendEcho.SHOW)));
	}
}
