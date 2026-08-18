/*
 * Copyright (C) BlowTorch contributors
 */
package com.resurrection.blowtorch2.lib.gauge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;

import org.junit.Test;

/**
 * Geometry / live-value parse without constructing Android views.
 */
public class GaugeWidgetControllerLogicTest {

	@Test
	public void parseValuesJson_idVM() {
		HashMap<String, double[]> m = GaugeWidgetController.parseValuesJson(
				"[{\"id\":\"hp\",\"v\":80,\"m\":100},{\"id\":\"mana\",\"v\":12.5,\"m\":50}]");
		assertEquals(2, m.size());
		assertEquals(80.0, m.get("hp")[0], 0.0001);
		assertEquals(100.0, m.get("hp")[1], 0.0001);
		assertEquals(12.5, m.get("mana")[0], 0.0001);
		assertEquals(50.0, m.get("mana")[1], 0.0001);
	}

	@Test
	public void parseValuesJson_normalizesIdAndSkipsReserved() {
		HashMap<String, double[]> m = GaugeWidgetController.parseValuesJson(
				"[{\"id\":\"HP\",\"v\":1,\"m\":2},{\"id\":\"main\",\"v\":3,\"m\":4}]");
		assertEquals(1, m.size());
		assertTrue(m.containsKey("hp"));
		assertEquals(1.0, m.get("hp")[0], 0.0001);
	}

	@Test
	public void parseValuesJson_invalidIsEmpty() {
		assertTrue(GaugeWidgetController.parseValuesJson(null).isEmpty());
		assertTrue(GaugeWidgetController.parseValuesJson("").isEmpty());
		assertTrue(GaugeWidgetController.parseValuesJson("null").isEmpty());
		assertTrue(GaugeWidgetController.parseValuesJson("{not-array}").isEmpty());
	}

	@Test
	public void landscapeZeroCopiesPortraitWithoutMutating() {
		GaugeWidget g = new GaugeWidget("hp");
		g.setX(10);
		g.setY(20);
		g.setW(200);
		g.setH(24);
		int[] geo = GaugeWidgetController.readGeometry(g, true);
		assertEquals(10, geo[0]);
		assertEquals(20, geo[1]);
		assertEquals(200, geo[2]);
		assertEquals(24, geo[3]);
		assertEquals(0, g.getLandX());
		assertEquals(0, g.getLandY());
		assertEquals(0, g.getLandW());
		assertEquals(0, g.getLandH());
	}

	@Test
	public void writeGeometryLandscapeOriginZeroRoundTrips() {
		GaugeWidget g = new GaugeWidget("hp");
		g.setX(10);
		g.setY(20);
		g.setW(200);
		g.setH(24);
		GaugeWidgetController.writeGeometry(g, true, 0, 0, 180, 30);
		int[] geo = GaugeWidgetController.readGeometry(g, true);
		assertEquals(0, geo[0]);
		assertEquals(0, geo[1]);
		assertEquals(180, geo[2]);
		assertEquals(30, geo[3]);
		assertEquals(10, g.getX());
		assertEquals(20, g.getY());
	}

	@Test
	public void writeGeometryLandscapeLeavesPortrait() {
		GaugeWidget g = new GaugeWidget("hp");
		g.setX(10);
		g.setY(20);
		g.setW(200);
		g.setH(24);
		GaugeWidgetController.writeGeometry(g, true, 50, 60, 180, 30);
		assertEquals(10, g.getX());
		assertEquals(20, g.getY());
		assertEquals(200, g.getW());
		assertEquals(24, g.getH());
		assertEquals(50, g.getLandX());
		assertEquals(60, g.getLandY());
		assertEquals(180, g.getLandW());
		assertEquals(30, g.getLandH());
		int[] geo = GaugeWidgetController.readGeometry(g, true);
		assertEquals(50, geo[0]);
		assertEquals(30, geo[3]);
		int[] port = GaugeWidgetController.readGeometry(g, false);
		assertEquals(10, port[0]);
		assertEquals(24, port[3]);
	}

	@Test
	public void writeGeometryPortraitLeavesLand() {
		GaugeWidget g = new GaugeWidget("hp");
		g.setLandX(40);
		g.setLandY(50);
		g.setLandW(160);
		g.setLandH(28);
		GaugeWidgetController.writeGeometry(g, false, 8, 9, 120, 18);
		assertEquals(8, g.getX());
		assertEquals(9, g.getY());
		assertEquals(120, g.getW());
		assertEquals(18, g.getH());
		assertEquals(40, g.getLandX());
		assertEquals(160, g.getLandW());
	}

	@Test
	public void persistOmitsLiveValues() {
		GaugeWidget g = new GaugeWidget("hp");
		g.setLiveValue(77);
		g.setLiveMax(200);
		g.setRemainSec(3);
		GaugeWidgetController.writeGeometry(g, false, 1, 2, 100, 20);
		String json = GaugeWidgetsStore.toJson(java.util.Collections.singletonList(g));
		assertTrue(json.contains("\"x\":1"));
		assertTrue(json.indexOf("live_value") < 0);
		assertTrue(json.indexOf("\"v\":") < 0);
		GaugeWidget again = GaugeWidgetsStore.parse(json).get(0);
		assertEquals(0.0, again.getLiveValue(), 0.0);
		assertEquals(1, again.getX());
	}
}
