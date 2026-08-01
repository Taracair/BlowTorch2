package com.resurrection.blowtorch2.lib.alias;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

import org.junit.Test;

/**
 * The combined alias matcher. This is the part of alias replacement that decides
 * <em>which</em> alias a line matched, and it had no coverage at all, because the
 * arithmetic lived in a class that needs Lua and Android to instantiate.
 *
 * <p>The risk it guards: an alias pattern may declare its own capture groups, so
 * every alias's group number depends on the ones before it. Off by one there and
 * a match is credited to the wrong alias, which sends the wrong command to the
 * game.
 */
public class AliasPatternTest {

	private static AliasData alias(String pre, String post, boolean enabled) {
		AliasData a = new AliasData();
		a.setPre(pre);
		a.setPost(post);
		a.setEnabled(enabled);
		return a;
	}

	private static List<AliasData> list(AliasData... items) {
		List<AliasData> out = new ArrayList<AliasData>();
		for (AliasData a : items) {
			out.add(a);
		}
		return out;
	}

	@Test
	public void noAliasesMeansNoPattern() {
		assertTrue(AliasPattern.build(null).isEmpty());
		assertTrue(AliasPattern.build(list()).isEmpty());
	}

	/** Empty, not a pattern that matches everywhere. */
	@Test
	public void allDisabledMeansNoPattern() {
		assertTrue(AliasPattern.build(list(alias("c", "cast", false))).isEmpty());
	}

	@Test
	public void anUnanchoredAliasGetsWordBoundaries() {
		assertEquals("(\\bc\\b)", AliasPattern.build(list(alias("c", "cast", true))).regex());
	}

	/** An anchored pattern supplies its own edge, so no boundary is added. */
	@Test
	public void anchorsReplaceTheBoundaries() {
		assertEquals("(^cast (.+)$)",
				AliasPattern.build(list(alias("^cast (.+)$", "c $1", true))).regex());
		assertEquals("(^kill\\b)",
				AliasPattern.build(list(alias("^kill", "k", true))).regex());
		assertEquals("(\\bnorth$)",
				AliasPattern.build(list(alias("north$", "n", true))).regex());
	}

	@Test
	public void alternativesAreJoinedWithoutALeadingBar() {
		String regex = AliasPattern.build(list(
				alias("c", "cast", true),
				alias("k", "kill", true))).regex();
		assertEquals("(\\bc\\b)|(\\bk\\b)", regex);
	}

	/** A disabled alias in the middle must not leave a stray alternative. */
	@Test
	public void disabledAliasesAreSkippedCleanly() {
		String regex = AliasPattern.build(list(
				alias("c", "cast", true),
				alias("x", "nope", false),
				alias("k", "kill", true))).regex();
		assertEquals("(\\bc\\b)|(\\bk\\b)", regex);
	}

	/** Disabling the first one must not leave the pattern starting with "|". */
	@Test
	public void disablingTheFirstAliasDoesNotLeaveALeadingBar() {
		String regex = AliasPattern.build(list(
				alias("c", "cast", false),
				alias("k", "kill", true))).regex();
		assertEquals("(\\bk\\b)", regex);
	}

	@Test
	public void eachAliasIsFoundByItsOwnGroup() {
		AliasData c = alias("c", "cast", true);
		AliasData k = alias("k", "kill", true);
		AliasPattern p = AliasPattern.build(list(c, k));
		assertSame(c, p.aliasForGroup(1));
		assertSame(k, p.aliasForGroup(2));
	}

	/**
	 * The case the arithmetic exists for: an alias whose pattern has groups of
	 * its own pushes every later alias further along.
	 */
	@Test
	public void innerGroupsShiftTheAliasesAfterThem() {
		AliasData spell = alias("^cast (.+) at (.+)$", "c $1 $2", true);
		AliasData kill = alias("k", "kill", true);
		AliasPattern p = AliasPattern.build(list(spell, kill));

		assertSame("outer group of the first alias", spell, p.aliasForGroup(1));
		assertNull("its two inner groups belong to no alias", p.aliasForGroup(2));
		assertNull(p.aliasForGroup(3));
		assertSame("the next alias starts after them", kill, p.aliasForGroup(4));
	}

	@Test
	public void matchingCreditsTheRightAlias() {
		AliasData spell = alias("^cast (.+)$", "c $1", true);
		AliasData kill = alias("k", "kill", true);
		AliasPattern p = AliasPattern.build(list(spell, kill));

		Matcher m = p.compile().matcher("cast fireball");
		assertTrue(m.find());
		assertSame(spell, p.matchedAlias(m));

		Matcher m2 = p.compile().matcher("k goblin");
		assertTrue(m2.find());
		assertSame(kill, p.matchedAlias(m2));
	}

	/** With inner groups in play, the outer group still identifies the alias. */
	@Test
	public void matchingCreditsTheRightAliasEvenWithInnerGroups() {
		AliasData spell = alias("^cast (.+) at (.+)$", "c $1 $2", true);
		AliasData kill = alias("k", "kill", true);
		AliasPattern p = AliasPattern.build(list(spell, kill));

		Matcher m = p.compile().matcher("cast fireball at goblin");
		assertTrue(m.find());
		assertEquals(1, AliasPattern.matchedGroup(m));
		assertSame(spell, p.matchedAlias(m));
	}

	/** Word boundaries are the point of the unanchored form. */
	@Test
	public void anUnanchoredAliasDoesNotMatchInsideAWord() {
		AliasPattern p = AliasPattern.build(list(alias("c", "cast", true)));
		assertTrue("stands alone", p.compile().matcher("c fireball").find());
		assertTrue("does not match inside 'cast'",
				!p.compile().matcher("cast fireball").find());
	}

	@Test
	public void anUnmatchedMatcherCreditsNobody() {
		assertEquals(-1, AliasPattern.matchedGroup(null));
		AliasPattern p = AliasPattern.build(list(alias("c", "cast", true)));
		assertNull(p.matchedAlias(null));
	}

	/**
	 * Alternatives that each compile can still throw when joined -- two aliases
	 * declaring the same named group is the case. build() cannot catch it, because
	 * it does not compile the join; the caller does. This is why
	 * {@code Plugin.buildAliases} wraps its {@code Pattern.compile}: that method is
	 * reached from {@code ConnectionBinderFacade.setAliasEnabled}, a synchronous
	 * binder call, and the facade has no catch of its own -- so an unguarded throw
	 * here was re-thrown in the UI process and killed the window.
	 */
	@Test
	public void duplicateNamedGroupsThrowFromTheJoinNotFromBuild() {
		AliasPattern p = AliasPattern.build(list(
				alias("(?<what>\\w+) up", "cast", true),
				alias("(?<what>\\w+) down", "quaff", true)));
		assertTrue("each alias compiled on its own, so the join is non-empty", !p.isEmpty());
		try {
			java.util.regex.Pattern.compile(p.regex());
			org.junit.Assert.fail("the join was expected to throw");
		} catch (java.util.regex.PatternSyntaxException expected) {
			assertTrue(expected.getMessage() != null);
		}
	}
}
