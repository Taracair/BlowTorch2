package com.resurrection.blowtorch2.lib.alias;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

/**
 * The recursive half of alias replacement, pinned as it behaves today.
 *
 * <p>Several of these encode quirks rather than intentions -- the sticky
 * {@code eatTail} flag and the semicolon split that looks at the whole buffer
 * instead of the match. They are here so that a later change to that method has
 * to be a deliberate one.
 */
public class AliasRecursionTest {

	private static AliasData alias(String pre, String post) {
		AliasData a = new AliasData();
		a.setPre(pre);
		a.setPost(post);
		a.setEnabled(true);
		return a;
	}

	private static AliasPattern patternFor(AliasData... aliases) {
		Collection<AliasData> all = new ArrayList<AliasData>();
		for (AliasData a : aliases) {
			all.add(a);
		}
		return AliasPattern.build(all);
	}

	private static AliasRecursion.Result run(String input, AliasData... aliases) {
		AliasPattern p = patternFor(aliases);
		return AliasRecursion.expand(p.compile(), p, input,
				new HashMap<String, String>());
	}

	/** Nothing matches: the text comes back untouched. */
	@Test
	public void textWithNoAliasIsUnchanged() {
		AliasRecursion.Result r = run("look at the sky", alias("^kk", "kill $1"));
		assertEquals("look at the sky", r.text());
		assertFalse(r.hitLimit());
	}

	/** The word-splitting form numbers the buffer from zero. */
	@Test
	public void wordSplitFormSubstitutesPositionally() {
		AliasRecursion.Result r = run("kk goblin", alias("^kk", "kill $1"));
		assertEquals("kill goblin", r.text());
		assertFalse(r.hitLimit());
	}

	@Test
	public void matchSinkSeesWordSplitCaptures() {
		final List<Map<String, String>> seen = new ArrayList<Map<String, String>>();
		AliasData a = alias("^kk", "kill $1");
		AliasPattern p = patternFor(a);
		AliasRecursion.expand(p.compile(), p, "kk goblin",
				new HashMap<String, String>(),
				new AliasRecursion.MatchSink() {
					public void onMatch(AliasData alias, Map<String, String> captures) {
						seen.add(new HashMap<String, String>(captures));
					}
				});
		assertEquals(1, seen.size());
		assertEquals("goblin", seen.get(0).get("1"));
	}

	@Test
	public void matchSinkSeesChainedAlias() {
		final List<String> pres = new ArrayList<String>();
		AliasPattern p = patternFor(alias("^kk", "attack $1"), alias("attack", "kill"));
		AliasRecursion.expand(p.compile(), p, "kk goblin",
				new HashMap<String, String>(),
				new AliasRecursion.MatchSink() {
					public void onMatch(AliasData alias, Map<String, String> captures) {
						pres.add(alias.getPre());
					}
				});
		assertEquals(2, pres.size());
		assertEquals("^kk", pres.get(0));
		assertEquals("attack", pres.get(1));
	}
	@Test
	public void semicolonTailIsPreserved() {
		assertEquals("kill goblin;flee",
				AliasRecursion.splitAndSubstitute("kill $1", "kk goblin;flee"));
	}

	/** With no semicolon there is no separator to add back. */
	@Test
	public void noSemicolonMeansNoSeparator() {
		assertEquals("kill goblin",
				AliasRecursion.splitAndSubstitute("kill $1", "kk goblin"));
	}

	/**
	 * A quirk, pinned deliberately: the split looks at the whole buffer, so only
	 * the words before the first semicolon are numbered even when the alias that
	 * fired sits after it.
	 */
	@Test
	public void splitAlwaysUsesTheFirstSemicolonInTheWholeBuffer() {
		assertEquals("say north;kk goblin",
				AliasRecursion.splitAndSubstitute("say $1", "go north;kk goblin"));
	}

