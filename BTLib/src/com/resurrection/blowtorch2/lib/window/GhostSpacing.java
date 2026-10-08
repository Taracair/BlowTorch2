package com.resurrection.blowtorch2.lib.window;

/**
 * Where a dimmed suggestion sits against the typed text.
 *
 * <p>The rest of the current word stays glued ({@code gri} + {@code zzled}).
 * A different word gets one space ({@code kill} + {@code goblin}), unless the
 * caret is already after a space. That space is drawn only; accepting inserts
 * a real one. A near-miss still replaces the typed token.
 */
public final class GhostSpacing {

	/** The bar after a suggestion is taken, and where the caret goes. */
	public static final class Accepted {
		private final String text;
		private final int caret;

		Accepted(final String text, final int caret) {
			this.text = text;
			this.caret = caret;
		}

		public String text() {
			return text;
		}

		public int caret() {
			return caret;
		}
	}

	private GhostSpacing() {
	}

	/**
	 * The suggestion is the rest of {@code prefix} (or a longer phrase that
	 * starts with it). An empty prefix is a new word, not a continuation:
	 * every string starts with {@code ""}.
	 */
	public static boolean continues(final String prefix, final String suggestion) {
		if (prefix == null || prefix.length() == 0 || suggestion == null) {
			return false;
		}
		return suggestion.length() > prefix.length()
				&& suggestion.toLowerCase(java.util.Locale.US)
						.startsWith(prefix.toLowerCase(java.util.Locale.US));
	}

	/**
	 * What to draw for this suggestion. {@code continuation} means {@code body}
	 * is only the missing tail, so it stays glued. Otherwise one leading space
	 * when {@link InputWordInsert} would put one before a real insert.
	 */
	public static String draw(final String textBefore, final String body,
			final boolean continuation) {
		if (body == null || body.length() == 0) {
			return body;
		}
		if (continuation || Character.isWhitespace(body.charAt(0))) {
			return body;
		}
		String before = InputHyphenBreaks.strip(textBefore);
		if (InputWordInsert.needsLeadingSpace(before, body)) {
			return " " + body;
		}
		return body;
	}

	/**
	 * A packed extra is a whole word. One space before the first of them
	 * unless the typed text already ends on one, or on an opener.
	 */
	public static boolean gapBeforeNewWord(final String textBefore) {
		String before = InputHyphenBreaks.strip(textBefore);
		if (before.length() == 0) {
			return false;
		}
		return InputWordInsert.needsLeadingSpace(before, "x");
	}

	/**
	 * Text and caret after the player takes {@code word}.
	 *
	 * <p>{@code caret} is an index into {@code text} after hyphen marks are
	 * stripped, the same index {@link WordSuggestions#complete} uses.
	 */
	public static Accepted accept(final String text, final int caret,
			final String word) {
		String plain = text == null ? "" : InputHyphenBreaks.strip(text);
		if (word == null || word.length() == 0) {
			WordSuggestions.Completion c = WordSuggestions.complete(plain, caret, word);
			return new Accepted(c.text(), c.caret());
		}
		if (word.trim().length() == 0) {
			int at = caret < 0 ? 0 : caret;
			if (at > plain.length()) {
				at = plain.length();
			}
			return new Accepted(plain, at);
		}
		if (!keepsTypedWord(plain, caret, word)) {
			WordSuggestions.Completion c = WordSuggestions.complete(plain, caret, word);
			return new Accepted(c.text(), c.caret());
		}
		InputWordInsert.Result r = InputWordInsert.apply(plain, caret, caret, word);
		return new Accepted(r.text(), r.caret());
	}

	/**
	 * Keep the typed token and insert beside it. A continuation or a near-miss
	 * replaces that token instead.
	 */
	private static boolean keepsTypedWord(final String text, final int caret,
			final String word) {
		if (word == null || word.trim().length() == 0) {
			return false;
		}
		String prefix = WordSuggestions.wordBefore(text, caret);
		if (continues(prefix, word)) {
			return false;
		}
		String head = firstToken(word);
		if (prefix.length() > 0 && head.equalsIgnoreCase(prefix)) {
			return false;
		}
		if (isCorrection(prefix, head)) {
			return false;
		}
		if (prefix.length() == 0) {
			return InputWordInsert.needsLeadingSpace(beforeCaret(text, caret), head);
		}
		return true;
	}

	/** Typo, skipped head, or letters-in-order. Those replace the token. */
	private static boolean isCorrection(final String prefix, final String head) {
		if (prefix == null || prefix.length() == 0 || head == null || head.length() == 0) {
			return false;
		}
		String needle = prefix.toLowerCase(java.util.Locale.US);
		String word = head.toLowerCase(java.util.Locale.US);
		int distance = WordSuggestions.typoDistance(needle, word);
		if (distance >= 1 && distance <= 2) {
			return true;
		}
		int skip = WordSuggestions.skipPrefixRank(needle, word);
		if (skip == 1 || skip == 2) {
			return true;
		}
		return WordSuggestions.isSubsequence(needle, word);
	}

	private static String firstToken(final String word) {
		String trimmed = word.trim();
		int sp = trimmed.indexOf(' ');
		return sp < 0 ? trimmed : trimmed.substring(0, sp);
	}

	private static String beforeCaret(final String text, final int caret) {
		if (text == null || text.length() == 0) {
			return "";
		}
		int at = caret;
		if (at < 0) {
			at = 0;
		}
		if (at > text.length()) {
			at = text.length();
		}
		return text.substring(0, at);
	}
}
