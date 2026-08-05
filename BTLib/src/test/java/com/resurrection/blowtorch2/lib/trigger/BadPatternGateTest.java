package com.resurrection.blowtorch2.lib.trigger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collection;

import org.junit.Test;

import com.resurrection.blowtorch2.lib.alias.AliasData;
import com.resurrection.blowtorch2.lib.alias.AliasPattern;

/**
 * A pattern is whatever the player typed, and it reaches Pattern.compile from
 * two places that must survive it: the editor, on the UI thread, and the
 * settings parser while loading a profile. Before these gates, one mistyped
 * bracket crashed the editor, and a bad pattern that had been saved could stop
 * the profile loading at all.
 */
public class BadPatternGateTest {

	/** The case that used to take the editor down. */
	@Test
	public void unclosedGroupDoesNotThrow() {
		TriggerData t = new TriggerData();
		t.setInterpretAsRegex(true);
		t.setPattern("You see a (dragon");
		assertNotNull("a broken pattern must still leave a usable matcher", t.getMatcher());
		assertNotNull("and must say what was wrong", t.getPatternError());
	}

	/** The fallback matches the text as typed, so behaviour stays predictable. */
	@Test
	public void brokenPatternFallsBackToLiteralMatching() {
		TriggerData t = new TriggerData();
		t.setInterpretAsRegex(true);
		t.setPattern("You see a (dragon");
		assertTrue(t.getMatcher().reset("You see a (dragon here").find());
		assertFalse(t.getMatcher().reset("You see a dragon here").find());
	}

	/** A dangling quantifier is the other common typo. */
	@Test
	public void danglingQuantifierDoesNotThrow() {
		TriggerData t = new TriggerData();
		t.setInterpretAsRegex(true);
		t.setPattern("*dragon");
		assertNotNull(t.getPatternError());
	}

	/** A good pattern is untouched and reports no complaint. */
	@Test
	public void validPatternIsUnaffected() {
		TriggerData t = new TriggerData();
		t.setInterpretAsRegex(true);
		t.setPattern("A (.+) dragon appears");
		assertNull(t.getPatternError());
		assertTrue(t.getMatcher().reset("A fierce dragon appears").find());
	}

	/** Fixing the pattern clears the previous complaint. */
	@Test
	public void errorClearsWhenThePatternIsCorrected() {
		TriggerData t = new TriggerData();
		t.setInterpretAsRegex(true);
		t.setPattern("(broken");
		assertNotNull(t.getPatternError());
		t.setPattern("(fixed)");
		assertNull(t.getPatternError());
	}

	/**
	 * A literal trigger containing "\E" ended the hand-built quoted span early
	 * and left the rest to be read as a regex, which threw on the next bracket.
	 */
	@Test
	public void literalTriggerContainingQuoteTerminatorIsSafe() {
		TriggerData t = new TriggerData();
		t.setInterpretAsRegex(false);
		t.setPattern("costs \\E5 (each)");
		assertTrue(t.getMatcher().reset("it costs \\E5 (each) today").find());
	}

	/** Regex metacharacters in a literal trigger stay literal. */
	@Test
	public void literalTriggerKeepsMetacharactersLiteral() {
		TriggerData t = new TriggerData();
		t.setInterpretAsRegex(false);
		t.setPattern("a.b");
		assertTrue(t.getMatcher().reset("a.b").find());
		assertFalse(t.getMatcher().reset("axb").find());
	}

	private static AliasData alias(String pre, String post) {
		AliasData a = new AliasData();
		a.setPre(pre);
		a.setPost(post);
		a.setEnabled(true);
		return a;
	}

	/**
	 * Every alias shares one joined regex, so a single bad pattern used to take
	 * the whole set down. The broken one is dropped, the rest keep working.
	 */
	@Test
	public void oneBadAliasDoesNotKillTheRest() {
		Collection<AliasData> all = new ArrayList<AliasData>();
		all.add(alias("^kk", "kill $1"));
		all.add(alias("(unclosed", "boom"));
		all.add(alias("^cast (.+)$", "c $1"));
		AliasPattern p = AliasPattern.build(all);
		assertFalse(p.isEmpty());
		assertTrue(p.regex().contains("kk"));
		assertTrue(p.regex().contains("cast"));
		assertFalse("the broken alias must not reach the regex",
				p.regex().contains("unclosed"));
	}

	/** Group numbering stays correct after a bad alias has been skipped. */
	@Test
	public void groupNumberingSurvivesASkippedAlias() {
		Collection<AliasData> all = new ArrayList<AliasData>();
		all.add(alias("(unclosed", "boom"));
		all.add(alias("^cast (.+) at (.+)$", "c $1 $2"));
		all.add(alias("^kk", "kill"));
		AliasPattern p = AliasPattern.build(all);
		java.util.regex.Matcher m = p.compile().matcher("cast fireball at goblin");
		assertTrue(m.find());
		assertEquals("c $1 $2", p.matchedAlias(m).getPost());
		java.util.regex.Matcher m2 = p.compile().matcher("kk");
		assertTrue(m2.find());
		assertEquals("kill", p.matchedAlias(m2).getPost());
	}

