package com.resurrection.blowtorch2.lib.mapper;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MapConflictDetectorTest {

	@Test
	public void detectsAsymmetricLink() {
		MudMap map = new MudMap("m", "asym");
		MapTile a = new MapTile("a", "L0", 0, 0);
		MapTile b = new MapTile("b", "L0", 1, 0);
		a.addExit(new MapExit("a", "b", "n"));
		map.setTiles(Arrays.asList(a, b));

		List<MapConflict> conflicts = MapConflictDetector.scan(map);
		assertEquals(1, countType(conflicts, MapConflict.Type.ASYMMETRIC));
		assertEquals(0, countType(conflicts, MapConflict.Type.GRID_COLLISION));
	}

	@Test
	public void symmetricLinkIsClean() {
		MudMap map = new MudMap("m", "sym");
		MapTile a = new MapTile("a", "L0", 0, 0);
		MapTile b = new MapTile("b", "L0", 0, 1);
		a.addExit(new MapExit("a", "b", "n", false, "s"));
		b.addExit(new MapExit("b", "a", "s", false, "n"));
		map.setTiles(Arrays.asList(a, b));

		List<MapConflict> conflicts = MapConflictDetector.scan(map);
		assertEquals(0, countType(conflicts, MapConflict.Type.ASYMMETRIC));
	}

	@Test
	public void specialExitSkipsAsymmetry() {
		MudMap map = new MudMap("m", "special");
		MapTile a = new MapTile("a", "L0", 0, 0);
		MapTile b = new MapTile("b", "L0", 5, 5);
		a.addExit(new MapExit("a", "b", "enter portal", true, null));
		map.setTiles(Arrays.asList(a, b));

		List<MapConflict> conflicts = MapConflictDetector.scan(map);
		assertEquals(0, countType(conflicts, MapConflict.Type.ASYMMETRIC));
	}

	@Test
	public void detectsDuplicateExitCommand() {
		MudMap map = new MudMap("m", "dup");
		MapTile a = new MapTile("a", "L0", 0, 0);
		MapTile b = new MapTile("b", "L0", 1, 0);
		MapTile c = new MapTile("c", "L0", 0, 1);
		a.addExit(new MapExit("a", "b", "n"));
		a.addExit(new MapExit("a", "c", "N"));
		b.addExit(new MapExit("b", "a", "s"));
		c.addExit(new MapExit("c", "a", "s"));
		map.setTiles(Arrays.asList(a, b, c));

		List<MapConflict> conflicts = MapConflictDetector.scan(map);
		assertEquals(1, countType(conflicts, MapConflict.Type.DUPLICATE_EXIT));
	}

	@Test
	public void detectsGridCollisionSameLevel() {
		MudMap map = new MudMap("m", "grid");
		MapTile a = new MapTile("a", "L0", 1, 2);
		MapTile b = new MapTile("b", "L0", 1, 2);
		map.setTiles(Arrays.asList(a, b));

		List<MapConflict> conflicts = MapConflictDetector.scan(map);
		assertEquals(1, countType(conflicts, MapConflict.Type.GRID_COLLISION));
		MapConflict c = firstOfType(conflicts, MapConflict.Type.GRID_COLLISION);
		assertTrue(c.getTileIds().contains("a"));
		assertTrue(c.getTileIds().contains("b"));
	}

	@Test
	public void differentLevelsSameCoordsNotCollision() {
		MudMap map = new MudMap("m", "levels");
		MapTile a = new MapTile("a", "L0", 1, 1);
		MapTile b = new MapTile("b", "L1", 1, 1);
		map.setTiles(Arrays.asList(a, b));

		List<MapConflict> conflicts = MapConflictDetector.scan(map);
		assertEquals(0, countType(conflicts, MapConflict.Type.GRID_COLLISION));
	}

	@Test
	public void refreshPreservesResolvedAndGmcp() {
		MudMap map = new MudMap("m", "refresh");
		MapTile a = new MapTile("a", "L0", 0, 0);
		MapTile b = new MapTile("b", "L0", 0, 0);
		map.setTiles(Arrays.asList(a, b));

		MapConflict resolved = new MapConflict(MapConflict.Type.ASYMMETRIC, "old",
				Arrays.asList("x"));
		resolved.setResolved(true);
		MapConflict gmcp = new MapConflict(MapConflict.Type.GMCP_MISMATCH, "gmcp",
				Arrays.asList("a"));
		map.setConflicts(Arrays.asList(resolved, gmcp));

		MapConflictDetector.refreshConflicts(map);

		boolean sawResolved = false;
		boolean sawGmcp = false;
		boolean sawGrid = false;
		for (MapConflict c : map.getConflicts()) {
			if (c.isResolved() && c.getType() == MapConflict.Type.ASYMMETRIC) {
				sawResolved = true;
			}
			if (c.getType() == MapConflict.Type.GMCP_MISMATCH) {
				sawGmcp = true;
			}
			if (c.getType() == MapConflict.Type.GRID_COLLISION) {
				sawGrid = true;
				assertFalse(c.isResolved());
			}
		}
		assertTrue(sawResolved);
		assertTrue(sawGmcp);
		assertTrue(sawGrid);
	}

	private static int countType(List<MapConflict> list, MapConflict.Type type) {
		int n = 0;
		for (MapConflict c : list) {
			if (c.getType() == type) {
				n++;
			}
		}
		return n;
	}

	private static MapConflict firstOfType(List<MapConflict> list, MapConflict.Type type) {
		for (MapConflict c : list) {
			if (c.getType() == type) {
				return c;
			}
		}
		return null;
	}
}
