package com.resurrection.blowtorch2.lib.gauge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;

import org.json.JSONObject;
import org.junit.Test;

public class GaugeWidgetsStoreTest {

	@Test
	public void normalizeName_acceptsValid() {
		assertEquals("hp", GaugeWidgetsStore.normalizeName("HP"));
		assertEquals("mana_1", GaugeWidgetsStore.normalizeName(" mana_1 "));
	}

	@Test
	public void normalizeName_rejectsReservedAndInvalid() {
		assertNull(GaugeWidgetsStore.normalizeName("main"));
		assertNull(GaugeWidgetsStore.normalizeName("mainDisplay"));
		assertNull(GaugeWidgetsStore.normalizeName("button_window"));
		assertNull(GaugeWidgetsStore.normalizeName("Bad-Name"));
		assertNull(GaugeWidgetsStore.normalizeName(""));
		assertNull(GaugeWidgetsStore.normalizeName(null));
	}

	@Test
	public void parse_invalidJson_returnsEmpty() {
		assertTrue(GaugeWidgetsStore.parse(null).isEmpty());
		assertTrue(GaugeWidgetsStore.parse("").isEmpty());
		assertTrue(GaugeWidgetsStore.parse("{not-array}").isEmpty());
		assertTrue(GaugeWidgetsStore.parse("null").isEmpty());
	}

	@Test
	public void parseAndToJson_roundTrip() {
		String json = "["
				+ "{\"id\":\"hp\",\"label\":\"HP\",\"shape\":\"hbar\","
				+ "\"source\":\"gmcp\",\"path\":\"Char.Vitals.hp\","
				+ "\"max_path\":\"Char.Vitals.maxhp\","
				+ "\"color_fill\":\"#CC2222\",\"color_track\":\"#333333\","
				+ "\"color_warn\":\"#FFAA00\",\"warn_pct\":25,\"opacity\":85,"
				+ "\"show_value\":true,\"show_label\":true,\"visible\":true,"
				+ "\"ime_mode\":\"stay\",\"x\":16,\"y\":8,\"w\":200,\"h\":24,"
				+ "\"land_x\":0,\"land_y\":0,\"land_w\":0,\"land_h\":0,"
				+ "\"tap_command\":\"score\"}"
				+ "]";
		ArrayList<GaugeWidget> list = GaugeWidgetsStore.parse(json);
		assertEquals(1, list.size());
		GaugeWidget g = list.get(0);
		assertEquals("hp", g.getId());
		assertEquals("HP", g.getLabel());
		assertEquals(GaugeWidget.Shape.HBAR, g.getShape());
		assertEquals(GaugeWidget.Source.GMCP, g.getSource());
		assertEquals("Char.Vitals.hp", g.getPath());
		assertEquals("Char.Vitals.maxhp", g.getMaxPath());
		assertEquals(GaugeWidget.DEFAULT_COLOR_FILL, g.getColorFill());
		assertEquals(85, g.getOpacity());
		assertTrue(g.isShowValue());
		assertEquals(GaugeWidget.ImeMode.STAY, g.getImeMode());
		assertEquals("score", g.getTapCommand());
		String out = GaugeWidgetsStore.toJson(list);
		ArrayList<GaugeWidget> again = GaugeWidgetsStore.parse(out);
		assertEquals(1, again.size());
		assertEquals("hp", again.get(0).getId());
		assertEquals("HP", again.get(0).getLabel());
		assertEquals(GaugeWidget.Shape.HBAR, again.get(0).getShape());
		assertEquals(GaugeWidget.Source.GMCP, again.get(0).getSource());
		assertEquals("score", again.get(0).getTapCommand());
	}

	@Test
	public void parse_skipsLiveValuesAndRemain() {
		String json = "[{\"id\":\"hp\",\"live_value\":80,\"live_max\":50,"
				+ "\"liveValue\":80,\"remain_sec\":12,\"remainSec\":12}]";
		ArrayList<GaugeWidget> list = GaugeWidgetsStore.parse(json);
		assertEquals(1, list.size());
		assertEquals(0.0, list.get(0).getLiveValue(), 0.0001);
		assertEquals(GaugeWidget.DEFAULT_LIVE_MAX, list.get(0).getLiveMax(), 0.0001);
		assertEquals(0.0, list.get(0).getRemainSec(), 0.0001);
		String out = GaugeWidgetsStore.toJson(list);
		assertFalse(out.contains("live_value"));
		assertFalse(out.contains("liveValue"));
		assertFalse(out.contains("live_max"));
		assertFalse(out.contains("remain_sec"));
		assertFalse(out.contains("remainSec"));
	}

