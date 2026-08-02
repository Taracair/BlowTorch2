package com.resurrection.blowtorch2.lib.alias;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AliasLocalEchoTest {

	@Test
	public void inheritMatchesGlobalWhenTelnetAllows() {
		assertTrue(AliasLocalEcho.shouldDisplay(true, true, AliasLocalEcho.INHERIT));
		assertFalse(AliasLocalEcho.shouldDisplay(false, true, AliasLocalEcho.INHERIT));
	}

	@Test
	public void telnetFloorBlocksEverythingIncludingForceOn() {
		assertFalse(AliasLocalEcho.shouldDisplay(true, false, AliasLocalEcho.INHERIT));
		assertFalse(AliasLocalEcho.shouldDisplay(false, false, AliasLocalEcho.FORCE_ON));
		assertFalse(AliasLocalEcho.shouldDisplay(true, false, AliasLocalEcho.FORCE_ON));
		assertFalse(AliasLocalEcho.shouldDisplay(true, false, AliasLocalEcho.FORCE_OFF));
	}

	@Test
	public void forceOnShowsWhenGlobalOff() {
		assertTrue(AliasLocalEcho.shouldDisplay(false, true, AliasLocalEcho.FORCE_ON));
		assertTrue(AliasLocalEcho.shouldDisplay(true, true, AliasLocalEcho.FORCE_ON));
	}

	@Test
	public void forceOffHidesWhenGlobalOn() {
		assertFalse(AliasLocalEcho.shouldDisplay(true, true, AliasLocalEcho.FORCE_OFF));
		assertFalse(AliasLocalEcho.shouldDisplay(false, true, AliasLocalEcho.FORCE_OFF));
	}

	@Test
	public void nullPolicyTreatedAsInherit() {
		assertTrue(AliasLocalEcho.shouldDisplay(true, true, null));
		assertFalse(AliasLocalEcho.shouldDisplay(false, true, null));
	}

	@Test
	public void parseAttribute() {
		assertEquals(AliasLocalEcho.INHERIT, AliasLocalEcho.fromAttribute(null));
		assertEquals(AliasLocalEcho.INHERIT, AliasLocalEcho.fromAttribute(""));
		assertEquals(AliasLocalEcho.INHERIT, AliasLocalEcho.fromAttribute("inherit"));
		assertEquals(AliasLocalEcho.FORCE_ON, AliasLocalEcho.fromAttribute("on"));
		assertEquals(AliasLocalEcho.FORCE_ON, AliasLocalEcho.fromAttribute("true"));
		assertEquals(AliasLocalEcho.FORCE_OFF, AliasLocalEcho.fromAttribute("off"));
		assertEquals(AliasLocalEcho.FORCE_OFF, AliasLocalEcho.fromAttribute("hide"));
	}

	@Test
	public void formatAttributeOmitsInherit() {
		assertNull(AliasLocalEcho.INHERIT.toAttribute());
		assertEquals("on", AliasLocalEcho.FORCE_ON.toAttribute());
		assertEquals("off", AliasLocalEcho.FORCE_OFF.toAttribute());
	}

	@Test
	public void aliasUpdateEchoHonorsForceAndInherit() {
		assertTrue(AliasLocalEcho.shouldEchoAliasUpdate(true, AliasLocalEcho.INHERIT));
		assertFalse(AliasLocalEcho.shouldEchoAliasUpdate(false, AliasLocalEcho.INHERIT));
		assertTrue(AliasLocalEcho.shouldEchoAliasUpdate(false, AliasLocalEcho.FORCE_ON));
		assertFalse(AliasLocalEcho.shouldEchoAliasUpdate(true, AliasLocalEcho.FORCE_OFF));
		assertTrue(AliasLocalEcho.shouldEchoAliasUpdate(true, null));
		assertFalse(AliasLocalEcho.shouldEchoAliasUpdate(false, null));
	}
}
