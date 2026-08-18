package com.resurrection.blowtorch2.lib.gauge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WidgetCommandParserTest {

	@Test
	public void emptyArgIsHelp() {
		assertHelp(WidgetCommandParser.parse(null));
		assertHelp(WidgetCommandParser.parse(""));
		assertHelp(WidgetCommandParser.parse("   "));
		assertHelp(WidgetCommandParser.parse(".widget"));
		assertHelp(WidgetCommandParser.parse("help"));
		assertHelp(WidgetCommandParser.parse("?"));
	}

	@Test
	public void list() {
		WidgetCommandParser.Result r = WidgetCommandParser.parse("list");
		assertOk(r, WidgetCommandParser.ACTION_LIST);
		assertNull(r.id);
		r = WidgetCommandParser.parse(".widget list");
		assertOk(r, WidgetCommandParser.ACTION_LIST);
	}

	@Test
	public void addDefaultsToHbar() {
		WidgetCommandParser.Result r = WidgetCommandParser.parse("add hp");
		assertOk(r, WidgetCommandParser.ACTION_ADD);
		assertEquals("hp", r.id);
		assertEquals(WidgetCommandParser.SHAPE_HBAR, r.shape);
	}

	@Test
	public void addAcceptsShapeAliases() {
		assertEquals(WidgetCommandParser.SHAPE_RING,
				ok("add hp ring").shape);
		assertEquals(WidgetCommandParser.SHAPE_HBAR,
				ok("add hp hbar").shape);
		assertEquals(WidgetCommandParser.SHAPE_VBAR,
				ok("add hp vbar").shape);
		assertEquals(WidgetCommandParser.SHAPE_HBAR,
				ok("add hp bar").shape);
		assertEquals(WidgetCommandParser.SHAPE_VBAR,
				ok("add hp vertical").shape);
		assertEquals(WidgetCommandParser.SHAPE_RING,
				ok("add hp circle").shape);
		assertEquals(WidgetCommandParser.SHAPE_RING,
				ok("ADD HP RING").shape);
		assertEquals(WidgetCommandParser.SHAPE_TIMER,
				ok("add stun timer").shape);
		assertEquals(WidgetCommandParser.SHAPE_TIMER,
				ok("add stun countdown").shape);
	}

	@Test
	public void removeAliases() {
		assertEquals("hp", ok("remove hp").id);
		assertEquals(WidgetCommandParser.ACTION_REMOVE, ok("delete hp").action);
		assertEquals(WidgetCommandParser.ACTION_REMOVE, ok("rm hp").action);
	}

	@Test
	public void showAndHide() {
		WidgetCommandParser.Result show = ok("show hp");
		assertEquals(WidgetCommandParser.ACTION_SHOW, show.action);
		assertEquals(Boolean.TRUE, show.flag);
		WidgetCommandParser.Result hide = ok("hide hp");
		assertEquals(WidgetCommandParser.ACTION_HIDE, hide.action);
		assertEquals(Boolean.FALSE, hide.flag);
	}

	@Test
	public void shape() {
		WidgetCommandParser.Result r = ok("shape hp ring");
		assertEquals(WidgetCommandParser.ACTION_SHAPE, r.action);
		assertEquals(WidgetCommandParser.SHAPE_RING, r.shape);
		assertEquals(WidgetCommandParser.SHAPE_TIMER, ok("shape stun timer").shape);
		assertEquals(WidgetCommandParser.SHAPE_TIMER,
				ok("shape stun countdown").shape);
	}

	@Test
	public void colorKeepsRawToken() {
		assertEquals("red", ok("color hp red").color);
		assertEquals("#CC2222", ok("color hp #CC2222").color);
		assertEquals("#333333", ok("track hp #333333").color);
		assertEquals(WidgetCommandParser.ACTION_TRACK,
				ok("track hp #333333").action);
	}

	@Test
	public void opacitySizeMove() {
		assertEquals(Integer.valueOf(70), ok("opacity hp 70").opacity);
		WidgetCommandParser.Result size = ok("size hp 180 24");
		assertEquals(Integer.valueOf(180), size.w);
		assertEquals(Integer.valueOf(24), size.h);
		WidgetCommandParser.Result move = ok("move hp 24 120");
		assertEquals(Integer.valueOf(24), move.x);
		assertEquals(Integer.valueOf(120), move.y);
	}

	@Test
	public void labelKeepsText() {
		assertEquals("HP", ok("label hp HP").text);
		assertEquals("Hit Points", ok("label hp Hit Points").text);
		assertEquals("", ok("label hp").text);
	}

	@Test
	public void valueOnOff() {
		assertEquals(Boolean.TRUE, ok("value hp on").flag);
		assertEquals(Boolean.FALSE, ok("value hp off").flag);
	}

	@Test
	public void sourceManual() {
		WidgetCommandParser.Result r = ok("source hp manual");
		assertEquals(WidgetCommandParser.SOURCE_MANUAL, r.source);
		assertNull(r.path);
		assertNull(r.maxPath);
	}

	@Test
	public void sourceGmcpWithOptionalMax() {
		WidgetCommandParser.Result both = ok(
				"source hp gmcp Char.Vitals.hp Char.Vitals.maxhp");
		assertEquals(WidgetCommandParser.SOURCE_GMCP, both.source);
		assertEquals("Char.Vitals.hp", both.path);
		assertEquals("Char.Vitals.maxhp", both.maxPath);
		WidgetCommandParser.Result one = ok("source hp gmcp Char.Vitals.hp");
		assertEquals("Char.Vitals.hp", one.path);
		assertNull(one.maxPath);
	}

	@Test
	public void sourceVarAndMcp() {
		WidgetCommandParser.Result var = ok("source hp var hp maxhp");
		assertEquals(WidgetCommandParser.SOURCE_VAR, var.source);
		assertEquals("hp", var.path);
		assertEquals("maxhp", var.maxPath);
		WidgetCommandParser.Result mcp = ok("source hp mcp hp maxhp");
		assertEquals(WidgetCommandParser.SOURCE_MCP, mcp.source);
		assertEquals("hp", mcp.path);
		assertEquals("maxhp", mcp.maxPath);
	}

	@Test
	public void bindIsSource() {
		WidgetCommandParser.Result r = ok("bind hp gmcp Char.Vitals.hp");
		assertEquals(WidgetCommandParser.ACTION_SOURCE, r.action);
		assertEquals(WidgetCommandParser.SOURCE_GMCP, r.source);
		assertEquals("Char.Vitals.hp", r.path);
	}

	@Test
	public void sourceTimerUsesClientTimerName() {
		WidgetCommandParser.Result r = ok("source hp timer stunwait");
		assertEquals(WidgetCommandParser.ACTION_SOURCE, r.action);
		assertEquals(WidgetCommandParser.SOURCE_TIMER, r.source);
		assertEquals("stunwait", r.path);
		assertNull(r.maxPath);
		assertEquals("stunwait", ok("bind hp timer stunwait").path);
	}

	@Test
	public void setValueAndMax() {
		WidgetCommandParser.Result v = ok("set hp 80");
		assertEquals(Double.valueOf(80), v.value);
		assertNull(v.max);
		WidgetCommandParser.Result pair = ok("set hp 80 100");
		assertEquals(Double.valueOf(80), pair.value);
		assertEquals(Double.valueOf(100), pair.max);
		WidgetCommandParser.Result slash = ok("set hp 80/100");
		assertEquals(Double.valueOf(80), slash.value);
		assertEquals(Double.valueOf(100), slash.max);
		WidgetCommandParser.Result timer = ok("set stun 12 30");
		assertEquals(Double.valueOf(12), timer.value);
		assertEquals(Double.valueOf(30), timer.max);
	}

	@Test
	public void tapSwipeHoldAndClear() {
		assertEquals("score", ok("tap hp score").text);
		assertEquals("", ok("tap hp").text);
		WidgetCommandParser.Result swipe = ok("swipe hp up drink");
		assertEquals(WidgetCommandParser.SWIPE_UP, swipe.swipeDir);
		assertEquals("drink", swipe.text);
		WidgetCommandParser.Result clear = ok("swipe hp up");
		assertEquals(WidgetCommandParser.SWIPE_UP, clear.swipeDir);
		assertEquals("", clear.text);
		assertEquals("sleep", ok("hold hp sleep").text);
		assertEquals("", ok("hold hp").text);
	}

	@Test
	public void warnPercentColorAndOff() {
		WidgetCommandParser.Result pct = ok("warn hp 25");
		assertEquals(Integer.valueOf(25), pct.warnPct);
		assertNull(pct.color);
		WidgetCommandParser.Result colored = ok("warn hp 25 orange");
		assertEquals(Integer.valueOf(25), colored.warnPct);
		assertEquals("orange", colored.color);
		WidgetCommandParser.Result off = ok("warn hp off");
		assertEquals(Integer.valueOf(0), off.warnPct);
		assertEquals(Boolean.FALSE, off.flag);
	}

	@Test
	public void imeStayHideOverlayAndAliases() {
		assertEquals(WidgetCommandParser.IME_STAY, ok("ime hp stay").ime);
		assertEquals(WidgetCommandParser.IME_HIDE, ok("ime hp hide").ime);
		assertEquals(WidgetCommandParser.IME_OVERLAY, ok("ime hp overlay").ime);
		assertEquals(WidgetCommandParser.IME_STAY, ok("ime hp game").ime);
		assertEquals(WidgetCommandParser.IME_OVERLAY, ok("ime hp over").ime);
		assertEquals(WidgetCommandParser.IME_OVERLAY, ok("ime hp float").ime);
		assertEquals(WidgetCommandParser.IME_OVERLAY, ok("ime hp keyboard").ime);
		assertEquals(WidgetCommandParser.IME_HIDE, ok("ime hp off").ime);
		assertEquals(WidgetCommandParser.ACTION_IME, ok("ime hp stay").action);
	}

	@Test
	public void unknownAndTyposCarryUsage() {
		assertErrorContains("foobar");
		assertErrorContains("ad hp");
		assertErrorContains("add");
		assertErrorContains("shape hp triangle");
		assertErrorContains("opacity hp lots");
		assertErrorContains("swipe hp diagonal drink");
		assertErrorContains("set hp potato");
		assertErrorContains("size hp 180");
		assertErrorContains("move hp 24");
		assertErrorContains("source hp foo");
		assertErrorContains("source hp timer");
		assertErrorContains("ime hp bounce");
		assertErrorContains("list extra");
		WidgetCommandParser.Result r = WidgetCommandParser.parse("nope");
		assertNotNull(r.error);
		assertTrue(r.error.contains(WidgetCommandParser.usage()));
		assertNull(r.action);
	}

	@Test
	public void invalidIds() {
		assertNotNull(WidgetCommandParser.parse("add").error);
		assertNotNull(WidgetCommandParser.parse("add HP!").error);
		assertNotNull(WidgetCommandParser.parse("add main").error);
		assertNotNull(WidgetCommandParser.parse("add button_window").error);
		assertEquals("hit_points", ok("add Hit_Points").id);
	}

	@Test
	public void usageMentionsTheMainVerbs() {
		String u = WidgetCommandParser.usage();
		assertTrue(u.contains("list"));
		assertTrue(u.contains("add"));
		assertTrue(u.contains("remove"));
		assertTrue(u.contains("source"));
		assertTrue(u.contains("swipe"));
		assertTrue(u.contains("warn"));
		assertTrue(u.contains("ime"));
		assertTrue(u.contains("timer"));
	}

	private static WidgetCommandParser.Result ok(String arg) {
		WidgetCommandParser.Result r = WidgetCommandParser.parse(arg);
		assertOk(r, r.action);
		return r;
	}

	private static void assertOk(WidgetCommandParser.Result r, String action) {
		assertNull(r.error);
		assertEquals(action, r.action);
	}

	private static void assertHelp(WidgetCommandParser.Result r) {
		assertNull(r.error);
		assertEquals(WidgetCommandParser.ACTION_HELP, r.action);
		assertEquals(WidgetCommandParser.usage(), r.note);
	}

	private static void assertErrorContains(String arg) {
		WidgetCommandParser.Result r = WidgetCommandParser.parse(arg);
		assertNotNull(arg, r.error);
		assertTrue(r.error.contains("Usage:"));
		assertFalse(r.error.isEmpty());
	}
}