	@Test
	public void durationSec_roundTrips_remainDoesNot() {
		GaugeWidget g = new GaugeWidget("stun");
		g.setShape(GaugeWidget.Shape.TIMER);
		g.setDurationSec(30);
		g.setRemainSec(12);
		String json = GaugeWidgetsStore.toJson(java.util.Collections.singletonList(g));
		GaugeWidget again = GaugeWidgetsStore.parse(json).get(0);
		assertEquals(30.0, again.getDurationSec(), 0.0001);
		assertEquals(0.0, again.getRemainSec(), 0.0001);
	}

	@Test
	public void parse_shapeAliases() {
		assertEquals(GaugeWidget.Shape.HBAR, parseOneShape("bar"));
		assertEquals(GaugeWidget.Shape.HBAR, parseOneShape("hbar"));
		assertEquals(GaugeWidget.Shape.VBAR, parseOneShape("vertical"));
		assertEquals(GaugeWidget.Shape.VBAR, parseOneShape("vbar"));
		assertEquals(GaugeWidget.Shape.RING, parseOneShape("circle"));
		assertEquals(GaugeWidget.Shape.RING, parseOneShape("pie"));
		assertEquals(GaugeWidget.Shape.RING, parseOneShape("zelda"));
		assertEquals(GaugeWidget.Shape.RING, parseOneShape("ring"));
		assertEquals(GaugeWidget.Shape.TIMER, parseOneShape("timer"));
		assertEquals(GaugeWidget.Shape.TIMER, parseOneShape("countdown"));
	}

	@Test
	public void parse_imeModeAliases() {
		assertEquals(GaugeWidget.ImeMode.STAY, parseOneIme("stay"));
		assertEquals(GaugeWidget.ImeMode.STAY, parseOneIme("game"));
		assertEquals(GaugeWidget.ImeMode.HIDE, parseOneIme("hide"));
		assertEquals(GaugeWidget.ImeMode.OVERLAY, parseOneIme("overlay"));
		assertEquals(GaugeWidget.ImeMode.OVERLAY, parseOneIme("over"));
		assertEquals(GaugeWidget.ImeMode.OVERLAY, parseOneIme("float"));
		assertEquals(GaugeWidget.ImeMode.STAY, parseOneIme("unknown"));
	}

	@Test
	public void parse_landCoordsZeroCopiesPortrait() {
		String json = "[{\"id\":\"hp\",\"x\":16,\"y\":8,\"w\":200,\"h\":24,"
				+ "\"land_x\":0,\"land_y\":0,\"land_w\":0,\"land_h\":0}]";
		GaugeWidget g = GaugeWidgetsStore.parse(json).get(0);
		assertEquals(0, g.getLandX());
		assertEquals(0, g.getLandW());
		assertEquals(16, g.resolveLandX());
		assertEquals(8, g.resolveLandY());
		assertEquals(200, g.resolveLandW());
		assertEquals(24, g.resolveLandH());
		String out = GaugeWidgetsStore.toJson(java.util.Collections.singletonList(g));
		GaugeWidget again = GaugeWidgetsStore.parse(out).get(0);
		assertEquals(0, again.getLandX());
		assertEquals(16, again.resolveLandX());
	}

	@Test
	public void parse_landOriginZeroIsRealWhenSizeSet() {
		String json = "[{\"id\":\"hp\",\"x\":16,\"y\":8,\"w\":200,\"h\":24,"
				+ "\"land_x\":0,\"land_y\":0,\"land_w\":320,\"land_h\":20}]";
		GaugeWidget g = GaugeWidgetsStore.parse(json).get(0);
		assertEquals(0, g.getLandX());
		assertEquals(0, g.getLandY());
		assertEquals(0, g.resolveLandX());
		assertEquals(0, g.resolveLandY());
		assertEquals(320, g.resolveLandW());
		assertEquals(20, g.resolveLandH());
	}

