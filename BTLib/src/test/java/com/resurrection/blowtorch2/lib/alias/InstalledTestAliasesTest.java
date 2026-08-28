package com.resurrection.blowtorch2.lib.alias;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import org.junit.Test;

/**
 * The three aliases used for a live-world hand check, one per alias form.
 *
 * <p>These exist so that the expected output for a device test is
 * something that was executed rather than reasoned about. If one of these
 * fails, the instructions given to the maintainer are wrong, not the app.
 */
public class InstalledTestAliasesTest {

	private static AliasData alias(String pre, String post) {
		AliasData a = new AliasData();
		a.setPre(pre);
		a.setPost(post);
		a.setEnabled(true);
		return a;
	}

	/** Exactly the entries written into the profile XML. */
	private static AliasPattern installed() {
		Collection<AliasData> all = new ArrayList<AliasData>();
		all.add(alias("zzp (.+)", "kill $1"));
		all.add(alias("^zzw", "kill $1 with $2"));
		all.add(alias("^zza (.+) at (.+)$", "cast $1 $2"));
		// The pair that chains a ^alias into an unanchored one, which is the
		// shape the sticky eatTail used to truncate.
		all.add(alias("^zzc", "zzt $1 $2"));
		all.add(alias("zzt", "poke"));
		return AliasPattern.build(all);
	}

	private static String typed(String line) {
		AliasPattern p = installed();
		return AliasRecursion.expand(p.compile(), p, line,
				new HashMap<String, String>()).text();
	}

	/** Unanchored, with a group: the form the regression in cf16c9b7 was about. */
	@Test
	public void unanchoredAliasSubstitutesItsGroup() {
		assertEquals("kill goblin", typed("zzp goblin"));
	}

	/** Word-splitting: the whole line is numbered from zero, so $1 is the first argument. */
	@Test
	public void wordSplitAliasNumbersTheWholeLine() {
		assertEquals("kill goblin with sword", typed("zzw goblin sword"));
	}

	/** Anchored: captures come from the alias's own pattern. */
	@Test
	public void anchoredAliasTakesItsOwnGroups() {
		assertEquals("cast fireball goblin", typed("zza fireball at goblin"));
	}

	/**
	 * The gesture that demonstrates the eatTail fix on the device. Before it,
	 * this sent "poke" and both arguments were lost.
	 */
	@Test
	public void chainedAliasKeepsItsArguments() {
		assertEquals("poke goblin sword", typed("zzc goblin sword"));
	}

	/** A line touching none of them is passed through untouched. */
	@Test
	public void ordinaryTypingIsUnaffected() {
		assertEquals("look at the fountain", typed("look at the fountain"));
	}
}
