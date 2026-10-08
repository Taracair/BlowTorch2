package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LastBarChipsTest {

	@Test
	public void leftPutsOneOnTheLeft() {
		int[] order = LastBarChips.numbersLeftToRight(3, false);
		assertEquals(1, order[0]);
		assertEquals(2, order[1]);
		assertEquals(3, order[2]);
		assertEquals(3, order[order.length - 1]);
	}

	@Test
	public void rightPutsOneOnTheRight() {
		// north, then look, then inventory: 1 is inventory.
		int[] order = LastBarChips.numbersLeftToRight(3, true);
		assertEquals(3, order[0]);
		assertEquals(2, order[1]);
		assertEquals(1, order[order.length - 1]);
		assertEquals("3 north", LastBarChips.label(order[0], "north", 20));
		assertEquals("2 look", LastBarChips.label(order[1], "look", 20));
		assertEquals("1 inventory", LastBarChips.label(order[2], "inventory", 20));
	}

	@Test
	public void firstSuggestionSitsOnTheRight() {
		int[] slots = LastBarChips.logicalLeftToRight(3, true);
		assertEquals(2, slots[0]);
		assertEquals(0, slots[slots.length - 1]);
		assertEquals(0, LastBarChips.logicalLeftToRight(3, false)[0]);
	}

	@Test
	public void shorterCommandsShowInFull() {
		assertEquals("look", LastBarChips.clip("look", 12));
		assertEquals("012345678901", LastBarChips.clip("0123456789012345", 12));
		assertEquals("1 look", LastBarChips.label(1, "look", 12));
	}

	@Test
	public void prefsNameFollowsTheFoldedDisplayName() {
		assertEquals("LASTBAR_world-a", LastBarChips.prefsName("world-a"));
	}
}