	/**
	 * An alias set that is nothing but broken patterns is empty, not explosive.
	 *
	 * <p>Note "*bad" would not do here: AliasPattern wraps an unanchored pattern
	 * in \b, and Java accepts a quantifier after a boundary, so "\b*bad\b"
	 * compiles. An unbalanced bracket is the real thing.
	 */
	@Test
	public void allBadAliasesGiveAnEmptyPattern() {
		Collection<AliasData> all = new ArrayList<AliasData>();
		all.add(alias("(unclosed", "boom"));
		all.add(alias("bad[set", "boom"));
		assertTrue(AliasPattern.build(all).isEmpty());
	}

	private static TriggerData trigger(String pattern, boolean regex) {
		TriggerData t = new TriggerData();
		t.setName(pattern);
		t.setInterpretAsRegex(regex);
		t.setPattern(pattern);
		return t;
	}

	/**
	 * The order the main profile parser uses. HyperSAXParser sets the pattern and
	 * then the flag; setInterpretAsRegex did not rebuild, so the matcher was left
	 * as the literal one built under the default flag. Every regex trigger in a
	 * saved profile matched only its own pattern text typed out verbatim.
	 */
	@Test
	public void profileParserSetterOrderStillBuildsARegexMatcher() {
		TriggerData t = new TriggerData();
		t.setPattern("A (.+) dragon appears");
		t.setInterpretAsRegex(true);
		assertEquals("the matcher must be rebuilt under the new flag",
				1, t.getMatcher().groupCount());
		assertTrue("a real game line must match",
				t.getMatcher().reset("A fierce dragon appears").find());
	}

	/** The same trigger, set the other way round, is indistinguishable. */
	@Test
	public void bothSetterOrdersAgree() {
		TriggerData profileOrder = new TriggerData();
		profileOrder.setPattern("You gain (\\d+) experience");
		profileOrder.setInterpretAsRegex(true);
		TriggerData pluginOrder = trigger("You gain (\\d+) experience", true);
		assertEquals(pluginOrder.getMatcher().groupCount(),
				profileOrder.getMatcher().groupCount());
		assertEquals(pluginOrder.getCompiledPattern().pattern(),
				profileOrder.getCompiledPattern().pattern());
	}

	/** Turning the flag back off restores literal matching. */
	@Test
	public void clearingTheRegexFlagRebuildsAsLiteral() {
		TriggerData t = trigger("a.b", true);
		assertTrue(t.getMatcher().reset("axb").find());
		t.setInterpretAsRegex(false);
		assertFalse("a literal trigger must stop matching as a regex",
				t.getMatcher().reset("axb").find());
		assertTrue(t.getMatcher().reset("a.b").find());
	}

	/**
	 * The combined pattern is where the gate was missing. buildTriggerSystem
	 * joined the raw pattern field, so a pattern TriggerData had already fallen
	 * back on reached Pattern.compile unsanitised and took the whole trigger
	 * system down -- out of a binder method, so it took the UI process with it.
	 */
	@Test
	public void badTriggerDoesNotKillTheCombinedPattern() {
		TriggerPattern p = new TriggerPattern();
		assertTrue(p.add(trigger("You see a (dragon", true)) > 0);
		assertTrue(p.add(trigger("A (.+) dragon appears", true)) > 0);
		java.util.regex.Matcher m = p.compile(0).matcher("A fierce dragon appears");
		assertTrue("the good trigger must still fire", m.find());
		assertEquals("A (.+) dragon appears", p.matchedTrigger(m).getName());
	}

	/**
	 * The other way the join used to throw: a literal trigger containing "\E"
	 * ended the hand-built quoted span early. Pattern.quote handles it, and
	 * building from the sanitised matcher is what carries that into the join.
	 */
	@Test
	public void literalTriggerContainingQuoteTerminatorSurvivesTheJoin() {
		TriggerPattern p = new TriggerPattern();
		assertTrue(p.add(trigger("cost: 5\\E(gold)", false)) > 0);
		java.util.regex.Matcher m = p.compile(0).matcher("it cost: 5\\E(gold) today");
		assertTrue(m.find());
	}