	@Test
	public void parse_landCoordsExplicitSurviveRoundTrip() {
		String json = "[{\"id\":\"hp\",\"x\":16,\"y\":8,\"w\":200,\"h\":24,"
				+ "\"land_x\":40,\"land_y\":12,\"land_w\":320,\"land_h\":20}]";
		GaugeWidget g = GaugeWidgetsStore.parse(json).get(0);
		assertEquals(40, g.getLandX());
		assertEquals(12, g.getLandY());
		assertEquals(320, g.getLandW());
		assertEquals(20, g.getLandH());
		assertEquals(40, g.resolveLandX());
		String out = GaugeWidgetsStore.toJson(java.util.Collections.singletonList(g));
		GaugeWidget again = GaugeWidgetsStore.parse(out).get(0);
		assertEquals(40, again.getLandX());
		assertEquals(320, again.getLandW());
	}

	@Test
	public void parse_timerSourceAndDuration() {
		String json = "[{\"id\":\"stun\",\"shape\":\"countdown\",\"source\":\"timer\","
				+ "\"path\":\"stunwait\",\"duration_sec\":30,"
				+ "\"remain_sec\":12,\"show_value\":false}]";
		GaugeWidget g = GaugeWidgetsStore.parse(json).get(0);
		assertEquals(GaugeWidget.Shape.TIMER, g.getShape());
		assertEquals(GaugeWidget.Source.TIMER, g.getSource());
		assertEquals("stunwait", g.getPath());
		assertEquals("stunwait", g.getTimerName());
		assertEquals("stunwait", g.resolveTimerName());
		assertEquals(30.0, g.getDurationSec(), 0.0001);
		assertEquals(0.0, g.getRemainSec(), 0.0001);
		assertFalse(g.isShowValue());
		String out = GaugeWidgetsStore.toJson(java.util.Collections.singletonList(g));
		assertTrue(out.contains("\"shape\":\"timer\""));
		assertTrue(out.contains("\"source\":\"timer\""));
		assertFalse(out.contains("remain"));
		GaugeWidget again = GaugeWidgetsStore.parse(out).get(0);
		assertEquals(30.0, again.getDurationSec(), 0.0001);
		assertEquals("timer", again.getShape().toJsonValue());
	}

	@Test
	public void parse_timerNameFillsPathWhenSourceTimer() {
		String json = "[{\"id\":\"stun\",\"source\":\"timer\",\"timer_name\":\"regen\"}]";
		GaugeWidget g = GaugeWidgetsStore.parse(json).get(0);
		assertEquals("regen", g.getPath());
		assertEquals("regen", g.getTimerName());
	}

	@Test
	public void parse_regexSourceRoundTrip() {
		GaugeWidget g = new GaugeWidget("hp");
		g.setSource(GaugeWidget.Source.REGEX);
		g.setPath("hp:\\s*([\\d.]+)");
		g.setMaxPath("maxhp:\\s*([\\d.]+)");
		g.setShowLabel(false);
		String json = GaugeWidgetsStore.toJson(java.util.Collections.singletonList(g));
		assertTrue(json.contains("\"source\":\"regex\""));
		assertFalse(json.contains("live_value"));
		GaugeWidget again = GaugeWidgetsStore.parse(json).get(0);
		assertEquals(GaugeWidget.Source.REGEX, again.getSource());
		assertEquals("hp:\\s*([\\d.]+)", again.getPath());
		assertEquals("maxhp:\\s*([\\d.]+)", again.getMaxPath());
		assertFalse(again.isShowLabel());
	}

	@Test
	public void parse_unplacedSentinelRoundTrips() {
		GaugeWidget g = new GaugeWidget("hp");
		g.setX(GaugeSpawnPlacement.UNPLACED);
		g.setY(GaugeSpawnPlacement.UNPLACED);
		assertTrue(g.isUnplaced());
		String json = GaugeWidgetsStore.toJson(java.util.Collections.singletonList(g));
		GaugeWidget again = GaugeWidgetsStore.parse(json).get(0);
		assertTrue(again.isUnplaced());
		assertEquals(GaugeSpawnPlacement.UNPLACED, again.getX());
		assertEquals(GaugeSpawnPlacement.UNPLACED, again.getY());
	}

	@Test
	public void parse_colorNamesAndHex() {
		String json = "[{\"id\":\"hp\",\"color_fill\":\"red\","
				+ "\"color_track\":\"#333\",\"color_warn\":\"#80FFAA00\"}]";
		GaugeWidget g = GaugeWidgetsStore.parse(json).get(0);
		assertEquals(0xFFFF0000, g.getColorFill());
		assertEquals(0xFF333333, g.getColorTrack());
		assertEquals(0x80FFAA00, g.getColorWarn());
		assertEquals("#FF0000", GaugeWidget.formatColor(g.getColorFill()));
		assertEquals("#80FFAA00", GaugeWidget.formatColor(g.getColorWarn()));
	}

