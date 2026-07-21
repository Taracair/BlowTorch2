package com.resurrection.blowtorch2.lib.alias;

import org.junit.Test;

import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AnchoredAliasCapturesTest {

	@Test
	public void fromMatchHarvestsGroups() {
		HashMap<String, String> map = AnchoredAliasCaptures.fromMatch(
				"^look (.+)$", "look north");
		assertEquals("look north", map.get("0"));
		assertEquals("north", map.get("1"));
	}

	@Test
	public void fromMatchFallbackPutsWholeTextWhenNoMatch() {
		HashMap<String, String> map = AnchoredAliasCaptures.fromMatch(
				"^cast (.+)$", "look north");
		assertEquals("look north", map.get("0"));
		assertEquals(1, map.size());
	}

	@Test
	public void fromMatchNullTextReturnsEmpty() {
		HashMap<String, String> map = AnchoredAliasCaptures.fromMatch("^x$", null);
		assertTrue(map.isEmpty());
	}
}
