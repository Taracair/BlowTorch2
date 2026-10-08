package com.resurrection.blowtorch2.lib.service.function;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LastListRequestTest {

	@Test
	public void noArgumentOpensTheWindow() {
		assertEquals(LastListRequest.SHOW, LastListRequest.parse(null));
		assertEquals(LastListRequest.SHOW, LastListRequest.parse(""));
		assertEquals(LastListRequest.SHOW, LastListRequest.parse("   "));
	}

	@Test
	public void frameAloneToggles() {
		assertEquals(LastListRequest.FRAME_TOGGLE, LastListRequest.parse("frame"));
		assertEquals(LastListRequest.FRAME_TOGGLE, LastListRequest.parse("  Frame  "));
		assertEquals(LastListRequest.FRAME_TOGGLE, LastListRequest.parse("FRAME"));
	}

	@Test
	public void frameOnAndOffIgnoreCaseAndExtraSpace() {
		assertEquals(LastListRequest.FRAME_ON, LastListRequest.parse("frame on"));
		assertEquals(LastListRequest.FRAME_OFF, LastListRequest.parse(" frame off "));
		assertEquals(LastListRequest.FRAME_ON, LastListRequest.parse("Frame on"));
		assertEquals(LastListRequest.FRAME_OFF, LastListRequest.parse("frame  off"));
		assertEquals(LastListRequest.FRAME_OFF, LastListRequest.parse("FRAME   OFF"));
		assertEquals(LastListRequest.FRAME_ON, LastListRequest.parse("  frame   On  "));
	}

	@Test
	public void listFloatAndBarArePlaces() {
		assertEquals(LastListRequest.SHOW, LastListRequest.parse("list"));
		assertEquals(LastListRequest.SHOW, LastListRequest.parse("List"));
		assertEquals(LastListRequest.FLOAT, LastListRequest.parse("float"));
		assertEquals(LastListRequest.FLOAT, LastListRequest.parse("FLOAT"));
		assertEquals(LastListRequest.FLOAT, LastListRequest.parse("floating"));
		assertEquals(LastListRequest.BAR, LastListRequest.parse("bar"));
		assertEquals(LastListRequest.BAR, LastListRequest.parse(" Bar "));
	}

	@Test
	public void floatOffOnAndToggle() {
		assertEquals(LastListRequest.FLOAT, LastListRequest.parse("float on"));
		assertEquals(LastListRequest.FLOAT, LastListRequest.parse("floating on"));
		assertEquals(LastListRequest.FLOAT_OFF, LastListRequest.parse("float off"));
		assertEquals(LastListRequest.FLOAT_OFF, LastListRequest.parse(" floating  off "));
		assertEquals(LastListRequest.FLOAT_OFF, LastListRequest.parse("FLOAT OFF"));
		assertEquals(LastListRequest.FLOAT_TOGGLE, LastListRequest.parse("float toggle"));
		assertEquals(LastListRequest.FLOAT_TOGGLE, LastListRequest.parse("Floating Toggle"));
		assertEquals(LastListRequest.MISUSE, LastListRequest.parse("float maybe"));
		assertEquals(LastListRequest.MISUSE, LastListRequest.parse("float off extra"));
		assertEquals(LastListRequest.MISUSE, LastListRequest.parse("off"));
	}

	@Test
	public void lastfloatIsFloatPlusTheRest() {
		assertEquals("float", LastListRequest.normalize("lastfloat", null));
		assertEquals("float", LastListRequest.normalize("lastfloat", "  "));
		assertEquals("float off", LastListRequest.normalize("lastfloat", "off"));
		assertEquals("float off", LastListRequest.normalize("lastfloat", " off "));
		assertEquals("float toggle", LastListRequest.normalize("lastfloat", "toggle"));
		assertEquals("off", LastListRequest.normalize("lastlist", "off"));
		assertEquals(LastListRequest.FLOAT_OFF,
				LastListRequest.parse(LastListRequest.normalize("lastfloat", "off")));
	}

	@Test
	public void floatCountAndLengthMatchTheBarRanges() {
		LastListRequest.Parsed five = LastListRequest.read("float 5");
		assertEquals(LastListRequest.FLOAT_COUNT, five.kind);
		assertEquals(5, five.number);
		assertEquals(LastListRequest.FLOAT_COUNT, LastListRequest.parse("floating 1"));
		assertEquals(LastListRequest.FLOAT_COUNT, LastListRequest.parse("FLOAT 100"));
		assertEquals(LastListRequest.MISUSE, LastListRequest.parse("float 0"));
		assertEquals(LastListRequest.MISUSE, LastListRequest.parse("float 101"));
		LastListRequest.Parsed length = LastListRequest.read("float length 12");
		assertEquals(LastListRequest.FLOAT_LENGTH, length.kind);
		assertEquals(12, length.number);
		assertEquals(LastListRequest.FLOAT_LENGTH, LastListRequest.parse("floating length 3"));
		assertEquals(LastListRequest.FLOAT_LENGTH, LastListRequest.parse("Float  Length  40"));
		assertEquals(LastListRequest.MISUSE, LastListRequest.parse("float length 2"));
		assertEquals(LastListRequest.MISUSE, LastListRequest.parse("float length 41"));
		assertEquals(LastListRequest.MISUSE, LastListRequest.parse("float length"));
		assertEquals(LastListRequest.MISUSE, LastListRequest.parse("length 12"));
		assertEquals(5, LastListRequest.read(LastListRequest.normalize("lastfloat", "5")).number);
		assertEquals(LastListRequest.FLOAT_LENGTH,
				LastListRequest.parse(LastListRequest.normalize("lastfloat", "length 12")));
	}

	@Test
	public void anythingElseIsLocalUsage() {
		assertEquals(LastListRequest.MISUSE, LastListRequest.parse("frame on extra"));
		assertEquals(LastListRequest.MISUSE, LastListRequest.parse("frame toggle"));
		assertEquals(LastListRequest.MISUSE, LastListRequest.parse("frames"));
		assertEquals(LastListRequest.MISUSE, LastListRequest.parse("on"));
		assertEquals(LastListRequest.MISUSE, LastListRequest.parse("float extra"));
		assertEquals(LastListRequest.MISUSE, LastListRequest.parse("bar on"));
	}
}