	@Test
	public void parse_skipsReservedAndCapsAtMax() {
		StringBuilder sb = new StringBuilder("[");
		sb.append("{\"id\":\"main\"},");
		for (int i = 0; i < 15; i++) {
			if (i > 0) {
				sb.append(',');
			}
			sb.append("{\"id\":\"g").append(i).append("\"}");
		}
		sb.append(']');
		ArrayList<GaugeWidget> list = GaugeWidgetsStore.parse(sb.toString());
		assertEquals(GaugeWidgetsStore.MAX, list.size());
		assertFalse(list.isEmpty());
		assertNotNull(GaugeWidgetsStore.normalizeName(list.get(0).getId()));
	}

	@Test
	public void validate_uniqueIdsAndMax() {
		ArrayList<GaugeWidget> list = new ArrayList<GaugeWidget>();
		list.add(new GaugeWidget("hp"));
		list.add(new GaugeWidget("HP"));
		list.add(new GaugeWidget("main"));
		list.add(null);
		for (int i = 0; i < 14; i++) {
			list.add(new GaugeWidget("w" + i));
		}
		ArrayList<GaugeWidget> same = GaugeWidgetsStore.validate(list);
		assertSame(list, same);
		assertEquals(GaugeWidgetsStore.MAX, list.size());
		assertEquals("hp", list.get(0).getId());
		assertNull(GaugeWidgetsStore.find(list, "main"));
		assertNotNull(GaugeWidgetsStore.find(list, "HP"));
		assertNotNull(GaugeWidgetsStore.find(list, "w0"));
	}

	@Test
	public void find_returnsNullWhenMissing() {
		ArrayList<GaugeWidget> list = GaugeWidgetsStore.parse("[{\"id\":\"hp\"}]");
		assertNull(GaugeWidgetsStore.find(list, "mana"));
		assertNull(GaugeWidgetsStore.find(null, "hp"));
		assertNull(GaugeWidgetsStore.find(list, "main"));
	}

	@Test
	public void opacityAndWarnPct_clamped() {
		GaugeWidget g = new GaugeWidget("hp");
		g.setOpacity(5);
		assertEquals(10, g.getOpacity());
		g.setOpacity(200);
		assertEquals(100, g.getOpacity());
		g.setWarnPct(-4);
		assertEquals(0, g.getWarnPct());
		g.setWarnPct(150);
		assertEquals(100, g.getWarnPct());
		assertEquals(25, new GaugeWidget("mana").getWarnPct());
		assertTrue(new GaugeWidget("mana").isShowValue());
	}

	@Test
	public void copy_includesTransientTimerState() {
		GaugeWidget g = new GaugeWidget("stun");
		g.setShape(GaugeWidget.Shape.TIMER);
		g.setDurationSec(30);
		g.setRemainSec(12);
		g.setLiveValue(80);
		g.setImeMode(GaugeWidget.ImeMode.OVERLAY);
		GaugeWidget c = g.copy();
		assertEquals(12.0, c.getRemainSec(), 0.0001);
		assertEquals(30.0, c.getDurationSec(), 0.0001);
		assertEquals(80.0, c.getLiveValue(), 0.0001);
		assertEquals(GaugeWidget.ImeMode.OVERLAY, c.getImeMode());
		assertEquals(0.4, c.ratio(), 0.0001);
	}

	@Test
	public void settingKeys() {
		assertEquals("gauge_widgets", GaugeWidgetsStore.SETTING_KEY);
		assertEquals("gauge_widgets_enabled", GaugeWidgetsStore.ENABLED_KEY);
		assertEquals(12, GaugeWidgetsStore.MAX);
	}

	@Test
	public void fromJson_nullWithoutId() throws Exception {
		assertNull(GaugeWidget.fromJson(null));
		assertNull(GaugeWidget.fromJson(new JSONObject()));
	}

	private static GaugeWidget.Shape parseOneShape(String shape) {
		return GaugeWidgetsStore.parse("[{\"id\":\"hp\",\"shape\":\"" + shape + "\"}]")
				.get(0).getShape();
	}

	private static GaugeWidget.ImeMode parseOneIme(String ime) {
		return GaugeWidgetsStore.parse("[{\"id\":\"hp\",\"ime_mode\":\"" + ime + "\"}]")
				.get(0).getImeMode();
	}
}
