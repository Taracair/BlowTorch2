package com.resurrection.blowtorch2.lib.trigger.style;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.resurrection.blowtorch2.lib.trigger.style.StyleSnapshot.ColorSpace;

public class StyleColorTokenTest {

	@Test
	public void parsesAnsiXtermRgbAndBareInt() {
		StyleColorToken ansi = StyleColorToken.parse("ansi:32");
		assertEquals(ColorSpace.ANSI16, ansi.space);
		assertEquals(32, ansi.code);
		StyleColorToken bare = StyleColorToken.parse("32");
		assertEquals(ColorSpace.ANSI16, bare.space);
		assertEquals(32, bare.code);
		StyleColorToken xterm = StyleColorToken.parse("xterm:208");
		assertEquals(ColorSpace.XTERM256, xterm.space);
		assertEquals(208, xterm.code);
		StyleColorToken rgb = StyleColorToken.parse("rgb:#ff8700");
		assertEquals(ColorSpace.RGB, rgb.space);
		assertEquals(0xff8700, rgb.code);
	}

	@Test
	public void formatRoundTrip() {
		assertEquals("ansi:32", StyleColorToken.format(ColorSpace.ANSI16, 32));
		assertEquals("xterm:208", StyleColorToken.format(ColorSpace.XTERM256, 208));
		assertEquals("rgb:#ff8700", StyleColorToken.format(ColorSpace.RGB, 0xff8700));
		assertEquals("ansi:32", StyleColorToken.parse("ansi:32").toString());
	}

	@Test
	public void rejectBlank() {
		assertNull(StyleColorToken.parse(""));
		assertNull(StyleColorToken.parse("rgb:gg"));
	}

	@Test
	public void describeCheckedOnlyTickedRows() {
		StyleSnapshot s = new StyleSnapshot(ColorSpace.ANSI16, 32, ColorSpace.ANSI16,
				40, false, 0, null);
		boolean[] checked = new boolean[] { true, false };
		String body = StyleClipboard.describeChecked(s, "loot", checked, false);
		assertTrue(body.contains("Foreground"));
		assertTrue(!body.contains("Background"));
		assertTrue(body.contains("exact recipe"));
	}
}
