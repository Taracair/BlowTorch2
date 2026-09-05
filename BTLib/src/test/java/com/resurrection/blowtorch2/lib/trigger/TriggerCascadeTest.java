package com.resurrection.blowtorch2.lib.trigger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.resurrection.blowtorch2.lib.trigger.style.StyleLineModel;
import com.resurrection.blowtorch2.lib.trigger.style.StyleMatchSpec;
import com.resurrection.blowtorch2.lib.trigger.style.StyleMatchSpec.Gate;
import com.resurrection.blowtorch2.lib.trigger.style.StyleSnapshot.ColorSpace;
import com.resurrection.blowtorch2.lib.window.TextTree;

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

	@Test
	public void styleOnlyFiresOnMatchingRun() throws Exception {
		TextTree tree = new TextTree();
		tree.addBytesImpl("\u001B[32mloot\n".getBytes("UTF-8"));
		StyleLineModel[] models = StyleLineModel.buildTree(tree);
		int[] starts = new int[] { 0 };
		TriggerData t = new TriggerData();
		t.setName("green");
		t.setPattern("");
		t.setEnabled(true);
		StyleMatchSpec spec = new StyleMatchSpec();
		spec.setFg(Gate.REQUIRE, ColorSpace.ANSI16, 32);
		t.setStyleMatch(spec);
		TriggerCascade c = compile(t);
		assertTrue(c.hasStyleWork());
		c.reset("loot\n");
		c.attachStyle(models, starts);
		TriggerCascade.Hit hit = c.nextHit();
		assertEquals("green", hit.trigger.getName());
		assertEquals("loot", hit.matched());
		assertNull(c.nextHit());
	}

	@Test
	public void regexPlusStyleSkipsWrongColour() throws Exception {
		TextTree tree = new TextTree();
		tree.addBytesImpl("\u001B[32mloot\u001B[0m loot\n".getBytes("UTF-8"));
		StyleLineModel[] models = StyleLineModel.buildTree(tree);
		int[] starts = new int[] { 0 };
		TriggerData t = trigger("loot-green", "loot");
		StyleMatchSpec spec = new StyleMatchSpec();
		spec.setFg(Gate.REQUIRE, ColorSpace.ANSI16, 32);
		t.setStyleMatch(spec);
		TriggerCascade c = compile(t);
		c.reset("loot loot\n");
		c.attachStyle(models, starts);
		List<TriggerCascade.Hit> hits = new ArrayList<TriggerCascade.Hit>();
		TriggerCascade.Hit h;
		while ((h = c.nextHit()) != null) {
			hits.add(h);
		}
		assertEquals(1, hits.size());
		assertEquals(0, hits.get(0).start);
	}

	@Test
	public void regexPlusStyleWithoutAttachDoesNotFire() {
		TriggerData t = trigger("loot-green", "loot");
		StyleMatchSpec spec = new StyleMatchSpec();
		spec.setFg(Gate.REQUIRE, ColorSpace.ANSI16, 32);
		t.setStyleMatch(spec);
		TriggerCascade c = compile(t);
		c.reset("loot\n");
		assertNull(c.nextHit());
	}

	@Test
	public void regexPlusStyleFailsClosedOnLengthMismatch() throws Exception {
		TextTree tree = new TextTree();
		tree.addBytesImpl("\u001B[32mloot\n".getBytes("UTF-8"));
		StyleLineModel[] models = StyleLineModel.buildTree(tree);
		int[] starts = new int[] { 0 };
		TriggerData t = trigger("loot-green", "loot");
		StyleMatchSpec spec = new StyleMatchSpec();
		spec.setFg(Gate.REQUIRE, ColorSpace.ANSI16, 32);
		t.setStyleMatch(spec);
		TriggerCascade c = compile(t);
		c.reset("loot\n");
		c.attachStyle(models, starts, new int[] { 99 });
		assertNull(c.nextHit());
	}

	@Test
	public void blankPatternWithoutStyleIsNotCompiled() {
		TriggerData t = new TriggerData();
		t.setName("empty");
		t.setPattern("");
		t.setEnabled(true);
		TriggerCascade c = compile(t);
		assertTrue(c.isEmpty());
	}
}
