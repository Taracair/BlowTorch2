package com.resurrection.blowtorch2.lib.alias;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import com.resurrection.blowtorch2.lib.responder.CaptureSubstitution;

/**
 * Turns an alias plus the text that matched it into the text to send.
 *
 * <p>Extracted from the alias replacement loop so the three different shapes an
 * alias can take are visible and testable. Which one applies is decided purely
 * by whether the pattern is anchored, and each behaves differently enough that
 * players hit the differences without knowing the rule.
 */
public final class AliasExpansion {

	/** Whitespace, for the word-splitting form. */
	private static final Pattern WHITESPACE = Pattern.compile("\\s");

	/** How an alias substitutes, decided by its anchors. */
	public enum Mode {
		/**
		 * {@code ^} but no {@code $}. The whole typed line is split on
		 * whitespace: {@code $0} is the first word, {@code $1} the next.
		 */
		WORD_SPLIT,
		/**
		 * Both {@code ^} and {@code $}. {@code $1} is the first {@code (…)}
		 * group in the pattern, as people expect from a regex.
		 */
		ANCHORED,
		/**
		 * Neither anchor. The pattern is wrapped in word boundaries, and
		 * substitutes from its own groups exactly as {@link #ANCHORED} does.
		 */
		PLAIN
	}

	private AliasExpansion() {
	}

	/**
	 * @param pre The alias pattern.
	 * @return Which substitution rule applies; {@link Mode#PLAIN} for null.
	 */
	public static Mode modeFor(final String pre) {
		if (pre == null) {
			return Mode.PLAIN;
		}
		boolean start = pre.startsWith("^");
		boolean end = pre.endsWith("$");
		if (start && end) {
			return Mode.ANCHORED;
		}
		if (start) {
			return Mode.WORD_SPLIT;
		}
		return Mode.PLAIN;
	}

	/**
	 * Capture map for the word-splitting form: every word of the typed line,
	 * numbered from zero.
	 *
	 * @param wholeInput The line the player typed.
	 * @return Word index (as a string) to word.
	 */
	public static Map<String, String> wordCaptures(final String wholeInput) {
		Map<String, String> map = new HashMap<String, String>();
		if (wholeInput == null) {
			return map;
		}
		String[] parts = WHITESPACE.split(wholeInput);
		for (int i = 0; i < parts.length; i++) {
			map.put(Integer.toString(i), parts[i]);
		}
		return map;
	}

	/**
	 * The text an alias expands to, before any script handling.
	 *
	 * @param alias The alias that matched.
	 * @param wholeInput The whole line the player typed.
	 * @param matched Just the part the alias matched.
	 * @return The replacement text, or "" when there is nothing to expand.
	 */
	public static String expand(final AliasData alias, final String wholeInput,
			final String matched) {
		return expand(alias, wholeInput, matched, null);
	}

	/**
	 * As {@link #expand(AliasData, String, String)}, also substituting session
	 * variables written as <code>${name}</code>.
	 *
	 * <p>Captures first, then variables, so a variable holding something like
	 * {@code $1} is treated as text rather than re-substituted.
	 *
	 * @param alias The alias that matched.
	 * @param wholeInput The whole line the player typed.
	 * @param matched Just the part the alias matched.
	 * @param variables Session variables, or null for none.
	 * @return The replacement text.
	 */
	public static String expand(final AliasData alias, final String wholeInput,
			final String matched, final java.util.Map<String, String> variables) {
		String expanded = expandCaptures(alias, wholeInput, matched);
		return com.resurrection.blowtorch2.lib.responder.VariableSubstitution
				.apply(expanded, variables);
	}

	/**
	 * The capture map {@link #expand} spends for {@code $1} in {@code With},
	 * and that Set Variable on the same alias uses.
	 *
	 * <p>Word-split uses the whole typed line; the other two forms re-run the
	 * alias pattern against the matched text.
	 */
	public static Map<String, String> captures(final AliasData alias,
			final String wholeInput, final String matched) {
		if (alias == null) {
			return new HashMap<String, String>();
		}
		switch (modeFor(alias.getPre())) {
		case WORD_SPLIT:
			return wordCaptures(wholeInput);
		case ANCHORED:
			return AnchoredAliasCaptures.fromMatch(alias.getPre(), matched);
		default:
			return AnchoredAliasCaptures.fromMatch(alias.getPre(), matched);
		}
	}

	private static String expandCaptures(final AliasData alias, final String wholeInput,
			final String matched) {
		if (alias == null) {
			return "";
		}
		String post = alias.getPost();
		if (post == null) {
			return "";
		}
		return CaptureSubstitution.apply(post, captures(alias, wholeInput, matched));
	}
}