	/**
	 * The attribution test. A trigger may declare its own groups, so its outer
	 * group number depends on every trigger before it -- and a trigger whose
	 * matcher reported a different group count than the text appended (which is
	 * exactly what the setter-order bug caused) shifted every later trigger.
	 */
	@Test
	public void groupNumberingAttributesMatchesToTheRightTrigger() {
		TriggerPattern p = new TriggerPattern();
		p.add(trigger("(\\w+) hits (\\w+) for (\\d+)", true));
		p.add(trigger("You gain (\\d+) experience", true));
		p.add(trigger("a.b", false));

		java.util.regex.Matcher m = p.compile(0).matcher("You gain 42 experience");
		assertTrue(m.find());
		assertEquals("You gain (\\d+) experience", p.matchedTrigger(m).getName());
		int index = TriggerPattern.matchedGroup(m);
		assertEquals("the capture must be reachable at index + 1", "42", m.group(index + 1));

		java.util.regex.Matcher m2 = p.compile(0).matcher("a.b");
		assertTrue(m2.find());
		assertEquals("a.b", p.matchedTrigger(m2).getName());
		assertFalse("a literal trigger must not match as a regex",
				p.compile(0).matcher("axb").find());
	}

	/** A profile-order regex trigger has to be attributed correctly too. */
	@Test
	public void profileParserOrderTriggerIsAttributedInTheJoin() {
		TriggerData first = new TriggerData();
		first.setPattern("(\\w+) tells you '(.+)'");
		first.setInterpretAsRegex(true);
		TriggerPattern p = new TriggerPattern();
		p.add(first);
		p.add(trigger("You are hungry", false));

		java.util.regex.Matcher m = p.compile(0).matcher("You are hungry");
		assertTrue(m.find());
		assertEquals("the second trigger must not be shifted by the first",
				"You are hungry", p.matchedTrigger(m).getName());
	}

	/** A disabled or filtered first trigger must not leave a leading "|". */
	@Test
	public void aSkippedFirstTriggerLeavesNoEmptyAlternative() {
		TriggerPattern p = new TriggerPattern();
		assertEquals("null must be skipped", -1, p.add(null));
		assertTrue(p.add(trigger("You are hungry", false)) > 0);
		assertFalse("a leading | matches everywhere and hides every trigger",
				p.regex().startsWith("|"));
		java.util.regex.Matcher m = p.compile(0).matcher("You are hungry");
		assertTrue(m.find());
		assertNotNull(p.matchedTrigger(m));
	}

	/** Nothing added means nothing to match, not an explosion. */
	@Test
	public void emptyTriggerPatternIsEmpty() {
		TriggerPattern p = new TriggerPattern();
		assertTrue(p.isEmpty());
		assertEquals("", p.regex());
	}

	/**
	 * Alternatives that each compile can still throw when joined -- two triggers
	 * declaring the same named group is the case, and buildTriggerSystem is
	 * reachable from a binder, so that throw killed the UI process. The second
	 * one is refused at add(), before it is appended, which is the only point
	 * where the group-to-trigger map can be kept describing what was built.
	 */
	@Test
	public void aSecondTriggerReusingANamedGroupIsSkippedNotThrown() {
		TriggerPattern p = new TriggerPattern();
		assertTrue(p.add(trigger("(?<who>\\w+) arrives", true)) > 0);
		assertEquals("the duplicate must be skipped, not appended",
				-1, p.add(trigger("(?<who>\\w+) leaves", true)));
		java.util.regex.Matcher m = p.compile(0).matcher("Taracair arrives");
		assertTrue("the first trigger must still fire", m.find());
		assertEquals("(?<who>\\w+) arrives", p.matchedTrigger(m).getName());
	}

	/** Different names are fine; only a collision is refused. */
	@Test
	public void distinctNamedGroupsBothSurviveTheJoin() {
		TriggerPattern p = new TriggerPattern();
		assertTrue(p.add(trigger("(?<who>\\w+) arrives", true)) > 0);
		assertTrue(p.add(trigger("(?<leaver>\\w+) leaves", true)) > 0);
		java.util.regex.Matcher m = p.compile(0).matcher("Taracair leaves");
		assertTrue(m.find());
		assertEquals("(?<leaver>\\w+) leaves", p.matchedTrigger(m).getName());
		assertEquals("Taracair", m.group("leaver"));
	}

	/**
	 * A skipped duplicate must not move the numbering: the trigger after it is
	 * attributed by group number, and a gap there points at the wrong trigger.
	 */
	@Test
	public void skippingADuplicateDoesNotShiftLaterTriggers() {
		TriggerPattern p = new TriggerPattern();
		p.add(trigger("(?<who>\\w+) arrives", true));
		p.add(trigger("(?<who>\\w+) leaves", true));
		p.add(trigger("You gain (\\d+) experience", true));
		java.util.regex.Matcher m = p.compile(0).matcher("You gain 42 experience");
		assertTrue(m.find());
		assertEquals("You gain (\\d+) experience", p.matchedTrigger(m).getName());
		int index = TriggerPattern.matchedGroup(m);
		assertEquals("42", m.group(index + 1));
	}
}
