package com.resurrection.blowtorch2.lib.mapper;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class MapDirectionsTest {

	@Test
	public void oppositeCommonPairs() {
		assertEquals("s", MapDirections.opposite("n"));
		assertEquals("n", MapDirections.opposite("s"));
		assertEquals("w", MapDirections.opposite("e"));
		assertEquals("east", MapDirections.opposite("west"));
		assertEquals("down", MapDirections.opposite("up"));
		assertEquals("d", MapDirections.opposite("u"));
		assertEquals("out", MapDirections.opposite("in"));
		assertEquals("sw", MapDirections.opposite("ne"));
		assertNull(MapDirections.opposite("portal"));
		assertNull(MapDirections.opposite(null));
	}

	@Test
	public void normalizeAliasesWithoutDirectionMap() {
		assertEquals("n", MapDirections.normalize("North", null));
		assertEquals("ne", MapDirections.normalize("northeast", null));
		assertEquals("enter cave", MapDirections.normalize("  Enter Cave  ", null));
		assertEquals("", MapDirections.normalize("  ", null));
		assertEquals("", MapDirections.normalize(null, null));
	}

	@Test
	public void normalizeGoWalkPrefixes() {
		assertEquals("w", MapDirections.normalize("go west", null));
		assertEquals("e", MapDirections.normalize("GO EAST", null));
		assertEquals("n", MapDirections.normalize("walk north", null));
		assertEquals("out", MapDirections.normalize("go out", null));
		assertEquals("south", MapDirections.opposite("go north"));
		assertEquals("go south", MapDirections.suggestReverse("go north", null));
		assertEquals("go west", MapDirections.storeCommand("go west", "w"));
	}

	@Test
	public void gridDeltaLexicon() {
		assertEquals(0, MapDirections.gridDelta("n")[0]);
		assertEquals(-1, MapDirections.gridDelta("n")[1]);
		assertEquals(1, MapDirections.gridDelta("east")[0]);
		assertEquals(0, MapDirections.gridDelta("e")[1]);
		assertEquals(-1, MapDirections.gridDelta(MapDirections.normalize("go west", null))[0]);
		assertNull(MapDirections.gridDelta("out"));
		assertNull(MapDirections.gridDelta("portal"));
		assertEquals(Integer.valueOf(1), MapDirections.levelDelta("climb"));
		assertEquals(Integer.valueOf(-1), MapDirections.levelDelta("descend"));
		assertEquals("u", MapDirections.normalize("ascend", null));
	}
}
