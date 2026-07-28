package com.resurrection.blowtorch2.lib.alias;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * The three shapes an alias can take, and how each substitutes.
 *
 * <p>These are pinned as they behave today, not as anyone might wish they
 * behaved. One of them, {@link AliasExpansion.Mode#PLAIN}, does not match the
 * user manual -- see {@link #plainAliasesDoNotSubstituteAtAllRightNow()}.
 */
public class AliasExpansionTest {

	private static AliasData alias(String pre, String post) {
		AliasData a = new AliasData();
		a.setPre(pre);
		a.setPost(post);
		a.setEnabled(true);
		return a;
	}

	@Test
	public void anchorsDecideTheMode() {
		assertEquals(AliasExpansion.Mode.ANCHORED,
				AliasExpansion.modeFor("^cast (.+)$"));
		assertEquals(AliasExpansion.Mode.WORD_SPLIT, AliasExpansion.modeFor("^cast"));
		assertEquals(AliasExpansion.Mode.PLAIN, AliasExpansion.modeFor("c"));
		assertEquals(AliasExpansion.Mode.PLAIN, AliasExpansion.modeFor("north$"));
		assertEquals(AliasExpansion.Mode.PLAIN, AliasExpansion.modeFor(null));
	}

	/** The word-splitting form numbers the whole typed line from zero. */
	@Test
	public void wordSplitNumbersEveryWordFromZero() {
		assertEquals("kk", AliasExpansion.wordCaptures("kk goblin north").get("0"));
		assertEquals("goblin", AliasExpansion.wordCaptures("kk goblin north").get("1"));
		assertEquals("north", AliasExpansion.wordCaptures("kk goblin north").get("2"));
	}

	@Test
	public void wordSplitSubstitutesFromTheWholeLine() {
		AliasData a = alias("^kk", "kill $1 with $2");
		assertEquals("kill goblin with sword",
				AliasExpansion.expand(a, "kk goblin sword", "kk"));
	}

	/** Anchored aliases take their captures from the pattern, as a regex would. */
	@Test
	public void anchoredSubstitutesFromThePatternGroups() {
		AliasData a = alias("^cast (.+) at (.+)$", "c $1 $2");
		assertEquals("c fireball goblin",
				AliasExpansion.expand(a, "cast fireball at goblin",
						"cast fireball at goblin"));
	}

	@Test
	public void anchoredWithOneGroup() {
		AliasData a = alias("^cast (.+)$", "c $1");
		assertEquals("c fireball",
				AliasExpansion.expand(a, "cast fireball", "cast fireball"));
	}

	/**
	 * Documents a real discrepancy rather than asserting it is correct.
	 *
	 * <p>The manual says an unanchored alias "uses normal regex $n groups in
	 * With". It does not: the replacement is passed through verbatim. Commit
	 * f743daaa wrapped it in Matcher.quoteReplacement to stop captured text
	 * containing a dollar sign from throwing, which also made every $n literal.
	 *
	 * <p>Pinned here so that whichever way this is settled, it is a deliberate
	 * change with a failing test to update, not a silent one.
	 */
	@Test
	public void plainAliasesDoNotSubstituteAtAllRightNow() {
		AliasData a = alias("kk", "kill $1");
		assertEquals("kill $1", AliasExpansion.expand(a, "kk goblin", "kk"));
	}

	@Test
	public void aliasWithNoReplacementExpandsToNothing() {
		assertEquals("", AliasExpansion.expand(alias("kk", null), "kk", "kk"));
		assertEquals("", AliasExpansion.expand(null, "kk", "kk"));
	}

	/** A missing capture stays literal rather than disappearing. */
	@Test
	public void aMissingCaptureIsLeftAlone() {
		AliasData a = alias("^kk", "kill $1 and $9");
		assertEquals("kill goblin and $9",
				AliasExpansion.expand(a, "kk goblin", "kk"));
	}
}
