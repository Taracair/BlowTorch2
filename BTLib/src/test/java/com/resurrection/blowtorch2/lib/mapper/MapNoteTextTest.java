package com.resurrection.blowtorch2.lib.mapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MapNoteTextTest {

	@Test
	public void encodeReplacesLineBreaksAndDecodePutsThemBack() {
		String encoded = MapNoteText.encode("first\r\nsecond\rthird");
		assertFalse(encoded.contains("\n"));
		assertFalse(encoded.contains("\r"));
		assertEquals("first\nsecond\nthird", MapNoteText.decode(encoded));
	}

	@Test
	public void standInIsAsciiSoALatinEncodingKeepsIt() {
		String encoded = MapNoteText.encode("first\nsecond");
		assertEquals("first{{NL}}second", encoded);
		for (int i = 0; i < encoded.length(); i++) {
			assertTrue(encoded.charAt(i) >= 0x21 && encoded.charAt(i) <= 0x7E);
		}
	}

	@Test
	public void plainTextIsUnchanged() {
		assertEquals("one line", MapNoteText.decode(MapNoteText.encode("one line")));
		assertEquals("", MapNoteText.encode(null));
		assertEquals("", MapNoteText.decode(null));
	}
}
