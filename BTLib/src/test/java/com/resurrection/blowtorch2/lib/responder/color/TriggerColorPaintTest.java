package com.resurrection.blowtorch2.lib.responder.color;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class TriggerColorPaintTest {

	@Test
	public void legacyIntsKeepXtermAndSentinelReset() {
		TriggerColorPaint p = TriggerColorPaint.fromLegacyInts(36, 75);
		assertEquals(TriggerColorPaint.FgMode.XTERM, p.getFgMode());
		assertEquals(36, p.getFgXterm());
		assertEquals(TriggerColorPaint.BgMode.XTERM, p.getBgMode());
		assertEquals(75, p.getBgXterm());
		assertTrue(p.canEmitLegacyInts());

		TriggerColorPaint sentinel = TriggerColorPaint.fromLegacyInts(45, 16);
		assertEquals(TriggerColorPaint.BgMode.RESET, sentinel.getBgMode());
		assertTrue(sentinel.resetsBackground());
		assertFalse(sentinel.paintsBackground());
		assertEquals(16, sentinel.legacyBackgroundInt());
		assertTrue(sentinel.canEmitLegacyInts());
		assertEquals("45", sentinel.formatTextAttr());
		assertEquals("16", sentinel.formatBackgroundAttr());
	}

	@Test
	public void literalXtermSixteenNeedsModeAttr() {
		TriggerColorPaint p = TriggerColorPaint.legacyDefaults();
		p.setForegroundXterm(15);
		p.setBackgroundXterm(16);
		assertTrue(p.needsBackgroundModeXtermAttr());
		assertEquals("xterm", p.formatBackgroundModeAttr());
		assertEquals("16", p.formatBackgroundAttr());
		assertFalse(p.canEmitLegacyInts());
	}

	@Test
	public void xmlRoundTripLegacyAndNew() {
		TriggerColorPaint old = TriggerColorPaint.fromXml(
				"36", null, "75", null,
				false, false, false, false, false, false);
		assertEquals(36, old.getFgXterm());
		assertEquals(75, old.getBgXterm());

		TriggerColorPaint sent = TriggerColorPaint.fromXml(
				"45", null, "0", null,
				false, false, false, false, false, false);
		assertEquals(TriggerColorPaint.BgMode.RESET, sent.getBgMode());

		TriggerColorPaint keep = TriggerColorPaint.fromXml(
				"#ff00aa", null, "keep", null,
				true, false, false, true, false, false);
		assertEquals(TriggerColorPaint.FgMode.RGB, keep.getFgMode());
		assertEquals(0xFF00AA, keep.getFgRgb());
		assertTrue(keep.keepsBackground());
		assertTrue(keep.hasStyle(TriggerColorPaint.STYLE_BOLD));
		assertTrue(keep.hasStyle(TriggerColorPaint.STYLE_UNDERLINE));

		TriggerColorPaint literal = TriggerColorPaint.fromXml(
				"1", null, "16", "xterm",
				false, false, false, false, false, false);
		assertEquals(TriggerColorPaint.BgMode.XTERM, literal.getBgMode());
		assertEquals(16, literal.getBgXterm());

		TriggerColorPaint defBg = TriggerColorPaint.fromXml(
				"keep", null, "default", null,
				false, false, false, false, false, false);
		assertEquals(TriggerColorPaint.FgMode.KEEP, defBg.getFgMode());
		assertEquals(TriggerColorPaint.BgMode.RESET, defBg.getBgMode());
	}

	@Test
	public void xmlDoesNotThrowOnJunk() {
		TriggerColorPaint p = TriggerColorPaint.fromXml(
				"not-a-color", null, "???", null,
				false, false, false, false, false, false);
		assertEquals(TriggerColorPaint.FgMode.XTERM, p.getFgMode());
		assertEquals(TriggerColorPaint.DEFAULT_FG_XTERM, p.getFgXterm());
	}

	@Test
	public void sgrXtermRgbResetAndStyles() {
		TriggerColorPaint x = TriggerColorPaint.fromLegacyInts(45, 232);
		assertEquals(Arrays.asList(38, 5, 45, 48, 5, 232), ints(x.toSgrOps()));

		TriggerColorPaint reset = TriggerColorPaint.fromLegacyInts(45, 16);
		assertEquals(Arrays.asList(38, 5, 45, 49), ints(reset.toSgrOps()));

		TriggerColorPaint rgb = TriggerColorPaint.legacyDefaults();
		rgb.setForegroundRgb(0xAABBCC);
		rgb.setBackgroundKeep();
		assertEquals(Arrays.asList(38, 2, 0xAA, 0xBB, 0xCC), ints(rgb.toSgrOps()));

		TriggerColorPaint styled = TriggerColorPaint.legacyDefaults();
		styled.setForegroundKeep();
		styled.setBackgroundKeep();
		styled.setStyle(TriggerColorPaint.STYLE_BOLD, true);
		styled.setStyle(TriggerColorPaint.STYLE_ITALIC, true);
		styled.setStyle(TriggerColorPaint.STYLE_UNDERLINE, true);
		styled.setStyle(TriggerColorPaint.STYLE_STRIKE, true);
		styled.setStyle(TriggerColorPaint.STYLE_REVERSE, true);
		styled.setStyle(TriggerColorPaint.STYLE_FAINT, true);
		assertEquals(Arrays.asList(66, 2, 3, 4, 7, 9), ints(styled.toSgrOps()));
		assertEquals(Arrays.asList(67, 22, 23, 24, 27, 29), ints(styled.toStyleOffOps()));
	}

	@Test
	public void boldOnlyUsesPrivateWeightCodesNotBright() {
		TriggerColorPaint bold = TriggerColorPaint.legacyDefaults();
		bold.setForegroundKeep();
		bold.setBackgroundKeep();
		bold.setStyle(TriggerColorPaint.STYLE_BOLD, true);
		assertEquals(Arrays.asList(66), ints(bold.toStyleOnOps()));
		assertEquals(Arrays.asList(67), ints(bold.toStyleOffOps()));
		assertFalse(ints(bold.toStyleOnOps()).contains(Integer.valueOf(1)));
		assertFalse(ints(bold.toStyleOffOps()).contains(Integer.valueOf(1)));
		assertFalse(ints(bold.toStyleOffOps()).contains(Integer.valueOf(22)));
	}

	@Test
	public void luaNumberFalseHexAndTable() {
		TriggerColorPaint n = TriggerColorPaint.fromLua(Double.valueOf(36),
				Double.valueOf(75));
		assertEquals(36, n.getFgXterm());
		assertEquals(75, n.getBgXterm());

		TriggerColorPaint sent = TriggerColorPaint.fromLua(Double.valueOf(10),
				Double.valueOf(231));
		assertTrue(sent.resetsBackground());

		TriggerColorPaint keep = TriggerColorPaint.fromLua(Boolean.FALSE,
				Boolean.FALSE);
		assertEquals(TriggerColorPaint.FgMode.KEEP, keep.getFgMode());
		assertTrue(keep.keepsBackground());

		TriggerColorPaint hex = TriggerColorPaint.fromLua("#00ff88", "keep");
		assertEquals(0x00FF88, hex.getFgRgb());
		assertTrue(hex.keepsBackground());

		Map<String, Object> t = new HashMap<String, Object>();
		t.put("xterm", Double.valueOf(16));
		TriggerColorPaint table = TriggerColorPaint.fromLua(null, t);
		assertEquals(TriggerColorPaint.BgMode.XTERM, table.getBgMode());
		assertEquals(16, table.getBgXterm());

		Map<String, Object> rgb = new HashMap<String, Object>();
		rgb.put("r", Double.valueOf(1));
		rgb.put("g", Double.valueOf(2));
		rgb.put("b", Double.valueOf(3));
		TriggerColorPaint rgbT = TriggerColorPaint.fromLua(rgb, "default");
		assertEquals(0x010203, rgbT.getFgRgb());
		assertTrue(rgbT.resetsBackground());
	}

	@Test
	public void parseHexShortAndAlpha() {
		assertEquals(Integer.valueOf(0xFF00AA),
				TriggerColorPaint.parseHexRgb("#f0a"));
		assertEquals(Integer.valueOf(0xAABBCC),
				TriggerColorPaint.parseHexRgb("#AABBCC"));
		assertEquals(Integer.valueOf(0x112233),
				TriggerColorPaint.parseHexRgb("#FF112233"));
		assertEquals("#AABBCC", TriggerColorPaint.formatHex(0xAABBCC));
	}

	@Test
	public void copyEqualsAndSummary() {
		TriggerColorPaint a = TriggerColorPaint.fromLua("#ff0000", false);
		TriggerColorPaint.applyLuaStyles(a, Boolean.TRUE, null, null,
				Boolean.TRUE, null, null);
		TriggerColorPaint b = a.copy();
		assertEquals(a, b);
		b.setStyle(TriggerColorPaint.STYLE_ITALIC, true);
		assertFalse(a.equals(b));
		assertTrue(a.summary().contains("#FF0000"));
		assertTrue(a.summary().contains("bg keep"));
		assertTrue(a.summary().contains("bold"));
	}

	private static List<Integer> ints(List<Integer> ops) {
		return ops;
	}
}
