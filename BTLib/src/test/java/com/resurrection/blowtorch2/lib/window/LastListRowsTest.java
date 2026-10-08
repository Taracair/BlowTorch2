package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LastListRowsTest {

	@Test
	public void fontStaysInsideTheRange() {
		assertEquals(14, LastListRows.fontSp(14));
		assertEquals(10, LastListRows.fontSp(1));
		assertEquals(32, LastListRows.fontSp(80));
	}

	@Test
	public void fingerOnARowSelectsThatRow() {
		int[] top = { 0, 40, 80 };
		int[] bottom = { 36, 76, 120 };
		assertEquals(0, LastListRows.rowAt(0, top, bottom));
		assertEquals(1, LastListRows.rowAt(40, top, bottom));
		assertEquals(2, LastListRows.rowAt(119, top, bottom));
	}

	@Test
	public void fingerBetweenRowsSelectsNothing() {
		int[] top = { 0, 40 };
		int[] bottom = { 36, 76 };
		assertEquals(-1, LastListRows.rowAt(36, top, bottom));
		assertEquals(-1, LastListRows.rowAt(38, top, bottom));
		assertEquals(-1, LastListRows.rowAt(-1, top, bottom));
		assertEquals(-1, LastListRows.rowAt(76, top, bottom));
	}

	@Test
	public void aDragOntoAnotherRowFollowsTheFinger() {
		int[] top = { 0, 48 };
		int[] bottom = { 44, 96 };
		assertEquals(0, LastListRows.rowAt(10, top, bottom));
		assertEquals(1, LastListRows.rowAt(50, top, bottom));
	}
}
