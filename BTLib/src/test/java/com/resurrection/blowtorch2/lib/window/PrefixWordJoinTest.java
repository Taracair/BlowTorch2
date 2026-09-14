package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PrefixWordJoinTest {

	@Test
	public void trailingSpaceOnThePrefixDoesNotDouble() {
		assertEquals("fix helmet", PrefixWordJoin.sendLine("fix ", "helmet"));
	}

	@Test
	public void aBarePrefixGetsOneSpace() {
		assertEquals("fix helmet", PrefixWordJoin.sendLine("fix", "helmet"));
	}

	@Test
	public void whitespaceOnTheWordIsTrimmedOffTheWire() {
		assertEquals("fix helmet", PrefixWordJoin.sendLine("fix", "helmet "));
	}

	@Test
	public void aBlankPrefixRefuses() {
		assertNull(PrefixWordJoin.sendLine("", "helmet"));
		assertNull(PrefixWordJoin.sendLine("   ", "helmet"));
		assertNull(PrefixWordJoin.sendLine(null, "helmet"));
	}

	@Test
	public void aBlankWordRefuses() {
		assertNull(PrefixWordJoin.sendLine("fix ", ""));
		assertNull(PrefixWordJoin.sendLine("fix ", "   "));
		assertNull(PrefixWordJoin.sendLine("fix ", null));
	}

	@Test
	public void aMultiWordPrefixStaysIntact() {
		assertEquals("look at helmet", PrefixWordJoin.sendLine("look at ", "helmet"));
	}

	@Test
	public void aDotCommandIsNotUsable() {
		assertNull(PrefixWordJoin.sendLine(".pick", "helmet"));
		assertNull(PrefixWordJoin.sendLine(".pick hold", "helmet"));
		assertFalse(PrefixWordJoin.usablePrefix(".pick"));
		assertTrue(PrefixWordJoin.usablePrefix("fix "));
	}
}
