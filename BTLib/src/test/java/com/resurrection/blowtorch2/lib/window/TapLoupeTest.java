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

	private static TapLoupe.Query query(List<TapLoupe.Target> merged, int x,
			int y, int radius) {
		return TapLoupe.query(merged, x, y, radius, 20);
	}

	private static List<TapLoupe.Target> merge(TapLoupe.Target... words) {
		return TapLoupe.merge(Arrays.asList(words), 20);
	}

	@Test
	public void twoDifferentWordsNearFingerAreLoupe() {
		List<TapLoupe.Target> merged = Arrays.asList(
				word(0, 0, 40, 20, "north", "n"),
				word(50, 0, 90, 20, "east", "e"));
		TapLoupe.Query q = query(merged, 45, 10, 20);
		assertEquals(TapLoupe.Kind.LOUPE, q.kind);
		assertEquals(2, q.candidates.size());
		assertNotNull(q.selected);
	}

	@Test
	public void twoWordsInTheCircleWithAGapWiderThanRadiusStillLoupe() {
		List<TapLoupe.Target> merged = Arrays.asList(
				word(0, 0, 20, 20, "north", "n"),
				word(50, 0, 70, 20, "east", "e"));
		TapLoupe.Query q = query(merged, 35, 10, 20);
		assertEquals(TapLoupe.Kind.LOUPE, q.kind);
		assertEquals(2, q.candidates.size());
	}

	@Test
	public void oneWordWithSeveralCommandsIsMenu() {
		List<TapLoupe.Target> merged = Arrays.asList(
				word(0, 0, 40, 20, "goblin", "kill goblin", "look goblin"));
		TapLoupe.Query q = query(merged, 20, 10, 20);
		assertEquals(TapLoupe.Kind.MENU, q.kind);
		assertEquals("goblin", q.selected.label);
	}

	@Test
	public void oneWordWithOneCommandIsNone() {
		List<TapLoupe.Target> merged = Arrays.asList(
				word(0, 0, 40, 20, "north", "n"));
		assertEquals(TapLoupe.Kind.NONE, query(merged, 20, 10, 20).kind);
	}

	@Test
	public void sameCommandsThatTouchMergeToOneWord() {
		TapLoupe.Target a = word(0, 0, 20, 16, "bot", "get bottle");
		TapLoupe.Target b = word(20, 0, 40, 16, "tle", "get bottle");
		List<TapLoupe.Target> merged = merge(a, b);
		assertEquals(1, merged.size());
		assertEquals("bottle", merged.get(0).label);
		assertEquals(0, merged.get(0).left);
		assertEquals(40, merged.get(0).right);
	}

	@Test
	public void sameCommandsWithAGapStayTwo() {
		TapLoupe.Target a = word(0, 0, 20, 16, "bot", "get bottle");
		TapLoupe.Target b = word(24, 0, 44, 16, "tle", "get bottle");
		List<TapLoupe.Target> merged = merge(a, b);
		assertEquals(2, merged.size());
	}

	@Test
	public void overlappingDifferentCommandsStayTwo() {
		TapLoupe.Target tap = word(0, 0, 40, 20, "bottle", "get bottle");
		TapLoupe.Target link = new TapLoupe.Target(0, 0, 40, 20, "bottle",
				new String[] { "send:look bottle" }, false, true);
		List<TapLoupe.Target> merged = merge(tap, link);
		assertEquals(2, merged.size());
		TapLoupe.Query q = query(merged, 20, 10, 8);
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
		assertEquals(TapLoupe.Kind.NONE, query(merged, 10, 8, 20).kind);
	}

	@Test
	public void wideTriggerWordStillLoupesACloseNeighbour() {
		// Finger sits in the left of a long capture; the next tappable word
		// is one space away. The circle never reaches it — OSC 8 short links
		// do not have this shape. Same-line cluster still yields a loupe.
		List<TapLoupe.Target> merged = Arrays.asList(
				word(0, 0, 80, 20, "a rusty sword", "get a rusty sword"),
				word(84, 0, 140, 20, "a leather bag", "get a leather bag"));
		TapLoupe.Query q = query(merged, 10, 10, 20);
		assertEquals(TapLoupe.Kind.LOUPE, q.kind);
		assertEquals(2, q.candidates.size());
		assertEquals("a rusty sword", q.selected.label);
	}

	@Test
	public void closeClusterDoesNotJumpToADifferentColumnOnTheNextLine() {
		List<TapLoupe.Target> merged = Arrays.asList(
				word(0, 0, 40, 60, "north", "n"),
				word(50, 0, 90, 60, "east", "e"),
				word(50, 22, 90, 82, "south", "s"));
		TapLoupe.Query q = TapLoupe.query(merged, 20, 30, 40, 22);
		assertEquals(TapLoupe.Kind.LOUPE, q.kind);
		assertEquals(2, q.candidates.size());
		assertEquals("north", q.selected.label);
		for (int i = 0; i < q.candidates.size(); i++) {
			assertFalse("south".equals(q.candidates.get(i).label));
		}
	}

	@Test
	public void stackedWordsAreALoupe() {
		List<TapLoupe.Target> merged = Arrays.asList(
				word(0, 0, 40, 60, "north", "n"),
				word(0, 22, 40, 82, "south", "s"));
		TapLoupe.Query q = TapLoupe.query(merged, 20, 30, 40, 22);
		assertEquals(TapLoupe.Kind.LOUPE, q.kind);
		assertEquals(2, q.candidates.size());
	}

	@Test
	public void stackedSameCommandWordsStayTwoAndLoupe() {
		TapLoupe.Target a = word(0, 0, 80, 60, "FlugHammer", "look FlugHammer");
		TapLoupe.Target b = word(0, 22, 80, 82, "FlugHammer", "look FlugHammer");
		List<TapLoupe.Target> merged = TapLoupe.merge(Arrays.asList(a, b), 22);
		assertEquals(2, merged.size());
		TapLoupe.Query q = TapLoupe.query(merged, 40, 30, 40, 22);
		assertEquals(TapLoupe.Kind.LOUPE, q.kind);
		assertEquals(2, q.candidates.size());
	}

	@Test
	public void wideWordKeepsThisLineWhenANarrowerWordBelowAlsoContainsTheFinger() {
		List<TapLoupe.Target> merged = Arrays.asList(
				word(0, 0, 80, 60, "a rusty sword", "get sword"),
				word(84, 0, 140, 60, "a leather bag", "get bag"),
				word(10, 22, 40, 82, "exit", "exit"));
		TapLoupe.Query q = TapLoupe.query(merged, 20, 30, 40, 22);
		assertEquals(TapLoupe.Kind.LOUPE, q.kind);
		assertEquals("a rusty sword", q.selected.label);
		boolean sawBag = false;
		for (int i = 0; i < q.candidates.size(); i++) {
			if ("a leather bag".equals(q.candidates.get(i).label)) {
				sawBag = true;
			}
		}
		assertTrue(sawBag);
	}

	@Test
	public void sameCommandFragmentsDoNotLoupeAsTwoWords() {
		TapLoupe.Target a = word(0, 0, 20, 16, "bot", "get bottle");
		TapLoupe.Target b = word(24, 0, 44, 16, "tle", "get bottle");
		List<TapLoupe.Target> merged = merge(a, b);
		assertEquals(2, merged.size());
		assertEquals(TapLoupe.Kind.NONE, query(merged, 10, 8, 20).kind);
	}

	@Test
	public void sameLineClusterIsTransitive() {
		List<TapLoupe.Target> merged = Arrays.asList(
				word(0, 0, 20, 16, "n", "n"),
				word(30, 0, 50, 16, "e", "e"),
				word(60, 0, 80, 16, "s", "s"));
		TapLoupe.Query q = query(merged, 10, 8, 20);
		assertEquals(TapLoupe.Kind.LOUPE, q.kind);
		assertEquals(3, q.candidates.size());
	}

	@Test
	public void underFingerFollowsAnIsolatedWord() {
		List<TapLoupe.Target> merged = Arrays.asList(
				word(0, 0, 80, 20, "sword", "get sword"),
				word(84, 0, 140, 20, "bag", "get bag"),
				word(400, 0, 440, 20, "exit", "exit"));
		assertEquals(TapLoupe.Kind.LOUPE, query(merged, 10, 10, 20).kind);
		assertEquals(TapLoupe.Kind.NONE, query(merged, 420, 10, 20).kind);
		TapLoupe.Target u = TapLoupe.underFinger(merged, 420, 10, 20, 20);
		assertEquals("exit", u.label);
	}

	@Test
	public void emptyOrNullIsNone() {
		assertEquals(TapLoupe.Kind.NONE, query(null, 0, 0, 10).kind);
		assertEquals(TapLoupe.Kind.NONE,
				query(new ArrayList<TapLoupe.Target>(), 0, 0, 10).kind);
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
