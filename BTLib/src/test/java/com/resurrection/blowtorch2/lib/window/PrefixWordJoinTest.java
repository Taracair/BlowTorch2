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

	@Test
	public void dollarOneIsThePickedWordInTheMiddle() {
		assertEquals("fix iron helmet",
				PrefixWordJoin.sendLine("fix $1 helmet", "iron"));
	}

	@Test
	public void dollarOneAtTheFrontPutsTheWordFirst() {
		assertEquals("iron helmet",
				PrefixWordJoin.sendLine("$1 helmet", "iron"));
	}

	@Test
	public void dollarOneAtTheEndDoesNotAlsoAppend() {
		assertEquals("kill goblin",
				PrefixWordJoin.sendLine("kill $1", "goblin"));
	}

	@Test
	public void dollarWordMatchesTappableWordSyntax() {
		assertEquals("get rusty",
				PrefixWordJoin.sendLine("get $word", "rusty"));
	}

	@Test
	public void dollarZeroIsTheSameToken() {
		assertEquals("look at bob",
				PrefixWordJoin.sendLine("look at $0", "bob"));
	}

	@Test
	public void dollarOneGluesToTheRestOfTheToken() {
		assertEquals("fix iron-helmet",
				PrefixWordJoin.sendLine("fix $1-helmet", "iron"));
	}

	@Test
	public void severalSlotsFillTheSameWord() {
		assertEquals("get ring;wear ring",
				PrefixWordJoin.sendLine("get $1;wear $1", "ring"));
	}

	@Test
	public void aPriceIsNotASlotSoTheWordIsStillAppended() {
		assertEquals("cost $5 potion",
				PrefixWordJoin.sendLine("cost $5", "potion"));
		assertEquals("pay $10 gold",
				PrefixWordJoin.sendLine("pay $10", "gold"));
	}

	@Test
	public void dollarTenIsNotDollarOne() {
		assertFalse(PrefixWordJoin.hasSlot("pay $10"));
		assertEquals("pay $10 gold",
				PrefixWordJoin.sendLine("pay $10", "gold"));
		assertTrue(PrefixWordJoin.hasSlot("fix $1 helmet"));
		assertTrue(PrefixWordJoin.hasSlot("get $word"));
		assertTrue(PrefixWordJoin.hasSlot("$1 helmet"));
		assertFalse(PrefixWordJoin.hasSlot("fix"));
	}
}
