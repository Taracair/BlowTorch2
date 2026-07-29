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
}
