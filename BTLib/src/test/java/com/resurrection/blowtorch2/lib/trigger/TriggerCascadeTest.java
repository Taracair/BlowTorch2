package com.resurrection.blowtorch2.lib.trigger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

/**
 * Cascade policy: every matching trigger fires, in list order, unless the
 * caller stops after a fire. Not the old joined-regex leftmost winner.
 */
public class TriggerCascadeTest {

	private static final String LINE = "Name earned $70 for a vermin.";

	private static TriggerData trigger(final String name, final String pattern) {
		TriggerData t = new TriggerData();
		t.setName(name);
		t.setInterpretAsRegex(true);
		t.setPattern(pattern);
		t.setEnabled(true);
		return t;
	}

	private static TriggerCascade compile(final TriggerData... triggers) {
		return TriggerCascade.compile(Arrays.asList(triggers));
	}

	private static int lineOf(final String text, final int offset) {
		int n = 0;
		int lim = Math.min(offset, text.length());
		for (int i = 0; i < lim; i++) {
			if (text.charAt(i) == '\n') {
				n++;
			}
		}
		return n;
	}

	/**
	 * Connection's rule: Keep going? off stops later triggers on that line,
	 * not the rest of the chunk.
	 */
	private static List<TriggerCascade.Hit> drain(final TriggerCascade c, final String text) {
		c.reset(text);
		List<TriggerCascade.Hit> out = new ArrayList<TriggerCascade.Hit>();
		java.util.HashSet<Integer> stoppedLines = new java.util.HashSet<Integer>();
		TriggerCascade.Hit h;
		while ((h = c.nextHit()) != null) {
			int ln = lineOf(text, h.start);
			if (stoppedLines.contains(Integer.valueOf(ln))) {
				continue;
			}
			out.add(h);
			if (!h.trigger.isKeepEvaluating()) {
				int span = 1;
				String matched = h.matched();
				if (matched != null) {
					for (int i = 0; i < matched.length(); i++) {
						if (matched.charAt(i) == '\n') {
							span++;
						}
					}
				}
				for (int i = 0; i < span; i++) {
					stoppedLines.add(Integer.valueOf(ln + i));
				}
			}
		}
		return out;
	}

	@Test
	public void overlappingTriggersBothFireInListOrder() {
		TriggerData earned = trigger("price", "earned \\$(\\d+)");
		TriggerData vermin = trigger("critter", "for a vermin");
		List<TriggerCascade.Hit> hits = drain(compile(earned, vermin), LINE);
		assertEquals(2, hits.size());
		assertEquals("price", hits.get(0).trigger.getName());
		assertEquals("critter", hits.get(1).trigger.getName());
		assertEquals("70", hits.get(0).groups[1]);
	}

	@Test
	public void listOrderWinsEvenWhenTheLaterPatternIsLeftmost() {
		TriggerData laterInLine = trigger("critter", "for a vermin");
		TriggerData earlierInLine = trigger("price", "earned \\$(\\d+)");
		List<TriggerCascade.Hit> hits = drain(compile(laterInLine, earlierInLine), LINE);
		assertEquals("critter", hits.get(0).trigger.getName());
		assertEquals("price", hits.get(1).trigger.getName());
	}

	@Test
	public void keepEvaluatingFalseStopsLaterTriggers() {
		TriggerData earned = trigger("price", "earned \\$(\\d+)");
		earned.setKeepEvaluating(false);
		TriggerData vermin = trigger("critter", "for a vermin");
		List<TriggerCascade.Hit> hits = drain(compile(earned, vermin), LINE);
		assertEquals(1, hits.size());
		assertEquals("price", hits.get(0).trigger.getName());
	}

	@Test
	public void keepEvaluatingFalseStopsFurtherFindsOfTheSameTrigger() {
		TriggerData t = trigger("word", "earned");
		t.setKeepEvaluating(false);
		List<TriggerCascade.Hit> hits = drain(compile(t), "earned earned");
		assertEquals(1, hits.size());
	}

	@Test
	public void keepEvaluatingFalseDoesNotBlockALaterLine() {
		TriggerData price = trigger("price", "earned");
		price.setKeepEvaluating(false);
		TriggerData hello = trigger("hello", "says hi");
		List<TriggerCascade.Hit> hits = drain(compile(price, hello),
				"Name earned $70 for a vermin.\nsomeone says hi\n");
		assertEquals(2, hits.size());
		assertEquals("price", hits.get(0).trigger.getName());
		assertEquals("hello", hits.get(1).trigger.getName());
	}

	@Test
	public void zeroWidthSpaceBeforeCaretStillAnchors() {
		TriggerData t = trigger("price", "\u200b^(.+?)\\s+earned\\s+\\$(.+)");
		List<TriggerCascade.Hit> hits = drain(compile(t),
				"[ tag ]: Name earned $70 for a vermin.\n");
		assertEquals(1, hits.size());
		assertEquals("70 for a vermin.", hits.get(0).groups[2]);
	}

	@Test
	public void unescapedDollarIsEndOfLineNotAPrice() {
		TriggerData bad = trigger("price", "earned $70");
		assertTrue(drain(compile(bad), LINE).isEmpty());
		TriggerData good = trigger("price", "earned \\$70");
		assertEquals(1, drain(compile(good), LINE).size());
	}

	@Test
	public void caretBindsPerLineUnderMultiline() {
		TriggerData t = trigger("anchored", "^--- cut ---$\\n^(.+)$");
		List<TriggerCascade.Hit> hits = drain(compile(t),
				"noise\n--- cut ---\nthe payload\nmore\n");
		assertEquals(1, hits.size());
		assertTrue(hits.get(0).matched().startsWith("--- cut ---"));
	}

	@Test
	public void dotIsNotDotall() {
		TriggerData t = trigger("greedy", "start(.+)end");
		assertTrue(drain(compile(t), "start\nmiddle\nend\n").isEmpty());
	}

	@Test
	public void captureOneMatchesTodaysWrapperArithmetic() {
		TriggerData t = trigger("two", "gold: (\\d+)\\nsilver: (\\d+)");
		List<TriggerCascade.Hit> hits = drain(compile(t), "gold: 12\nsilver: 40\n");
		assertEquals("12", hits.get(0).groups[1]);
		assertEquals("40", hits.get(0).groups[2]);

		TriggerPattern combined = new TriggerPattern();
		int owner = combined.add(t);
		Matcher m = combined.compile(Pattern.MULTILINE).matcher("gold: 12\nsilver: 40\n");
		assertTrue(m.find());
		assertEquals(hits.get(0).groups[1], m.group(owner + 1));
		assertEquals(hits.get(0).groups[2], m.group(owner + 2));
	}

	@Test
	public void emptyCompileYieldsNoHits() {
		TriggerCascade c = TriggerCascade.compile(new ArrayList<TriggerData>());
		assertTrue(c.isEmpty());
		c.reset("anything");
		assertNull(c.nextHit());
	}

	@Test
	public void twoTriggersMayShareANamedGroup() {
		TriggerData a = trigger("one", "(?<who>\\w+) earned");
		TriggerData b = trigger("two", "(?<who>\\w+) for");
		List<TriggerCascade.Hit> hits = drain(compile(a, b), LINE);
		assertEquals(2, hits.size());
	}

	@Test
	public void stopEndsTheWalkImmediately() {
		TriggerData a = trigger("price", "earned");
		TriggerData b = trigger("critter", "vermin");
		TriggerCascade c = compile(a, b);
		c.reset(LINE);
		assertEquals("price", c.nextHit().trigger.getName());
		c.stop();
		assertNull(c.nextHit());
	}
}
