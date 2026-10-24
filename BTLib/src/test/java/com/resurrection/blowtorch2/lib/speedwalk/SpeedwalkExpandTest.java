package com.resurrection.blowtorch2.lib.speedwalk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;

import org.junit.Test;

public class SpeedwalkExpandTest {

	private static HashMap<String, DirectionData> compass() {
		HashMap<String, DirectionData> m = new HashMap<String, DirectionData>();
		m.put("n", SpeedwalkExpand.compassEntry("n", "n"));
		m.put("e", SpeedwalkExpand.compassEntry("e", "e"));
		m.put("s", SpeedwalkExpand.compassEntry("s", "s"));
		m.put("w", SpeedwalkExpand.compassEntry("w", "w"));
		m.put("u", SpeedwalkExpand.compassEntry("u", "u"));
		m.put("d", SpeedwalkExpand.compassEntry("d", "d"));
		m.put("h", SpeedwalkExpand.compassEntry("h", "nw"));
		m.put("j", SpeedwalkExpand.compassEntry("j", "ne"));
		m.put("k", SpeedwalkExpand.compassEntry("k", "sw"));
		m.put("l", SpeedwalkExpand.compassEntry("l", "se"));
		return m;
	}

	private static String lines(String... cmds) {
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < cmds.length; i++) {
			if (i > 0) {
				b.append(SpeedwalkExpand.CRLF);
			}
			b.append(cmds[i]);
		}
		return b.toString();
	}

	@Test
	public void runThreeNorthTwoEast() {
		SpeedwalkExpand.Result r = SpeedwalkExpand.forward("3n2e", compass());
		assertTrue(r.ok);
		assertEquals(lines("n", "n", "n", "e", "e"), r.cmd);
	}

	@Test
	public void runCommaInsertsLiteralBetweenWalks() {
		SpeedwalkExpand.Result r = SpeedwalkExpand.forward("2n,open door,n", compass());
		assertTrue(r.ok);
		// Historic loop: a comma after a direction adds an extra CRLF.
		assertEquals(lines("n", "n", "", "open door", "n"), r.cmd);
	}

	@Test
	public void runUnknownLetterFails() {
		SpeedwalkExpand.Result r = SpeedwalkExpand.forward("nq", compass());
		assertFalse(r.ok);
		assertEquals("q", r.errorBit);
		assertEquals(1, r.errorIndex);
	}

	@Test
	public void revThreeNorthTwoEastIsWestThenSouth() {
		SpeedwalkExpand.Result r = SpeedwalkExpand.reverse("3n2e", compass());
		assertTrue(r.ok);
		assertEquals(lines("w", "w", "s", "s", "s"), r.cmd);
	}

	@Test
	public void revKeepsCommaTextAndDoesNotInventCloseDoor() {
		SpeedwalkExpand.Result r = SpeedwalkExpand.reverse("2n,open door,n", compass());
		assertTrue(r.ok);
		assertEquals(lines("s", "open door", "s", "s"), r.cmd);
	}

	@Test
	public void revUsesFilledReverseForCustomLetters() {
		HashMap<String, DirectionData> m = compass();
		DirectionData cave = new DirectionData("c", "cave");
		cave.setReverse("out");
		m.put("c", cave);
		SpeedwalkExpand.Result r = SpeedwalkExpand.reverse("nc", m);
		assertTrue(r.ok);
		assertEquals(lines("out", "s"), r.cmd);
	}

	@Test
	public void revBlankReverseFallsBackToCompassInOut() {
		HashMap<String, DirectionData> m = compass();
		m.put("i", new DirectionData("i", "in"));
		m.put("o", new DirectionData("o", "out"));
		SpeedwalkExpand.Result r = SpeedwalkExpand.reverse("i", m);
		assertTrue(r.ok);
		assertEquals("out", r.cmd);
	}

	@Test
	public void revCustomWithoutReverseTellsThePlayerToFillTheDictionary() {
		HashMap<String, DirectionData> m = compass();
		m.put("c", new DirectionData("c", "cave"));
		SpeedwalkExpand.Result r = SpeedwalkExpand.reverse("c", m);
		assertFalse(r.ok);
		assertEquals("c", r.missingLetter);
		assertEquals("cave", r.missingCommand);
		assertNull(SpeedwalkExpand.resolvedReverse(m.get("c"), m));
	}

	@Test
	public void compassEntryFillsReverseFromOpposite() {
		DirectionData n = SpeedwalkExpand.compassEntry("n", "n");
		assertEquals("s", n.getReverse());
		DirectionData h = SpeedwalkExpand.compassEntry("h", "nw");
		assertEquals("se", h.getReverse());
	}
	@Test
	public void walkSendsEachCommandAsWrittenCountTimes() {
		SpeedwalkExpand.Result r = SpeedwalkExpand.walk("1ne1sw2e3nw", false, null, compass());
		assertTrue(r.ok);
		assertEquals(lines("ne", "sw", "e", "e", "nw", "nw", "nw"), r.cmd);
		r = SpeedwalkExpand.walk("1nw1climb vine2s", false, null, compass());
		assertEquals(lines("nw", "climb vine", "s", "s"), r.cmd);
		r = SpeedwalkExpand.walk(" 1nw 1climb vine 2s ", false, null, compass());
		assertEquals(lines("nw", "climb vine", "s", "s"), r.cmd);
	}

	@Test
	public void walkBackReversesOrderAndDirections() {
		SpeedwalkExpand.Result r = SpeedwalkExpand.walk("1nw1climb vine2s", true, null, compass());
		assertTrue(r.ok);
		// No way back known for climb vine: it stays as written.
		assertEquals(lines("n", "n", "climb vine", "se"), r.cmd);
		java.util.Map<String, String> pairs = new java.util.HashMap<String, String>();
		pairs.put("climb vine", "down");
		r = SpeedwalkExpand.walk("1nw1climb vine2s", true, pairs, compass());
		assertEquals(lines("n", "n", "down", "se"), r.cmd);
		// A pair works both ways.
		r = SpeedwalkExpand.walk("1down", true, pairs, compass());
		assertEquals("climb vine", r.cmd);
	}

	@Test
	public void walkBackUsesTheReverseOfTheRowWithThatCommand() {
		HashMap<String, DirectionData> dirs = compass();
		DirectionData door = new DirectionData("x", "enter door");
		door.setReverse("leave door");
		dirs.put("x", door);
		assertEquals("leave door", SpeedwalkExpand.walk("1enter door", true, null, dirs).cmd);
		// The letter is not the command: "l" is look here, not se.
		assertEquals("l", SpeedwalkExpand.walk("1l", true, null, dirs).cmd);
		assertEquals("go south", SpeedwalkExpand.walk("1go north", true, null, dirs).cmd);
	}

	@Test
	public void walkNeedsACountBeforeEveryCommand() {
		assertFalse(SpeedwalkExpand.walk("n2e", false, null, compass()).ok);
		assertFalse(SpeedwalkExpand.walk("2", false, null, compass()).ok);
		assertFalse(SpeedwalkExpand.walk("0n", false, null, compass()).ok);
		assertFalse(SpeedwalkExpand.walk("100n", false, null, compass()).ok);
		assertFalse(SpeedwalkExpand.walk("", false, null, compass()).ok);
	}
}
