package com.resurrection.blowtorch2.lib.mapper;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MapPathfinderTest {

	@Test
	public void findCommandsShortestPath() {
		MudMap map = linearMap();
		List<String> cmds = MapPathfinder.findCommands(map, "a", "c");
		assertEquals(Arrays.asList("e", "e"), cmds);
	}

	@Test
	public void findCommandsSameTileIsEmpty() {
		MudMap map = linearMap();
		assertTrue(MapPathfinder.findCommands(map, "a", "a").isEmpty());
	}

	@Test
	public void findCommandsUnreachableIsEmpty() {
		MudMap map = linearMap();
		MapTile island = new MapTile("z", "L0", 9, 9);
		map.getTiles().add(island);
		assertTrue(MapPathfinder.findCommands(map, "a", "z").isEmpty());
	}

	@Test
	public void findCommandsChoosesFewerHops() {
		MudMap map = new MudMap("m", "test");
		MapTile a = new MapTile("a", "L0", 0, 0);
		MapTile b = new MapTile("b", "L0", 1, 0);
		MapTile c = new MapTile("c", "L0", 2, 0);
		a.addExit(new MapExit("a", "b", "e"));
		b.addExit(new MapExit("b", "c", "e"));
		a.addExit(new MapExit("a", "c", "portal"));
		map.setTiles(Arrays.asList(a, b, c));

		List<String> cmds = MapPathfinder.findCommands(map, "a", "c");
		assertEquals(Arrays.asList("portal"), cmds);
	}

	@Test
	public void findCommandsNullSafe() {
		assertTrue(MapPathfinder.findCommands(null, "a", "b").isEmpty());
		MudMap map = linearMap();
		assertTrue(MapPathfinder.findCommands(map, null, "b").isEmpty());
		assertTrue(MapPathfinder.findCommands(map, "a", null).isEmpty());
		assertTrue(MapPathfinder.findCommands(map, "missing", "a").isEmpty());
	}

	private static MudMap linearMap() {
		MudMap map = new MudMap("m", "linear");
		MapTile a = new MapTile("a", "L0", 0, 0);
		MapTile b = new MapTile("b", "L0", 1, 0);
		MapTile c = new MapTile("c", "L0", 2, 0);
		a.addExit(new MapExit("a", "b", "e"));
		b.addExit(new MapExit("b", "a", "w"));
		b.addExit(new MapExit("b", "c", "e"));
		c.addExit(new MapExit("c", "b", "w"));
		map.setTiles(Arrays.asList(a, b, c));
		return map;
	}
}
