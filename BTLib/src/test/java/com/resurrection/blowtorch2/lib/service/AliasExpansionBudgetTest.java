package com.resurrection.blowtorch2.lib.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;

import org.junit.Test;

import com.resurrection.blowtorch2.lib.alias.AliasData;
import com.resurrection.blowtorch2.lib.alias.AliasExpansionBudget;
import com.resurrection.blowtorch2.lib.alias.AliasPattern;
import com.resurrection.blowtorch2.lib.alias.AliasRecursion;

/**
 * Connection re-runs every semicolon segment of an alias rewrite through the
 * aliases. {@link #walk} models that re-insertion with the real recursion,
 * split and repeat; it is not Connection itself, so the last test checks that
 * Connection spends the budget.
 */
public class AliasExpansionBudgetTest {

	@Test
	public void budgetAllowsExactlyItsTotal() {
		AliasExpansionBudget b = new AliasExpansionBudget(3, 20);
		assertTrue(b.spend());
		assertTrue(b.spend());
		assertTrue(b.spend());
		assertFalse(b.spend());
	}

	@Test
	public void depthCountsOnlyTheChainASegmentCameFrom() {
		AliasExpansionBudget b = new AliasExpansionBudget(1000, 20);
		assertEquals(0, b.take());
		b.children(2);
		assertEquals(1, b.take());
		b.children(1);
		assertEquals(2, b.take());
		assertEquals(1, b.take());
		assertEquals(0, b.take());
	}

	@Test
	public void aliasThatSendsItsOwnTriggerAfterSemicolonStopsWithinTheDepth() {
		AliasPattern p = AliasPattern.build(Collections.singletonList(
				new AliasData("^say (.+)$", "Say one $1;say two $1", true)));
		int[] rewrites = new int[1];
		assertEquals(null, walk(p, "say lol", rewrites));
		assertTrue("stopped after " + rewrites[0],
				rewrites[0] <= AliasExpansionBudget.DEFAULT_MAX_DEPTH + 1);
	}

	@Test
	public void aliasThatDoublesItselfHitsTheDepthFirst() {
		AliasPattern p = AliasPattern.build(Collections.singletonList(
				new AliasData("^x$", "x;x", true)));
		int[] rewrites = new int[1];
		assertEquals(null, walk(p, "x", rewrites));
		assertEquals(AliasExpansionBudget.DEFAULT_MAX_DEPTH, rewrites[0]);
	}

	@Test
	public void wideShallowFanOutHitsTheTotal() {
		List<AliasData> l = new ArrayList<AliasData>();
		l.add(new AliasData("^a$", repeat("b", 40), true));
		l.add(new AliasData("^b$", repeat("c", 40), true));
		l.add(new AliasData("^c$", "north", true));
		int[] rewrites = new int[1];
		assertEquals(null, walk(AliasPattern.build(l), "a", rewrites));
		assertEquals(AliasExpansionBudget.DEFAULT_MAX_REWRITES, rewrites[0]);
	}

	private static String repeat(final String cmd, final int n) {
		StringBuilder sb = new StringBuilder(cmd);
		for (int i = 1; i < n; i++) {
			sb.append(';').append(cmd);
		}
		return sb.toString();
	}

	@Test
	public void threeLevelChainIsSent() {
		List<AliasData> l = new ArrayList<AliasData>();
		l.add(new AliasData("^a$", "b;b", true));
		l.add(new AliasData("^b$", "c;c", true));
		l.add(new AliasData("^c$", "north;south", true));
		List<String> sent = walk(AliasPattern.build(l), "a", new int[1]);
		assertEquals(8, sent.size());
	}

	@Test
	public void hundredRepeatsOfAFourCommandAliasFitTheBudget() {
		AliasPattern p = AliasPattern.build(Collections.singletonList(new AliasData(
				"^curse (.+)$",
				"cast 'malison' $1;cast 'corruption' $1;cast 'decay' $1;cast 'doom' $1",
				true)));
		List<String> sent = walk(p, "#" + CommandRepeat.MAX_REPEAT + " curse goblin",
				new int[1]);
		assertEquals(4 * CommandRepeat.MAX_REPEAT, sent.size());
		assertEquals("cast 'malison' goblin", sent.get(0));
		assertEquals("cast 'doom' goblin", sent.get(sent.size() - 1));
	}

	@Test
	public void processPreparedSegmentsSpendsTheBudgetAndDropsTheLine() throws Exception {
		String src = read("BTLib/src/com/resurrection/blowtorch2/lib/service/Connection.java");
		int start = src.indexOf("private Data processPreparedSegments");
		int end = src.indexOf("public final void cancelCommandWaits", start);
		assertTrue(start >= 0 && end > start);
		String body = src.substring(start, end);
		assertTrue(body.contains("new AliasExpansionBudget()"));
		assertTrue(body.contains("aliasBudget.spend()"));
		assertTrue(body.contains("aliasBudget.take();"));
		assertTrue(body.contains("aliasBudget.children(aliasCommands.size());"));
		int over = body.indexOf("if (overBudget) {");
		assertTrue(over >= 0);
		assertTrue(body.indexOf("mDataToServer.setLength(0);", over) > over);
	}

	/** @return what reaches the server, or null when the budget ran out. */
	private static List<String> walk(final AliasPattern p, final String typed,
			final int[] rewrites) {
		AliasExpansionBudget budget = new AliasExpansionBudget();
		List<String> list = CommandRepeat.expand(CommandSemicolon.split(typed)).segments();
		List<String> sent = new ArrayList<String>();
		ListIterator<String> it = new ArrayList<String>(list).listIterator();
		while (it.hasNext()) {
			String cmd = it.next();
			budget.take();
			String out = AliasRecursion.expand(p.compile(), p, cmd,
					new HashMap<String, String>()).text();
			if (out.equals(cmd)) {
				sent.add(cmd);
				continue;
			}
			if (!budget.spend()) {
				return null;
			}
			rewrites[0]++;
			List<String> parts = CommandSemicolon.split(out);
			budget.children(parts.size());
			for (String part : parts) {
				it.add(part);
			}
			for (int i = 0; i < parts.size(); i++) {
				it.previous();
			}
		}
		return sent;
	}

	private static String read(String relative) throws Exception {
		java.io.File[] candidates = new java.io.File[] {
				new java.io.File(relative),
				new java.io.File("../" + relative),
		};
		for (java.io.File f : candidates) {
			if (f.isFile()) {
				return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
			}
		}
		throw new AssertionError("missing " + relative);
	}
}
