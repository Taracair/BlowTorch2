package com.resurrection.blowtorch2.lib.responder.color;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.resurrection.blowtorch2.lib.service.plugin.settings.SettingsOptionXmlTest;

/**
 * ColorAction XML emit on the JVM. Load still needs android.sax
 * ({@code ColorElementListener}); round-trip here is format +
 * {@link TriggerColorPaint#fromXml}.
 */
public class ColorActionXmlTest {

	@Test
	public void legacyXterm36And75OmitModeStylesAndHex() throws Exception {
		ColorAction action = new ColorAction();
		action.setColor(36);
		action.setBackgroundColor(75);
		String xml = emit(action);

		assertTrue(xml.contains("<color"));
		assertEquals("36", attr(xml, "text"));
		assertEquals("75", attr(xml, "background"));
		assertNull(attr(xml, "backgroundMode"));
		assertNull(attr(xml, "bold"));
		assertFalse(xml.contains("#"));

		TriggerColorPaint loaded = fromEmitted(xml);
		assertEquals(TriggerColorPaint.FgMode.XTERM, loaded.getFgMode());
		assertEquals(36, loaded.getFgXterm());
		assertEquals(TriggerColorPaint.BgMode.XTERM, loaded.getBgMode());
		assertEquals(75, loaded.getBgXterm());
		assertEquals(0, loaded.getStyles());
		assertEquals(loaded, TriggerColorPaint.fromXml(
				"36", null, "75", null,
				false, false, false, false, false, false));
	}

	@Test
	public void sentinelBackgroundViaSetBackgroundColorOmitsMode()
			throws Exception {
		ColorAction action = new ColorAction();
		action.setColor(45);
		action.setBackgroundColor(16);
		String xml = emit(action);

		assertEquals("16", attr(xml, "background"));
		assertNull(attr(xml, "backgroundMode"));

		TriggerColorPaint loaded = fromEmitted(xml);
		assertEquals(TriggerColorPaint.BgMode.RESET, loaded.getBgMode());
		assertTrue(loaded.resetsBackground());
		assertEquals(loaded, TriggerColorPaint.fromXml(
				"45", null, "16", null,
				false, false, false, false, false, false));
	}

	@Test
	public void literalXterm16EmitsBackgroundModeXterm() throws Exception {
		TriggerColorPaint spec = TriggerColorPaint.legacyDefaults();
		spec.setForegroundXterm(1);
		spec.setBackgroundXterm(16);
		ColorAction action = new ColorAction();
		action.setPaint(spec);
		String xml = emit(action);

		assertEquals("16", attr(xml, "background"));
		assertEquals("xterm", attr(xml, "backgroundMode"));

		TriggerColorPaint loaded = fromEmitted(xml);
		assertEquals(TriggerColorPaint.BgMode.XTERM, loaded.getBgMode());
		assertEquals(16, loaded.getBgXterm());
		assertEquals(loaded, TriggerColorPaint.fromXml(
				"1", null, "16", "xterm",
				false, false, false, false, false, false));
	}

	@Test
	public void rgbAndKeepEmitHexAndKeepToken() throws Exception {
		ColorAction action = new ColorAction();
		action.setPaint(TriggerColorPaint.fromLua("#ff00aa", Boolean.FALSE));
		String xml = emit(action);

		assertEquals("#FF00AA", attr(xml, "text"));
		assertEquals("keep", attr(xml, "background"));
		assertNull(attr(xml, "backgroundMode"));

		TriggerColorPaint loaded = fromEmitted(xml);
		assertEquals(TriggerColorPaint.FgMode.RGB, loaded.getFgMode());
		assertEquals(0xFF00AA, loaded.getFgRgb());
		assertTrue(loaded.keepsBackground());
		assertEquals(loaded, TriggerColorPaint.fromXml(
				"#FF00AA", null, "keep", null,
				false, false, false, false, false, false));
	}

	@Test
	public void boldAndUnderlineOmitUncheckedStyleAttrs() throws Exception {
		TriggerColorPaint spec = TriggerColorPaint.fromLegacyInts(36, 75);
		spec.setStyle(TriggerColorPaint.STYLE_BOLD, true);
		spec.setStyle(TriggerColorPaint.STYLE_UNDERLINE, true);
		ColorAction action = new ColorAction();
		action.setPaint(spec);
		String xml = emit(action);

		assertEquals("true", attr(xml, "bold"));
		assertEquals("true", attr(xml, "underline"));
		assertNull(attr(xml, "faint"));
		assertNull(attr(xml, "italic"));
		assertNull(attr(xml, "reverse"));
		assertNull(attr(xml, "strike"));

		TriggerColorPaint loaded = fromEmitted(xml);
		assertTrue(loaded.hasStyle(TriggerColorPaint.STYLE_BOLD));
		assertTrue(loaded.hasStyle(TriggerColorPaint.STYLE_UNDERLINE));
		assertFalse(loaded.hasStyle(TriggerColorPaint.STYLE_FAINT));
		assertFalse(loaded.hasStyle(TriggerColorPaint.STYLE_ITALIC));
		assertFalse(loaded.hasStyle(TriggerColorPaint.STYLE_REVERSE));
		assertFalse(loaded.hasStyle(TriggerColorPaint.STYLE_STRIKE));
		assertEquals(loaded, TriggerColorPaint.fromXml(
				"36", null, "75", null,
				true, false, false, true, false, false));
	}

	private static String emit(ColorAction action) throws Exception {
		SettingsOptionXmlTest.RecordingXmlSerializer out =
				new SettingsOptionXmlTest.RecordingXmlSerializer();
		ColorActionParser.saveColorActionToXML(out, action);
		return out.toString();
	}

	private static TriggerColorPaint fromEmitted(String xml) {
		return TriggerColorPaint.fromXml(
				attr(xml, "text"),
				attr(xml, "textMode"),
				attr(xml, "background"),
				attr(xml, "backgroundMode"),
				"true".equals(attr(xml, "bold")),
				"true".equals(attr(xml, "faint")),
				"true".equals(attr(xml, "italic")),
				"true".equals(attr(xml, "underline")),
				"true".equals(attr(xml, "reverse")),
				"true".equals(attr(xml, "strike")));
	}

	private static String attr(String xml, String name) {
		String needle = " " + name + "=\"";
		int i = xml.indexOf(needle);
		if (i < 0) {
			return null;
		}
		int start = i + needle.length();
		int end = xml.indexOf('"', start);
		if (end < 0) {
			return null;
		}
		return xml.substring(start, end);
	}
}
