package com.resurrection.blowtorch2.lib.mapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Pure-Java coverage for GMCP coord helpers, policy normalize, and lock flags.
 * MapStore JSON round-trips need a real org.json (Android stubs are unmocked).
 */
public class MapperGmcpParseTest {

	@Test
	public void coordsStringIdXyz() {
		Integer[] xyz = MapperController.parseCoordsString("0,16,9,1");
		assertNotNull(xyz);
		assertEquals(Integer.valueOf(16), xyz[0]);
		assertEquals(Integer.valueOf(9), xyz[1]);
		assertEquals(Integer.valueOf(1), xyz[2]);
	}

	@Test
	public void coordsStringXy() {
		Integer[] xyz = MapperController.parseCoordsString("18,15");
		assertNotNull(xyz);
		assertEquals(Integer.valueOf(18), xyz[0]);
		assertEquals(Integer.valueOf(15), xyz[1]);
		assertNull(xyz[2]);
	}

	@Test
	public void coordsStringEmpty() {
		assertNull(MapperController.parseCoordsString(""));
		assertNull(MapperController.parseCoordsString(null));
	}

	@Test
	public void normalizeGmcpPolicy() {
		assertEquals(MapperController.GMCP_POLICY_SYNC,
				MapperController.normalizeGmcpPolicy(null));
		assertEquals(MapperController.GMCP_POLICY_FOLLOW,
				MapperController.normalizeGmcpPolicy("follow"));
		assertEquals(MapperController.GMCP_POLICY_FOLLOW,
				MapperController.normalizeGmcpPolicy("off"));
		assertEquals(MapperController.GMCP_POLICY_STRICT,
				MapperController.normalizeGmcpPolicy("STRICT"));
		assertEquals(MapperController.GMCP_POLICY_SYNC,
				MapperController.normalizeGmcpPolicy("sync"));
	}

	@Test
	public void tileLockFlagsDefaultOff() {
		MapTile tile = new MapTile(null, "L0", 0, 0);
		assertFalse(tile.isLockTitle());
		assertFalse(tile.isLockPosition());
		tile.setLockTitle(true);
		tile.setLockPosition(true);
		assertTrue(tile.isLockTitle());
		assertTrue(tile.isLockPosition());
	}

	@Test
	public void sparseAndUnitGridProfileDefaults() {
		MapperGmcpProfiles.Profile sparse =
				MapperGmcpProfiles.defaultsFor(MapperGmcpProfiles.PROFILE_SPARSE);
		assertFalse(sparse.useCoords);
		assertEquals(MapperController.GMCP_POLICY_SYNC, sparse.policy);
		MapperGmcpProfiles.Profile grid =
				MapperGmcpProfiles.defaultsFor(MapperGmcpProfiles.PROFILE_UNIT_GRID);
		assertTrue(grid.useCoords);
	}
}
