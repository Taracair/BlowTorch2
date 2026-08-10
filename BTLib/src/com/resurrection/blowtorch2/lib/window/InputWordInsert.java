package com.resurrection.blowtorch2.lib.window;

/**
 * Where a tapped word lands in the input bar, and what spacing it needs.
 *
 * <p>Pure string work with no view in it, so the fiddly part — the four
 * combinations of "is there text before the caret" and "is there text after
 * it" — can be tested on the JVM instead of by retyping on a phone.
 *
 * <p>The rule is the one a player would describe: put the word where the caret
 * is, with exactly one space on each side that needs one, and leave the caret
 * after the word ready for the next thing they type. Typing {@code k}, tapping
 * <i>grizzled</i> and tapping <i>troll</i> has to produce {@code k grizzled
 * troll} and not {@code kgrizzledtroll} or {@code k  grizzled  troll}.
 *
 * <p>Punctuation is the exception. Closers and sentence marks attach to the
 * preceding word ({@code slowo} + {@code ,} → {@code slowo, }), and openers /
 * prefix sigils leave the next tap glued on ({@code (} + {@code foo} →
 * {@code (foo}).
 */
public final class InputWordInsert {

	/** The text the box should hold, and where the caret goes in it. */
	public static final class Result {
		private final String text;
		private final int caret;

		Result(final String text, final int caret) {
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

	private InputWordInsert() {
	}

	/**
	 * Work out the new contents of the input bar.
	 *
	 * @param current what the box holds now; null is treated as empty.
	 * @param selStart caret, or start of the selection the word replaces.
	 * @param selEnd end of that selection; equal to selStart for a plain caret.
	 * @param word the text to insert; null or blank changes nothing.
	 * @return the new text and caret, never null.
	 */
	public static Result apply(final String current, final int selStart,
			final int selEnd, final String word) {
		String existing = current == null ? "" : current;
		if (word == null || word.trim().length() == 0) {
			return new Result(existing, clamp(selEnd, existing.length()));
		}
		String insert = word.trim();

		// A selection is replaced, which is also what makes this safe to call
		// while the player has text selected: the word takes its place rather
		// than landing in the middle of it.
		int start = clamp(Math.min(selStart, selEnd), existing.length());
		int end = clamp(Math.max(selStart, selEnd), existing.length());

		String before = existing.substring(0, start);
		String after = existing.substring(end);

		StringBuilder out = new StringBuilder(before);
		if (needsLeadingSpace(before, insert)) {
			out.append(' ');
		}
		out.append(insert);
		int caret = out.length();
		// A trailing space unless what follows already brings one, or the
		// insert itself is an opener / prefix that wants the next token glued
		// on. At the end of the line the space is added for ordinary words, so
		// a second tap chains without the player reaching for the space bar --
		// which is the whole saving on a phone.
		//
		// That space is sent: Connection.processOutputData appends the command
		// verbatim (mDataToServer.append(d.mCmdString + mCRLF)) and nothing on
		// that path trims it. Checked, not assumed. "kill troll " is fine on
		// every world I know of, and the player can see and delete it; trimming
		// the outgoing line globally to tidy this up would change what every
		// other command sends, which is far too wide a fix for a cosmetic edge.
		if (needsTrailingSpace(insert, after)) {
			out.append(' ');
			caret = out.length();
		}
		out.append(after);
		return new Result(out.toString(), caret);
	}

	/**
	 * Space before the insert unless it is empty, already spaced, starts with a
	 * closer / sentence mark, or sits right after an opener / quote.
	 */
	private static boolean needsLeadingSpace(final String before,
			final String insert) {
		if (before.length() == 0 || endsWithSpace(before)) {
			return false;
		}
		if (attachesToPrevious(insert.charAt(0))) {
			return false;
		}
		if (opensNext(before.charAt(before.length() - 1))) {
			return false;
		}
		return true;
	}

	/**
	 * Space after the insert unless what follows already has one, or the insert
	 * ends with something that expects the next token glued on.
	 */
	private static boolean needsTrailingSpace(final String insert,
			final String after) {
		if (after.length() > 0 && startsWithSpace(after)) {
			return false;
		}
		if (expectsNextGlued(insert.charAt(insert.length() - 1))) {
			return false;
		}
		return true;
	}

	/**
	 * Characters that stick to the word before them: no leading space.
	 * Includes ASCII quotes (ambiguous open/close) on the leading side so
	 * {@code don't} + {@code '} still attaches, and typographic closers.
	 */
	private static boolean attachesToPrevious(final char c) {
		switch (c) {
		case ',':
		case '.':
		case ';':
		case ':':
		case '!':
		case '?':
		case ')':
		case ']':
		case '}':
		case '\'':
		case '"':
		case '`':
		case '\u00BB': // »
		case '\u201D': // ”
		case '\u2019': // ’
		case '\u2026': // …
			return true;
		default:
			return false;
		}
	}

	/**
	 * Character before the caret that makes the next insert attach with no
	 * leading space: openers, quotes, and the same prefix sigils as
	 * {@link #expectsNextGlued}. Needed so {@code @} + tap {@code bob} builds
	 * {@code @bob} after the first insert left the caret on {@code @}.
	 */
	private static boolean opensNext(final char c) {
		return expectsNextGlued(c);
	}

	/**
	 * No trailing space: the insert ends with an opener / quote, or with a
	 * prefix sigil. {@code @ # $} are included because in MUD input they
	 * introduce the next token the same way {@code (} does ({@code @} + tap
	 * name → {@code @name}); a trailing space would force a backspace before
	 * the second tap. {@code /} and {@code %} are deliberately left out: in
	 * game text they read as separator and unit far more often than as prefix
	 * ({@code n/s}, {@code 1/2}, {@code 50%}), and this same path serves
	 * tapping words, where a missing space silently welds two tapped words
	 * into one. A space that should not be there is visible and one backspace
	 * away; a space that should be there and is not costs a retyped command.
	 * ASCII {@code '} / {@code "} are ambiguous open/close —
	 * ending with one suppresses the trailing space (opener reading); starting
	 * with one suppresses the leading space (closer reading). A quote-only
	 * insert after a word therefore becomes {@code word"} with neither space.
	 */
	private static boolean expectsNextGlued(final char c) {
		switch (c) {
		case '(':
		case '[':
		case '{':
		case '\'':
		case '"':
		case '\u00AB': // «
		case '\u201C': // “
		case '\u2018': // ‘
		case '@':
		case '#':
		case '$':
			return true;
		default:
			return false;
		}
	}

	private static boolean endsWithSpace(final String s) {
		return Character.isWhitespace(s.charAt(s.length() - 1));
	}

	private static boolean startsWithSpace(final String s) {
		return Character.isWhitespace(s.charAt(0));
	}

	private static int clamp(final int value, final int max) {
		if (value < 0) {
			return 0;
		}
		if (value > max) {
			return max;
		}
		return value;
	}
}
