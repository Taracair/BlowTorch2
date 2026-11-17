package com.resurrection.blowtorch2.lib.mapper;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class MapperGeometryEventTest {

	@Test
	public void willNamesTheFrameAboutToBeWritten() {
		assertEquals("will\tfloat\t12\t48\t280\t320",
				MapperGeometryEvent.message("will", false, 12, 48, 280, 320));
		assertEquals("did\tfull\t0\t0\t0\t0",
				MapperGeometryEvent.message("did", true, 0, 0, 0, 0));
	}

	@Test
	public void aFloatAnswerReplacesTheFrame() {
		assertArrayEquals(new int[] { 12, 40, 280, 200 },
				MapperGeometryEvent.floatAdjustment("12,40,280,200"));
		assertArrayEquals(new int[] { -4, 0, 10, 10 },
				MapperGeometryEvent.floatAdjustment("  -4 0 10 10 "));
	}

	@Test
	public void silenceOrGarbageLeavesTheFrameAlone() {
		assertNull(MapperGeometryEvent.floatAdjustment(null));
		assertNull(MapperGeometryEvent.floatAdjustment(""));
		assertNull(MapperGeometryEvent.floatAdjustment("   "));
		assertNull(MapperGeometryEvent.floatAdjustment("12,40"));
		assertNull(MapperGeometryEvent.floatAdjustment("12,40,0,200"));
		assertNull(MapperGeometryEvent.floatAdjustment("nope"));
	}
}
