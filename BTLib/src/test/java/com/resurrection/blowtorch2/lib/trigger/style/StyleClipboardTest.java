package com.resurrection.blowtorch2.lib.trigger.style;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import com.resurrection.blowtorch2.lib.service.SgrStyle;
import com.resurrection.blowtorch2.lib.trigger.style.StyleClipboard.LayerRow;
import com.resurrection.blowtorch2.lib.trigger.style.StyleSnapshot.ColorSpace;

public class StyleClipboardTest {

	@Test
	public void plainGlyphOmitsOffFlagsAndDefaultPaper() {
		StyleSnapshot s = new StyleSnapshot(ColorSpace.ANSI16, 32, ColorSpace.ANSI16,
				40, false, 0, null);
		assertEquals(Arrays.asList("fg", "text"),
				ids(StyleClipboard.layers(s, "loot")));
	}

	@Test
	public void underlineShowsOnlyWhenTheRunHasIt() {
		StyleSnapshot off = new StyleSnapshot(ColorSpace.ANSI16, 32,
				ColorSpace.ANSI16, 40, false, 0, null);
		StyleSnapshot on = new StyleSnapshot(ColorSpace.ANSI16, 32,
				ColorSpace.ANSI16, 40, false, SgrStyle.UNDERLINE, null);
		assertFalse(ids(StyleClipboard.layers(off, "a")).contains("underline"));
		assertTrue(ids(StyleClipboard.layers(on, "a")).contains("underline"));
	}

	@Test
	public void paintedBackgroundAndLinkAppear() {
		StyleSnapshot s = new StyleSnapshot(ColorSpace.XTERM256, 208,
				ColorSpace.XTERM256, 52, false, 0, "https://example.org/");
		assertEquals(Arrays.asList("fg", "bg", "href"),
				ids(StyleClipboard.layers(s, "")));
	}

	@Test
	public void brightAndBoldStayWhenOn() {
		StyleSnapshot s = new StyleSnapshot(ColorSpace.ANSI16, 31, ColorSpace.ANSI16,
				40, true, SgrStyle.WEIGHT, null);
		assertEquals(Arrays.asList("fg", "bright", "weight"),
				ids(StyleClipboard.layers(s, "")));
	}

	private static List<String> ids(final List<LayerRow> rows) {
		ArrayList<String> out = new ArrayList<String>();
		for (int i = 0; i < rows.size(); i++) {
			out.add(rows.get(i).id);
		}
		return out;
	}
}
