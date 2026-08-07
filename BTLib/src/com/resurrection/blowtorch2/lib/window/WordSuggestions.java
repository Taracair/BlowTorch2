package com.resurrection.blowtorch2.lib.window;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Words the game has just used, offered back while you type.
 *
 * <p><b>Why this is not the keyboard's job.</b> Gboard completes from a language
 * dictionary and from what you have typed before, and it has no idea what is on
 * screen. The words that are slow to type in a MUD are exactly the ones it will
 * never know: a mob called <i>grizzled</i>, a player called <i>Tonkatsu</i>, an
 * item called <i>gnarled oaken staff</i>. Worse, it actively corrects them into
 * English — type "grizz" and it offers "grid", "grim", "grip".
 *
 * <p>So this keeps a small, recent vocabulary of what the world actually said,
 * and completes from that. Type {@code k gri} and it offers {@code grizzled},
 * because the mob walked in three lines ago.
 *
 * <p>Newest first, deliberately: the thing that just arrived is nearly always
 * the thing you are about to name. A bounded store, because this lives in the UI
 * process for the length of a session.
 *
 * <p><b>What "recent" counts in.</b> Lines, not words. A word count is a poor
 * proxy: a quiet hour of a few lines keeps names from hours ago alive, while one
 * noisy room description evicts everything you were just looking at. The window
 * is the last {@link #DEFAULT_MAX_LINES} lines the world sent, so "recent" means
 * the same thing here as it does on screen. The word cap below is only a memory
 * backstop for a world that sends very wide lines.
 */
public final class WordSuggestions {

	/**
	 * Hard cap on distinct words, as a memory backstop only — the line window is
	 * the rule. Set high enough that a normal 300 lines (roughly 800–1500
	 * distinct words past {@link #MIN_WORD_LENGTH}) never reaches it, or the cap
	 * would silently become the real rule again.
	 */
	public static final int DEFAULT_MAX_WORDS = 4000;

	/**
	 * How many of the world's most recent lines count as "fresh". Player-settable;
	 * see {@link #setMaxLines}.
	 *
	 * <p>The one place this number is written down. The option default, the
	 * "unchanged, do not persist" comparison in the settings parser and the
	 * completer itself all read it from here: if they disagree the parser quietly
	 * stops saving the value the player chose, and nothing fails loudly.
	 */
	public static final int DEFAULT_MAX_LINES = 300;

	/** Above this a window is no longer a window. The one place this is written. */
	public static final int MAX_LINES = 5000;

	/**
	 * How many suggestions fit on the strip without it becoming a wall — and so
	 * how high {@code .complete N} goes. Written here rather than in the two
	 * processes that need it, which would drift.
	 */
	public static final int MAX_ON_STRIP = 6;

	/**
	 * Shorter than this is not worth completing — you have typed most of it by
	 * the time the suggestion appears, and short words are the common ones that
	 * would crowd out the useful proper nouns.
	 */
	public static final int MIN_WORD_LENGTH = 4;

	/** Below this many typed characters, everything matches and nothing helps. */
	public static final int MIN_PREFIX_LENGTH = 2;

	/** A word as it was spelled, and the line it was last seen on. */
	private static final class Seen {
		private final String spelling;
		private final int line;

		Seen(final String spelling, final int line) {
			this.spelling = spelling;
			this.line = line;
		}
	}

	/**
	 * Insertion-ordered, so iterating backwards gives newest first. Keyed by the
	 * lower-cased word, valued by the spelling as it appeared — a player is
	 * "Tonkatsu" and the completion should say so.
	 *
	 * <p>Insertion order is also line order: a word re-seen is removed and put
	 * back, so its stamp only ever moves forward. That is what lets the window be
	 * trimmed from the front instead of scanned.
	 */
	private final LinkedHashMap<String, Seen> words =
			new LinkedHashMap<String, Seen>();

	private final int maxWords;

	/** Lines the world has sent this session; the clock the window measures. */
	private int linesSeen = 0;

	private int maxLines = DEFAULT_MAX_LINES;

	/**
	 * The newest line that actually contained a word, which is what the window is
	 * measured back from. Not {@link #linesSeen}: a world that sends blank lines,
	 * or a chunk that ends in a newline, would otherwise push vocabulary out of
	 * the window without having said anything.
	 */
	private int lastWordLine = 0;

	public WordSuggestions() {
		this(DEFAULT_MAX_WORDS);
	}

	public WordSuggestions(final int maxWords) {
		this.maxWords = maxWords > 0 ? maxWords : DEFAULT_MAX_WORDS;
	}

	/**
	 * How many recent lines count as fresh.
	 *
	 * @param lines the window; zero or less turns the window off, leaving only
	 *        the word cap — for a player who wants everything the session said.
	 */
	public void setMaxLines(final int lines) {
		this.maxLines = lines;
		prune();
	}

	public int getMaxLines() {
		return maxLines;
	}

	/** How many lines the world has sent since this completer started. */
	public int linesSeen() {
		return linesSeen;
	}

	/**
	 * Take the words out of text the game sent.
	 *
	 * @param text any incoming text; null and empty are ignored. May be several
	 *        lines — this arrives as whole TCP chunks, not a line at a time — so
	 *        the newlines inside it are what advances the window.
	 */
	public void learn(final String text) {
		if (text == null || text.length() == 0) {
			return;
		}
		int start = -1;
		for (int i = 0; i <= text.length(); i++) {
			char c = i < text.length() ? text.charAt(i) : '\n';
			boolean part = i < text.length() && isWordChar(c);
			if (part && start < 0) {
				start = i;
			} else if (!part && start >= 0) {
				addWord(text.substring(start, i));
				start = -1;
			}
			// After the word closes: a word ending at the newline still belongs
			// to the line it ended.
			if (i < text.length() && c == '\n') {
				linesSeen++;
			}
		}
		prune();
	}

	/** Drop what has fallen out of the window, then out of the word cap. */
	private void prune() {
		java.util.Iterator<Map.Entry<String, Seen>> it = words.entrySet().iterator();
		if (maxLines > 0) {
			while (it.hasNext()) {
				if (it.next().getValue().line > lastWordLine - maxLines) {
					break;
				}
				it.remove();
			}
		}
		while (words.size() > maxWords) {
			java.util.Iterator<String> keys = words.keySet().iterator();
			keys.next();
			keys.remove();
		}
	}

	private static boolean isWordChar(final char c) {
		return Character.isLetterOrDigit(c) || c == '\'' || c == '-';
	}

	private void addWord(final String raw) {
		if (raw.length() < MIN_WORD_LENGTH) {
			return;
		}
		// All-digits is a number, not a name: "1234" completes nothing useful
		// and pushes real words out of a bounded store.
		boolean anyLetter = false;
		for (int i = 0; i < raw.length(); i++) {
			if (Character.isLetter(raw.charAt(i))) {
				anyLetter = true;
				break;
			}
		}
		if (!anyLetter) {
			return;
		}
		String key = raw.toLowerCase(Locale.US);
		// Remove before put so a word seen again moves to the newest end rather
		// than keeping its original position. It also gets today's line stamp, so
		// a name the world keeps repeating never falls out of the window.
		words.remove(key);
		words.put(key, new Seen(raw, linesSeen));
		lastWordLine = linesSeen;
	}

	/**
	 * Completions for what is being typed, newest first.
	 *
	 * @param prefix the partial word; shorter than {@link #MIN_PREFIX_LENGTH}
	 *        gives nothing.
	 * @param max how many to return.
	 * @return never null, possibly empty.
	 */
	public List<String> suggest(final String prefix, final int max) {
		List<String> out = new ArrayList<String>();
		if (prefix == null || prefix.length() < MIN_PREFIX_LENGTH || max <= 0) {
			return out;
		}
		String needle = prefix.toLowerCase(Locale.US);
		// Forward once collecting only matches, rather than copying the whole
		// store to walk it backwards. This runs on every keystroke, and a line
		// window holds several times what the old 500-word cap did, so the cost
		// has to follow the number of matches and not the size of the vocabulary.
		List<String> matches = new ArrayList<String>();
		for (Map.Entry<String, Seen> e : words.entrySet()) {
			// Not the word you have already finished typing.
			if (e.getKey().length() > needle.length() && e.getKey().startsWith(needle)) {
				matches.add(e.getValue().spelling);
			}
		}
		for (int i = matches.size() - 1; i >= 0 && out.size() < max; i--) {
			out.add(matches.get(i));
		}
		return out;
	}

	/** Everything learned so far is dropped — a new world, a new vocabulary. */
	public void clear() {
		words.clear();
		linesSeen = 0;
		lastWordLine = 0;
	}

	public int size() {
		return words.size();
	}

	/**
	 * The partial word immediately before the caret, which is what a completion
	 * would replace.
	 *
	 * @param text the input bar's contents.
	 * @param caret where the cursor is.
	 * @return the partial word, empty when the caret is not at the end of one.
	 */
	public static String wordBefore(final String text, final int caret) {
		if (text == null) {
			return "";
		}
		int end = caret;
		if (end < 0) {
			end = 0;
		}
		if (end > text.length()) {
			end = text.length();
		}
		int start = end;
		while (start > 0 && isWordChar(text.charAt(start - 1))) {
			start--;
		}
		return text.substring(start, end);
	}

	/** The new contents and caret after accepting a completion. */
	public static final class Completion {
		private final String text;
		private final int caret;

		Completion(final String text, final int caret) {
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

	/**
	 * Replace the partial word before the caret with the whole one, and leave a
	 * space so the next word can be typed straight away.
	 *
	 * @param text the input bar's contents.
	 * @param caret where the cursor is.
	 * @param word the completion that was chosen.
	 * @return the new text and caret; never null.
	 */
	public static Completion complete(final String text, final int caret,
			final String word) {
		String existing = text == null ? "" : text;
		if (word == null || word.length() == 0) {
			return new Completion(existing, caret);
		}
		int end = caret;
		if (end < 0) {
			end = 0;
		}
		if (end > existing.length()) {
			end = existing.length();
		}
		int start = end;
		while (start > 0 && isWordChar(existing.charAt(start - 1))) {
			start--;
		}
		StringBuilder out = new StringBuilder();
		out.append(existing, 0, start);
		out.append(word);
		int newCaret = out.length();
		String after = existing.substring(end);
		if (after.length() == 0 || !Character.isWhitespace(after.charAt(0))) {
			out.append(' ');
			newCaret = out.length();
		}
		out.append(after);
		return new Completion(out.toString(), newCaret);
	}
}
