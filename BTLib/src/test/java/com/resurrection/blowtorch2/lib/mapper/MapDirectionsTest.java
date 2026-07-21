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

	@Test
	public void normalizeIgnoresSpeedwalkDiagonalKeys() {
		java.util.HashMap<String, com.resurrection.blowtorch2.lib.speedwalk.DirectionData> map =
				new java.util.HashMap<String, com.resurrection.blowtorch2.lib.speedwalk.DirectionData>();
		map.put("h", new com.resurrection.blowtorch2.lib.speedwalk.DirectionData("h", "nw"));
		map.put("j", new com.resurrection.blowtorch2.lib.speedwalk.DirectionData("j", "ne"));
		map.put("k", new com.resurrection.blowtorch2.lib.speedwalk.DirectionData("k", "sw"));
		map.put("l", new com.resurrection.blowtorch2.lib.speedwalk.DirectionData("l", "se"));
		assertEquals("se", MapDirections.normalize("go se", map));
		assertEquals("sw", MapDirections.normalize("go sw", map));
		assertEquals("se", MapDirections.normalize("se", map));
		assertEquals("se", MapDirections.normalize("l", map));
		assertEquals(1, MapDirections.gridDelta(MapDirections.normalize("go se", map))[0]);
		assertEquals(1, MapDirections.gridDelta(MapDirections.normalize("go se", map))[1]);
		assertEquals(-1, MapDirections.gridDelta(MapDirections.normalize("go sw", map))[0]);
		assertEquals(1, MapDirections.gridDelta(MapDirections.normalize("go sw", map))[1]);
	}
}
