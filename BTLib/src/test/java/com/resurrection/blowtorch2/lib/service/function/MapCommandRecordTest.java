package com.resurrection.blowtorch2.lib.service.function;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MapCommandRecordTest {

	@Test
	public void barePrintsStatus() {
		assertEquals(MapCommand.RECORD_STATUS, MapCommand.parseRecord(null));
		assertEquals(MapCommand.RECORD_STATUS, MapCommand.parseRecord(""));
		assertEquals(MapCommand.RECORD_STATUS, MapCommand.parseRecord("   "));
	}

	@Test
	public void toggleFlipsAndOnOffStay() {
		assertEquals(MapCommand.RECORD_TOGGLE, MapCommand.parseRecord("toggle"));
		assertEquals(MapCommand.RECORD_TOGGLE, MapCommand.parseRecord("Toggle"));
		assertEquals(MapCommand.RECORD_ON, MapCommand.parseRecord("on"));
		assertEquals(MapCommand.RECORD_ON, MapCommand.parseRecord("1"));
		assertEquals(MapCommand.RECORD_ON, MapCommand.parseRecord("true"));
		assertEquals(MapCommand.RECORD_OFF, MapCommand.parseRecord("off"));
		assertEquals(MapCommand.RECORD_OFF, MapCommand.parseRecord("0"));
		assertEquals(MapCommand.RECORD_OFF, MapCommand.parseRecord("false"));
	}

	@Test
	public void junkIsNotAFlip() {
		assertEquals(MapCommand.RECORD_BAD, MapCommand.parseRecord("on please"));
		assertEquals(MapCommand.RECORD_BAD, MapCommand.parseRecord("blah"));
	}

	@Test
	public void bareEchoOneWayAndGmcpDoNotFlip() {
		assertEquals(MapCommand.FLAG_STATUS, MapCommand.parseEcho(null));
		assertEquals(MapCommand.FLAG_STATUS, MapCommand.parseEcho(""));
		assertEquals(MapCommand.FLAG_STATUS, MapCommand.parseEcho("   "));
		assertEquals(MapCommand.FLAG_ON, MapCommand.parseEcho("on"));
		assertEquals(MapCommand.FLAG_ON, MapCommand.parseEcho("true"));
		assertEquals(MapCommand.FLAG_OFF, MapCommand.parseEcho("quiet"));
		assertEquals(MapCommand.FLAG_TOGGLE, MapCommand.parseEcho("toggle"));
		assertEquals(MapCommand.FLAG_BAD, MapCommand.parseEcho("on please"));

		assertEquals(MapCommand.FLAG_STATUS, MapCommand.parseOneWay(""));
		assertEquals(MapCommand.FLAG_ON, MapCommand.parseOneWay("accept"));
		assertEquals(MapCommand.FLAG_OFF, MapCommand.parseOneWay("smart"));
		assertEquals(MapCommand.FLAG_OFF, MapCommand.parseOneWay("close"));
		assertEquals(MapCommand.FLAG_TOGGLE, MapCommand.parseOneWay("toggle"));
		assertEquals(MapCommand.FLAG_BAD, MapCommand.parseOneWay("on please"));

		assertEquals(MapCommand.FLAG_STATUS, MapCommand.parseGmcp(""));
		assertEquals(MapCommand.FLAG_ON, MapCommand.parseGmcp("on"));
		assertEquals(MapCommand.FLAG_OFF, MapCommand.parseGmcp("off"));
		assertEquals(MapCommand.FLAG_TOGGLE, MapCommand.parseGmcp("toggle"));
		assertEquals(MapCommand.FLAG_BAD, MapCommand.parseGmcp("on please"));
		assertEquals(MapCommand.FLAG_OTHER, MapCommand.parseGmcp("grow"));
		assertEquals(MapCommand.FLAG_OTHER, MapCommand.parseGmcp("grow on"));

		assertEquals(MapCommand.FLAG_STATUS, MapCommand.parseGmcpGrow(null));
		assertEquals(MapCommand.FLAG_STATUS, MapCommand.parseGmcpGrow(""));
		assertEquals(MapCommand.FLAG_ON, MapCommand.parseGmcpGrow("on"));
		assertEquals(MapCommand.FLAG_OFF, MapCommand.parseGmcpGrow("off"));
		assertEquals(MapCommand.FLAG_TOGGLE, MapCommand.parseGmcpGrow("toggle"));
		assertEquals(MapCommand.FLAG_BAD, MapCommand.parseGmcpGrow("on please"));
		assertEquals(MapCommand.FLAG_BAD, MapCommand.parseGmcpGrow("foo"));
	}
}
