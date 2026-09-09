package com.resurrection.blowtorch2.lib.ping;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PingCommandParserTest {

	@Test
	public void blankIsStatus() {
		assertEquals(PingCommandParser.ACTION_STATUS, PingCommandParser.parse("").action);
		assertEquals(PingCommandParser.ACTION_STATUS, PingCommandParser.parse(null).action);
		assertEquals(PingCommandParser.ACTION_STATUS, PingCommandParser.parse("  ").action);
	}

	@Test
	public void showAndOn() {
		assertEquals(PingCommandParser.ACTION_SHOW, PingCommandParser.parse("show").action);
		assertEquals(PingCommandParser.ACTION_SHOW, PingCommandParser.parse("on").action);
	}

	@Test
	public void hideAndOff() {
		assertEquals(PingCommandParser.ACTION_HIDE, PingCommandParser.parse("hide").action);
		assertEquals(PingCommandParser.ACTION_HIDE, PingCommandParser.parse("off").action);
	}

	@Test
	public void toggle() {
		assertEquals(PingCommandParser.ACTION_TOGGLE, PingCommandParser.parse("toggle").action);
	}

	@Test
	public void opacityClamps() {
		assertEquals(Integer.valueOf(15), PingCommandParser.parse("opacity 1").opacity);
		assertEquals(Integer.valueOf(100), PingCommandParser.parse("opacity 999").opacity);
		assertEquals(Integer.valueOf(40), PingCommandParser.parse("opacity 40").opacity);
	}

	@Test
	public void sizeClamps() {
		assertEquals(Integer.valueOf(12), PingCommandParser.parse("size 3").size);
		assertEquals(Integer.valueOf(36), PingCommandParser.parse("size 99").size);
	}

	@Test
	public void posClamps() {
		PingCommandParser.Result r = PingCommandParser.parse("pos -4 140");
		assertEquals(PingCommandParser.ACTION_POS, r.action);
		assertEquals(Integer.valueOf(0), r.x);
		assertEquals(Integer.valueOf(100), r.y);
	}

	@Test
	public void unknownIsUsage() {
		PingCommandParser.Result r = PingCommandParser.parse("banana");
		assertNull(r.action);
		assertTrue(r.error.contains("Usage:"));
	}

	@Test
	public void extraTokensOnShowFail() {
		assertTrue(PingCommandParser.parse("show now").error.contains("Usage:"));
	}
}
