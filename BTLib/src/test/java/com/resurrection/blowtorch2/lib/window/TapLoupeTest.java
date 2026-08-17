package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class TapLoupeTest {

	private static TapLoupe.Target word(int left, int top, int right, int bottom,
			String label, String... cmds) {
		return new TapLoupe.Target(left, top, right, bottom, label, cmds,
				false, false);
	}

	@Test
	public void twoDifferentWordsNearFingerAreLoupe() {
		List<TapLoupe.Target> merged = Arrays.asList(
				word(0, 0, 40, 20, "north", "n"),
				word(50, 0, 90, 20, "east", "e"));
		TapLoupe.Query q = TapLoupe.query(merged, 45, 10, 20);
		assertEquals(TapLoupe.Kind.LOUPE, q.kind);
		assertEquals(2, q.candidates.size());
		assertNotNull(q.selected);
	}

	@Test
	public void oneWordWithSeveralCommandsIsMenu() {
		List<TapLoupe.Target> merged = Arrays.asList(
				word(0, 0, 40, 20, "goblin", "kill goblin", "look goblin"));
		TapLoupe.Query q = TapLoupe.query(merged, 20, 10, 20);
		assertEquals(TapLoupe.Kind.MENU, q.kind);
		assertEquals("goblin", q.selected.label);
	}

	@Test
	public void oneWordWithOneCommandIsNone() {
		List<TapLoupe.Target> merged = Arrays.asList(
				word(0, 0, 40, 20, "north", "n"));
		assertEquals(TapLoupe.Kind.NONE, TapLoupe.query(merged, 20, 10, 20).kind);
	}

	@Test
	public void sameCommandsThatTouchMergeToOneWord() {
		TapLoupe.Target a = word(0, 0, 20, 16, "bot", "get bottle");
		TapLoupe.Target b = word(20, 0, 40, 16, "tle", "get bottle");
		List<TapLoupe.Target> merged = TapLoupe.merge(Arrays.asList(a, b));
		assertEquals(1, merged.size());
		assertEquals("bottle", merged.get(0).label);
		assertEquals(0, merged.get(0).left);
		assertEquals(40, merged.get(0).right);
	}

	@Test
	public void sameCommandsWithAGapStayTwo() {
		TapLoupe.Target a = word(0, 0, 20, 16, "bot", "get bottle");
		TapLoupe.Target b = word(24, 0, 44, 16, "tle", "get bottle");
		List<TapLoupe.Target> merged = TapLoupe.merge(Arrays.asList(a, b));
		assertEquals(2, merged.size());
	}

	@Test
	public void overlappingDifferentCommandsStayTwo() {
		TapLoupe.Target tap = word(0, 0, 40, 20, "bottle", "get bottle");
		TapLoupe.Target link = new TapLoupe.Target(0, 0, 40, 20, "bottle",
				new String[] { "send:look bottle" }, false, true);
		List<TapLoupe.Target> merged = TapLoupe.merge(Arrays.asList(tap, link));
		assertEquals(2, merged.size());
		TapLoupe.Query q = TapLoupe.query(merged, 20, 10, 8);
		assertEquals(TapLoupe.Kind.LOUPE, q.kind);
	}

	@Test
	public void pickPrefersTheBoxThatContainsTheFinger() {
		TapLoupe.Target north = word(0, 0, 40, 20, "north", "n");
		TapLoupe.Target east = word(50, 0, 90, 20, "east", "e");
		TapLoupe.Target picked = TapLoupe.pick(Arrays.asList(north, east), 60, 10);
		assertEquals("east", picked.label);
	}

	@Test
	public void pickOnTheSmallerContainingBoxWins() {
		TapLoupe.Target wide = word(0, 0, 100, 20, "wide", "wide");
		TapLoupe.Target tight = word(40, 0, 60, 20, "tight", "tight");
		TapLoupe.Target picked = TapLoupe.pick(Arrays.asList(wide, tight), 50, 10);
		assertEquals("tight", picked.label);
	}

	@Test
	public void circleMissesAWordThatIsTooFar() {
		List<TapLoupe.Target> merged = Arrays.asList(
				word(0, 0, 20, 16, "a", "a"),
				word(200, 0, 220, 16, "b", "b"));
		assertEquals(TapLoupe.Kind.NONE, TapLoupe.query(merged, 10, 8, 20).kind);
	}

	@Test
	public void emptyOrNullIsNone() {
		assertEquals(TapLoupe.Kind.NONE, TapLoupe.query(null, 0, 0, 10).kind);
		assertEquals(TapLoupe.Kind.NONE,
				TapLoupe.query(new ArrayList<TapLoupe.Target>(), 0, 0, 10).kind);
		assertNull(TapLoupe.pick(null, 0, 0));
	}

	@Test
	public void radiusIsAtLeastLineAndTwentyEightDp() {
		assertEquals(40, TapLoupe.radiusPx(32, 1f));
		assertTrue(TapLoupe.radiusPx(10, 3f) >= 84);
	}

	@Test
	public void loupePickOpensMenuEvenWhenTapWouldSendFirst() {
		TapLoupe.Target holdMenu = new TapLoupe.Target(0, 0, 40, 20, "goblin",
				new String[] { "kill goblin", "look goblin" }, true, false);
		assertTrue(TapLoupe.pickOpensMenu(holdMenu));
		TapLoupe.Target oneCmd = word(0, 0, 40, 20, "north", "n");
		assertFalse(TapLoupe.pickOpensMenu(oneCmd));
		TapLoupe.Target href = new TapLoupe.Target(0, 0, 40, 20, "link",
				new String[] { "send:look" }, true, true);
		assertFalse(TapLoupe.pickOpensMenu(href));
	}
}
