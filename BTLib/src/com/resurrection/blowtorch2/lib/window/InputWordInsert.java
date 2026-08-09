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
		if (before.length() > 0 && !endsWithSpace(before)) {
			out.append(' ');
		}
		out.append(insert);
		int caret = out.length();
		// A trailing space unless what follows already brings one. At the end of
		// the line it is added anyway, so a second tap chains without the player
		// reaching for the space bar -- which is the whole saving on a phone.
		//
		// That space is sent: Connection.processOutputData appends the command
		// verbatim (mDataToServer.append(d.mCmdString + mCRLF)) and nothing on
		// that path trims it. Checked, not assumed. "kill troll " is fine on
		// every world I know of, and the player can see and delete it; trimming
		// the outgoing line globally to tidy this up would change what every
		// other command sends, which is far too wide a fix for a cosmetic edge.
		if (after.length() == 0 || !startsWithSpace(after)) {
			out.append(' ');
			caret = out.length();
		}
		out.append(after);
		return new Result(out.toString(), caret);
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
