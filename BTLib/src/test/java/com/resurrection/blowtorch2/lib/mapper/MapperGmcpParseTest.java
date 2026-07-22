package com.resurrection.blowtorch2.lib.mapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * Pure-Java coverage for GMCP coord string helpers.
 * Full Room.Info JSON shapes are exercised on-device (Android stubs org.json).
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
}
