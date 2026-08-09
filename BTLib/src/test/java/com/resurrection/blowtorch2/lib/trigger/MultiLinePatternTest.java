package com.resurrection.blowtorch2.lib.trigger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

/**
 * Patterns that span more than one line, through the real combined-pattern
 * machinery rather than a hand-rolled regex.
 *
 * <p>Now that an unfinished trailing line is held back until it is complete,
 * every match runs against whole lines, so a pattern is finally allowed to
 * describe a block. These pin what such a pattern can and cannot do, because
 * the answer is not obvious: the combined pattern is compiled MULTILINE but not
 * DOTALL, which means {@code .} still stops at a line end and a pattern has to
 * say {@code \n} where it means one.
 */
public class MultiLinePatternTest {

	private static TriggerData trigger(final String name, final String pattern) {
		TriggerData t = new TriggerData();
		t.setName(name);
		// Order matters: setInterpretAsRegex and setPattern each rebuild the
		// compiled form, and the flag has to be set before the pattern is read
		// as one.
		t.setInterpretAsRegex(true);
		t.setPattern(pattern);
		t.setEnabled(true);
		return t;
	}

	private static Matcher matcherFor(final TriggerData... triggers) {
		TriggerPattern combined = new TriggerPattern();
		for (TriggerData t : triggers) {
			assertTrue("trigger " + t.getName() + " should have been accepted",
					combined.add(t) > 0);
		}
		return combined.compile(Pattern.MULTILINE).matcher("");
	}

	@Test
	public void aPatternSayingNewlineMatchesAcrossTwoLines() {
		Matcher m = matcherFor(trigger("room", "You see a (\\w+) here\\.\\nIt looks (\\w+)"));
		m.reset("You see a sword here.\nIt looks rusty\n");
		assertTrue("a \\n in the pattern must match the line break", m.find());
		assertTrue(m.group().contains("\n"));
	}

	@Test
	public void capturesFromDifferentLinesBothSurvive() {
		TriggerData t = trigger("two", "gold: (\\d+)\\nsilver: (\\d+)");
		TriggerPattern combined = new TriggerPattern();
		int owner = combined.add(t);
		Matcher m = combined.compile(Pattern.MULTILINE).matcher("gold: 12\nsilver: 40\n");
		assertTrue(m.find());
		// The same arithmetic Connection uses: each trigger is wrapped in a group
		// of its own, so the player's $1 is the one after the wrapper.
		assertEquals("$1 comes from the first line", "12", m.group(owner + 1));
		assertEquals("$2 comes from the second", "40", m.group(owner + 2));
	}

	@Test
	public void dotStillStopsAtALineEnd() {
		// Compiled MULTILINE, not DOTALL. This is the rule a player has to know:
		// ".+" will not run over the end of a line, so a block pattern must
		// spell out its \n.
		Matcher m = matcherFor(trigger("greedy", "start(.+)end"));
		m.reset("start\nmiddle\nend\n");
		assertTrue("dot must not swallow the newlines", !m.find());
	}

	@Test
	public void anchorsBindToEachLineNotTheWholeChunk() {
		// MULTILINE is what makes ^ and $ useful here: a block pattern anchored
		// per line is the readable way to write one.
		Matcher m = matcherFor(trigger("anchored", "^--- cut ---$\\n^(.+)$"));
		m.reset("noise\n--- cut ---\nthe payload\nmore\n");
		assertTrue(m.find());
		assertTrue(m.group().startsWith("--- cut ---"));
	}

	@Test
	public void aThreeLineBlockMatchesAsOne() {
		Matcher m = matcherFor(trigger("advert",
				"^\\+-+\\+$\\n^\\| (.+) \\|$\\n^\\+-+\\+$"));
		m.reset("+------+\n| SALE |\n+------+\n");
		assertTrue(m.find());
		assertEquals(2, countNewlines(m.group()));
	}

	@Test
	public void singleLinePatternsAreUnaffectedByAnyOfThis() {
		Matcher m = matcherFor(
				trigger("single", "\\[chatnet\\] (.+)"),
				trigger("block", "a\\nb"));
		m.reset("[chatnet] Tonkatsu says, \"sleeby\"\n");
		assertTrue(m.find());
		assertEquals(0, countNewlines(m.group()));
	}

	@Test
	public void aMultiLinePatternSitsAlongsideSingleLineOnesInTheSameJoin() {
		// The combined pattern is one alternation over every enabled trigger, so
		// a block pattern must not disturb its neighbours.
		TriggerData single = trigger("chat", "\\[chatnet\\] (.+)");
		TriggerData block = trigger("block", "top\\nbottom");
		TriggerPattern combined = new TriggerPattern();
		int singleGroup = combined.add(single);
		int blockGroup = combined.add(block);
		assertTrue(singleGroup > 0);
		assertTrue(blockGroup > singleGroup);
		Pattern p = combined.compile(Pattern.MULTILINE);
		assertNotNull(p);

		Matcher m = p.matcher("top\nbottom\n[chatnet] hi\n");
		assertTrue(m.find());
		assertEquals("top\nbottom", m.group());
		assertTrue(m.find());
		assertTrue(m.group().startsWith("[chatnet]"));
	}

	private static int countNewlines(final String s) {
		int n = 0;
		for (int i = 0; i < s.length(); i++) {
			if (s.charAt(i) == '\n') {
				n++;
			}
		}
		return n;
	}
}
