package com.resurrection.blowtorch2.lib.alias;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Joined alias alternation plus capture-group → alias. Outer group numbers
 * depend on groups declared before; wrong numbering sends the wrong command.
 * Word-boundary wrapping and alternative order are load-bearing.
 */
public final class AliasPattern {

	/** Matches nothing and knows no aliases; what you get with none enabled. */
	public static final AliasPattern EMPTY = new AliasPattern("", new HashMap<Integer, AliasData>());

	private final String regex;
	private final Map<Integer, AliasData> groupToAlias;

	private AliasPattern(final String regex, final Map<Integer, AliasData> groupToAlias) {
		this.regex = regex;
		this.groupToAlias = groupToAlias;
	}

	/**
	 * Build the combined pattern from a set of aliases, skipping disabled ones.
	 *
	 * @param aliases Aliases in the order they should be tried.
	 * @return The combined pattern, or {@link #EMPTY} when none are enabled.
	 */
	public static AliasPattern build(final Collection<AliasData> aliases) {
		if (aliases == null || aliases.isEmpty()) {
			return EMPTY;
		}
		StringBuilder joined = new StringBuilder();
		Map<Integer, AliasData> map = new HashMap<Integer, AliasData>();
		int currentGroup = 1;
		for (AliasData alias : aliases) {
			if (alias == null || !alias.isEnabled()) {
				continue;
			}
			String pre = alias.getPre();
			if (pre == null) {
				continue;
			}
			// An anchored pattern supplies its own boundary; an unanchored one is
			// wrapped so "c" does not match inside "cast".
			String prefix = pre.startsWith("^") ? "" : "\\b";
			String suffix = pre.endsWith("$") ? "" : "\\b";
			String one = "(" + prefix + pre + suffix + ")";
			int groups;
			try {
				// Advance past this alternative's own groups, not just by one: the
				// alias pattern may declare groups of its own.
				groups = Pattern.compile(one).matcher("").groupCount();
			} catch (PatternSyntaxException bad) {
				// An alias pattern is whatever the player typed. This used to
				// throw straight out of buildAliases, and since every alias
				// shares one joined regex, a single mistyped bracket took the
				// whole alias set down with it. Drop the one that will not
				// compile and keep the rest working.
				com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor(
						"AliasPattern.build: skipping alias with bad pattern '" + pre + "'", bad);
				continue;
			}
			if (joined.length() > 0) {
				joined.append("|");
			}
			joined.append(one);
			map.put(Integer.valueOf(currentGroup), alias);
			currentGroup += groups;
		}
		if (joined.length() == 0) {
			return EMPTY;
		}
		return new AliasPattern(joined.toString(), map);
	}

	/** @return The combined regex source, or empty when no alias is enabled. */
	public String regex() {
		return regex;
	}

	/** @return true when nothing is enabled, so the caller should not try to match. */
	public boolean isEmpty() {
		return regex.length() == 0;
	}

	/** @return Compiled form of {@link #regex()}. */
	public Pattern compile() {
		return Pattern.compile(regex);
	}

	/** @return The alias owning capture group {@code group}, or null. */
	public AliasData aliasForGroup(final int group) {
		return groupToAlias.get(Integer.valueOf(group));
	}

	/**
	 * Which capture group actually matched, which identifies the alias.
	 *
	 * <p>The lowest-numbered non-null group wins. An alias's own inner groups sit
	 * after its outer one, so the outer group is always found first.
	 *
	 * @param m A matcher positioned on a successful match.
	 * @return The group number, or -1 if none matched.
	 */
	public static int matchedGroup(final Matcher m) {
		if (m == null) {
			return -1;
		}
		for (int i = 1; i <= m.groupCount(); i++) {
			if (m.group(i) != null) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * The alias behind the current match.
	 *
	 * @param m A matcher positioned on a successful match.
	 * @return The alias, or null when the match cannot be attributed to one --
	 *     which the caller must check. Dereferencing this blindly is how a
	 *     mismatched group number would become a crash rather than a no-op.
	 */
	public AliasData matchedAlias(final Matcher m) {
		int group = matchedGroup(m);
		return group < 0 ? null : aliasForGroup(group);
	}
}
