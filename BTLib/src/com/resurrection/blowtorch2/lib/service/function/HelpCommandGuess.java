package com.resurrection.blowtorch2.lib.service.function;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;

/**
 * One command name when a typed word matched nothing.
 * A swap counts as one edit; a short name allows one, a long name three. A tie is no guess.
 */
public final class HelpCommandGuess {

	/** Real commands that answer to these names and are not registered with cmd(). */
	private static final String[] FILTER_ALIASES = {
		"commands",
		"kb",
	};

	private HelpCommandGuess() {
	}

	/** Inserts, deletes, substitutions, and one adjacent swap, each costing 1. */
	public static int distance(final String left, final String right) {
		int n = left.length();
		int m = right.length();
		int[] prev2 = new int[m + 1];
		int[] prev = new int[m + 1];
		int[] cur = new int[m + 1];
		for (int j = 0; j <= m; j++) {
			prev[j] = j;
		}
		for (int i = 1; i <= n; i++) {
			cur[0] = i;
			char ca = left.charAt(i - 1);
			for (int j = 1; j <= m; j++) {
				int cost = ca == right.charAt(j - 1) ? 0 : 1;
				int best = Math.min(Math.min(prev[j] + 1, cur[j - 1] + 1),
						prev[j - 1] + cost);
				if (i > 1 && j > 1
						&& ca == right.charAt(j - 2)
						&& left.charAt(i - 2) == right.charAt(j - 1)) {
					best = Math.min(best, prev2[j - 2] + 1);
				}
				cur[j] = best;
			}
			int[] older = prev2;
			prev2 = prev;
			prev = cur;
			cur = older;
		}
		return prev[m];
	}

	/** Documented command names, lowercased, plus kb and commands. */
	public static String pick(final String typed) {
		LinkedHashSet<String> names = new LinkedHashSet<String>();
		for (String name : HelpCommand.registeredNames()) {
			if (name == null || name.isEmpty()) {
				continue;
			}
			names.add(name.toLowerCase(Locale.US));
		}
		for (String alias : FILTER_ALIASES) {
			names.add(alias);
		}
		return pick(typed, names);
	}

	/**
	 * @return the single closest accepted name, or null when none qualify or
	 *         two names share that distance
	 */
	public static String pick(final String typed, final Collection<String> candidates) {
		if (typed == null || candidates == null) {
			return null;
		}
		String query = typed.toLowerCase(Locale.US);
		String best = null;
		int bestDistance = Integer.MAX_VALUE;
		boolean tie = false;
		LinkedHashSet<String> seen = new LinkedHashSet<String>();
		for (String candidate : candidates) {
			if (candidate == null || candidate.isEmpty()) {
				continue;
			}
			String name = candidate.toLowerCase(Locale.US);
			if (!seen.add(name)) {
				continue;
			}
			int d = distance(query, name);
			if (!accepted(d, name.length())) {
				continue;
			}
			if (best == null || d < bestDistance) {
				best = name;
				bestDistance = d;
				tie = false;
			} else if (d == bestDistance) {
				tie = true;
			}
		}
		if (tie || best == null) {
			return null;
		}
		return best;
	}

	public static String didYouMean(final String name) {
		if (name == null || name.isEmpty()) {
			return null;
		}
		return "Did you mean \"." + name + "\"?\n";
	}

	private static boolean accepted(final int distance, final int candidateLength) {
		if (distance <= 0) {
			return false;
		}
		return distance <= budget(candidateLength);
	}

	/** How many edits a name of this length can still be. */
	static int budget(final int candidateLength) {
		if (candidateLength <= 4) {
			return 1;
		}
		if (candidateLength <= 7) {
			return 2;
		}
		return 3;
	}
}
