package com.resurrection.blowtorch2.lib.responder.color;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

import com.resurrection.blowtorch2.lib.window.TextTree;

/**
 * New paint modes dump as CSI the window already understands. KEEP must not
 * emit SGR 49 (that is RESET / the old 0-16-231 sentinels).
 */
public class ColorActionPaintTest {

	private static final String ESC = "\u001B";
	private static final String ENC = "ISO-8859-1";

	@Test
	public void truecolorForegroundDumps38_2() throws Exception {
		String dumped = paint("fox", rgbKeep("#aabbcc"));
		assertTrue(visible(dumped), dumped.contains(ESC + "[38;2;170;187;204m"));
		assertFalse(visible(dumped), dumped.contains(ESC + "[49m"));
		assertFalse(visible(dumped), dumped.contains(ESC + "[48;5;"));
	}

	@Test
	public void sentinelBackgroundStillEmits49() throws Exception {
		ColorAction action = new ColorAction();
		action.setColor(45);
		action.setBackgroundColor(16);
		String dumped = paint("fox", action);
		assertTrue(visible(dumped), dumped.contains(ESC + "[38;5;45m"));
		assertTrue(visible(dumped), dumped.contains(ESC + "[49m"));
	}

	@Test
	public void keepKeepDoesNotInjectDefaultForeground() throws Exception {
		TriggerColorPaint spec = TriggerColorPaint.legacyDefaults();
		spec.setForegroundKeep();
		spec.setBackgroundKeep();
		ColorAction action = new ColorAction();
		action.setPaint(spec);
		String dumped = paint("fox", action);
		assertFalse("KEEP/KEEP must not reset fg: " + visible(dumped),
				dumped.contains(ESC + "[39m"));
	}

	@Test
	public void boldUnderlineDumpAndOff() throws Exception {
		TriggerColorPaint spec = TriggerColorPaint.legacyDefaults();
		spec.setForegroundXterm(45);
		spec.setBackgroundKeep();
		spec.setStyle(TriggerColorPaint.STYLE_BOLD, true);
		spec.setStyle(TriggerColorPaint.STYLE_UNDERLINE, true);
		ColorAction action = new ColorAction();
		action.setPaint(spec);
		String dumped = paint("fox", action);
		assertTrue("bold+underline unit: " + visible(dumped),
				dumped.contains(ESC + "[66;4m"));
		assertTrue("style must close: " + visible(dumped),
				dumped.contains("67") && dumped.contains("24"));
		assertFalse("bold off must not emit 22: " + visible(dumped),
				dumped.contains(ESC + "[22") || dumped.contains(";22m")
						|| dumped.contains(";22;"));
	}

	private static ColorAction rgbKeep(String hex) {
		TriggerColorPaint spec = TriggerColorPaint.fromLua(hex, Boolean.FALSE);
		ColorAction action = new ColorAction();
		action.setPaint(spec);
		return action;
	}

	private static String paint(String text, ColorAction action)
			throws Exception {
		TextTree working = new TextTree();
		working.setModCount(0);
		working.addBytesImpl((text + "\n").getBytes(ENC));
		TextTree.Line line = working.getLines().get(0);
		Matcher m = Pattern.compile(text).matcher(
				TextTree.deColorLine(line).toString());
		assertTrue(m.find());
		action.doResponse(null, working, 0, null, line, m.start(), m.end() - 2,
				m.group(), null, "test", "host", 0, 0, false, null, null, null,
				"c", ENC);
		return new String(working.dumpToBytes(false), ENC);
	}

	private static String visible(String s) {
		return s.replace(ESC, "<ESC>").replace("\n", "\\n");
	}
}
