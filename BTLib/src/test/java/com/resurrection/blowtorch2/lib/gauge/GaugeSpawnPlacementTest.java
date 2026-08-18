package com.resurrection.blowtorch2.lib.gauge;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GaugeSpawnPlacementTest {

	@Test
	public void unplacedIsNotOrigin() {
		assertEquals(-1, GaugeSpawnPlacement.UNPLACED);
		assertTrue(GaugeSpawnPlacement.isUnplaced(-1, -1));
		assertFalse(GaugeSpawnPlacement.isUnplaced(0, 0));
		assertFalse(GaugeSpawnPlacement.isUnplaced(-1, 0));
		assertFalse(GaugeSpawnPlacement.isUnplaced(8, 8));
		GaugeWidget placed = new GaugeWidget("hp");
		placed.setX(0);
		placed.setY(0);
		assertFalse(placed.isUnplaced());
		GaugeWidget fresh = new GaugeWidget("mana");
		fresh.setX(GaugeSpawnPlacement.UNPLACED);
		fresh.setY(GaugeSpawnPlacement.UNPLACED);
		assertTrue(fresh.isUnplaced());
	}

	@Test
	public void centerInParent() {
		assertArrayEquals(new int[] { 100, 88 },
				GaugeSpawnPlacement.center(400, 200, 200, 24));
		assertArrayEquals(new int[] { 0, 0 },
				GaugeSpawnPlacement.center(200, 24, 200, 24));
		// Widget larger than parent — caller clamps.
		assertArrayEquals(new int[] { -50, -10 },
				GaugeSpawnPlacement.center(100, 20, 200, 40));
	}
}
