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
 */
public final class WordSuggestions {

	/** How many distinct words to remember. Oldest fall out first. */
	public static final int DEFAULT_MAX_WORDS = 500;

	/**
	 * Shorter than this is not worth completing — you have typed most of it by
	 * the time the suggestion appears, and short words are the common ones that
	 * would crowd out the useful proper nouns.
	 */
	public static final int MIN_WORD_LENGTH = 4;

	/** Below this many typed characters, everything matches and nothing helps. */
	public static final int MIN_PREFIX_LENGTH = 2;

	/**
	 * Insertion-ordered, so iterating backwards gives newest first. Keyed by the
	 * lower-cased word, valued by the spelling as it appeared — a player is
	 * "Tonkatsu" and the completion should say so.
	 */
	private final LinkedHashMap<String, String> words =
			new LinkedHashMap<String, String>();

	private final int maxWords;

	public WordSuggestions() {
		this(DEFAULT_MAX_WORDS);
	}

	public WordSuggestions(final int maxWords) {
		this.maxWords = maxWords > 0 ? maxWords : DEFAULT_MAX_WORDS;
	}

	/**
	 * Take the words out of a line the game sent.
	 *
	 * @param text any incoming text; null and empty are ignored.
	 */
	public void learn(final String text) {
		if (text == null || text.length() == 0) {
			return;
		}
		int start = -1;
		for (int i = 0; i <= text.length(); i++) {
			boolean part = i < text.length() && isWordChar(text.charAt(i));
			if (part && start < 0) {
				start = i;
			} else if (!part && start >= 0) {
				addWord(text.substring(start, i));
				start = -1;
			}
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
		// than keeping its original position.
		words.remove(key);
		words.put(key, raw);
		if (words.size() > maxWords) {
			java.util.Iterator<String> it = words.keySet().iterator();
			it.next();
			it.remove();
		}
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
		List<Map.Entry<String, String>> all =
				new ArrayList<Map.Entry<String, String>>(words.entrySet());
		for (int i = all.size() - 1; i >= 0 && out.size() < max; i--) {
			Map.Entry<String, String> e = all.get(i);
			// Not the word you have already finished typing.
			if (e.getKey().startsWith(needle) && e.getKey().length() > needle.length()) {
				out.add(e.getValue());
			}
		}
		return out;
	}

	/** Everything learned so far is dropped — a new world, a new vocabulary. */
	public void clear() {
		words.clear();
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