	/**
	 * Another quirk: the pattern splits on a single whitespace character, so two
	 * spaces in a row produce an empty word rather than being collapsed.
	 */
	@Test
	public void doubledSpacesProduceAnEmptyWord() {
		assertEquals("kill |goblin",
				AliasRecursion.splitAndSubstitute("kill $1|$2", "kk  goblin"));
	}

	/** An alias whose output contains another alias keeps expanding. */
	@Test
	public void expansionChainsThroughASecondAlias() {
		AliasRecursion.Result r = run("kk goblin",
				alias("^kk", "attack $1"), alias("attack", "kill"));
		assertEquals("kill goblin", r.text());
		assertFalse(r.hitLimit());
	}

	/**
	 * The regression test for the sticky {@code eatTail}. It used to be declared
	 * outside the pass loop, so once a {@code ^kk} alias had fired, the next
	 * pass through the unanchored branch dropped everything after its own match
	 * and the player's argument vanished: this line produced "kill".
	 */
	@Test
	public void tailSurvivesAfterAWordSplitAliasHasFired() {
		AliasRecursion.Result r = run("kk goblin sword",
				alias("^kk", "attack $1 $2"), alias("attack", "kill"));
		assertEquals("kill goblin sword", r.text());
	}

	/**
	 * The reason the flag exists at all: within one pass, the word-splitting
	 * branch rewrites the whole buffer including everything after the semicolon,
	 * so the tail must not be appended a second time.
	 */
	@Test
	public void wordSplitDoesNotDuplicateTheTailWithinAPass() {
		AliasRecursion.Result r = run("kk goblin;flee", alias("^kk", "kill $1"));
		assertEquals("kill goblin;flee", r.text());
	}

	/**
	 * The reason this class has a pass limit. An alias whose replacement still
	 * contains its own trigger as a whole word never stopped expanding, and the
	 * loop runs on the connection thread.
	 */
	@Test
	public void selfMatchingAliasStopsAtTheLimitInsteadOfHanging() {
		AliasRecursion.Result r = run("kk", alias("kk", "kk goblin"));
		assertTrue("a self-matching alias must report the limit", r.hitLimit());
		assertFalse("and must not run away", r.text().length() > 10000);
	}

	/** Two aliases that expand into each other loop just as well. */
	@Test
	public void mutuallyRecursiveAliasesStopAtTheLimit() {
		AliasRecursion.Result r = run("aa", alias("aa", "bb"), alias("bb", "aa"));
		assertTrue(r.hitLimit());
	}

	/** The limit is honoured exactly, and is reachable with a low ceiling. */
	@Test
	public void passLimitIsRespected() {
		AliasPattern p = patternFor(alias("kk", "kk goblin"));
		AliasRecursion.Result r = AliasRecursion.expand(p.compile(), p, "kk",
				new HashMap<String, String>(), 2);
		assertTrue(r.hitLimit());
	}

	/** Session variables reach the anchored form. */
	@Test
	public void anchoredFormSpendsSessionVariables() {
		Map<String, String> vars = new HashMap<String, String>();
		vars.put("target", "goblin");
		AliasPattern p = patternFor(alias("^att$", "kill ${target}"));
		AliasRecursion.Result r = AliasRecursion.expand(p.compile(), p, "att", vars);
		assertEquals("kill goblin", r.text());
	}

	/** Null input is answered, not thrown at. */
	@Test
	public void nullInputIsSafe() {
		AliasPattern p = patternFor(alias("^kk", "kill $1"));
		AliasRecursion.Result r = AliasRecursion.expand(p.compile(), p, null,
				new HashMap<String, String>());
		assertEquals("", r.text());
		assertFalse(r.hitLimit());
	}

	/** An empty alias set leaves the line alone. */
	@Test
	public void emptyAliasSetLeavesTextAlone() {
		List<AliasData> none = new ArrayList<AliasData>();
		AliasPattern p = AliasPattern.build(none);
		AliasRecursion.Result r = AliasRecursion.expand(p.compile(), p, "kk goblin",
				new HashMap<String, String>());
		assertEquals("kk goblin", r.text());
	}
}
