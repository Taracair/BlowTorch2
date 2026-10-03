package com.resurrection.blowtorch2.lib.window;

import java.text.BreakIterator;
import java.util.ArrayList;

/**
 * Where to break a long input word so the rest of the line is used.
 * The mark is a line break only. Callers strip it before send, copy
 * and history.
 */
public final class InputHyphenBreaks {

	/** Zero-width space. A word break for the field, not a character to send. */
	public static final char MARK = '\u200b';

	public static final int[] NO_CUTS = new int[0];

	/** One edit against the original string. Apply from the highest index down. */
	public static final class Op {
		public final int index;
		public final boolean insert;

		Op(final int index, final boolean insert) {
			this.index = index;
			this.insert = insert;
		}
	}

	public interface Measurer {
		float width(String text, int start, int end);
	}

	private InputHyphenBreaks() {
	}

	public static boolean contains(final CharSequence text) {
		if (text == null) {
			return false;
		}
		for (int i = 0; i < text.length(); i++) {
			if (text.charAt(i) == MARK) {
				return true;
			}
		}
		return false;
	}

	public static String strip(final String text) {
		if (text == null || text.indexOf(MARK) < 0) {
			return text == null ? "" : text;
		}
		StringBuilder out = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c != MARK) {
				out.append(c);
			}
		}
		return out.toString();
	}

	/**
	 * The grapheme that contains {@code index}, so a delete does not keep
	 * half an emoji. {@code [start, end)}.
	 */
	public static int[] clusterRange(final String s, final int index) {
		if (s == null || s.length() == 0) {
			return new int[] { 0, 0 };
		}
		int at = index < 0 ? 0 : index;
		if (at >= s.length()) {
			at = s.length() - 1;
		}
		BreakIterator it = BreakIterator.getCharacterInstance();
		it.setText(s);
		int start = it.isBoundary(at) ? at : it.preceding(at);
		if (start == BreakIterator.DONE) {
			start = 0;
		}
		int end = it.following(start);
		if (end == BreakIterator.DONE) {
			end = s.length();
		}
		return new int[] { start, end };
	}

	/**
	 * Logical offset of a buffer index. Marks do not count.
	 */
	public static int logicalIndex(final String raw, final int buffer) {
		if (raw == null || buffer <= 0) {
			return 0;
		}
		int end = Math.min(buffer, raw.length());
		int seen = 0;
		for (int i = 0; i < end; i++) {
			if (raw.charAt(i) != MARK) {
				seen++;
			}
		}
		return seen;
	}

	/**
	 * Buffer index of a logical offset. Marks already sitting on that
	 * boundary count as the same spot, so a second pass does not move them.
	 */
	public static int bufferIndex(final String raw, final int logical) {
		if (raw == null || logical <= 0) {
			return 0;
		}
		int seen = 0;
		for (int i = 0; i < raw.length(); i++) {
			if (seen == logical) {
				return i;
			}
			if (raw.charAt(i) != MARK) {
				seen++;
			}
		}
		return raw.length();
	}

	/**
	 * Logical offsets where a mark should sit. {@code lineWidth} is the
	 * inner width of the field, already clear of Edit and Send.
	 */
	public static int[] cuts(final String logical, final float lineWidth,
			final float hyphenWidth, final int minLead, final int minTail,
			final boolean polish, final Measurer measurer) {
		if (logical == null || logical.length() == 0 || lineWidth <= 0f
				|| measurer == null) {
			return NO_CUTS;
		}
		int lead = minLead < 1 ? 1 : minLead;
		int tail = minTail < 1 ? 1 : minTail;
		ArrayList<Integer> found = new ArrayList<Integer>();
		int n = logical.length();
		int i = 0;
		float x = 0f;
		while (i < n) {
			char c = logical.charAt(i);
			if (c == '\n') {
				x = 0f;
				i++;
				continue;
			}
			if (isGap(c)) {
				int j = i + 1;
				while (j < n && isGap(logical.charAt(j))) {
					j++;
				}
				float w = measurer.width(logical, i, j);
				// A gap that does not fit stays on this line. The next word
				// starts at column 0, the same as the field's own breaker.
				if (x > 0f && x + w > lineWidth) {
					x = 0f;
				} else {
					x += w;
					if (x > lineWidth) {
						x = 0f;
					}
				}
				i = j;
				continue;
			}
			int j = i + 1;
			while (j < n && logical.charAt(j) != '\n' && !isGap(logical.charAt(j))) {
				j++;
			}
			x = placeWord(logical, i, j, x, lineWidth, hyphenWidth, lead, tail,
					polish, measurer, found);
			i = j;
		}
		if (found.isEmpty()) {
			return NO_CUTS;
		}
		int[] out = new int[found.size()];
		for (int k = 0; k < out.length; k++) {
			out[k] = found.get(k).intValue();
		}
		return out;
	}

	/**
	 * Edits that make {@code raw} carry {@code cuts}. Empty when it already
	 * does. A composing span is left alone.
	 */
	public static Op[] reconcile(final String raw, final int[] cuts,
			final int composeStart, final int composeEnd) {
		String text = raw == null ? "" : raw;
		int cs = composeStart;
		int ce = composeEnd;
		if (cs < 0 || ce < 0) {
			cs = -1;
			ce = -1;
		} else if (cs > ce) {
			int swap = cs;
			cs = ce;
			ce = swap;
		}
		boolean[] want = new boolean[text.length() + 1];
		if (cuts != null) {
			for (int i = 0; i < cuts.length; i++) {
				int cut = cuts[i];
				if (cut <= 0) {
					continue;
				}
				int at = bufferIndex(text, cut);
				if (inside(at, cs, ce)) {
					continue;
				}
				if (at >= 0 && at <= text.length()) {
					want[at] = true;
				}
			}
		}
		ArrayList<Op> ops = new ArrayList<Op>();
		for (int i = text.length(); i >= 0; i--) {
			boolean isMark = i < text.length() && text.charAt(i) == MARK;
			if (isMark && !want[i] && !inside(i, cs, ce)) {
				ops.add(new Op(i, false));
			} else if (want[i] && !isMark) {
				ops.add(new Op(i, true));
			}
		}
		if (ops.isEmpty()) {
			return NO_OPS;
		}
		return ops.toArray(new Op[ops.size()]);
	}

	private static final Op[] NO_OPS = new Op[0];

	private static boolean inside(final int index, final int start, final int end) {
		return start >= 0 && index >= start && index < end;
	}

	private static float placeWord(final String logical, final int start, final int end,
			final float x, final float lineWidth, final float hyphenWidth,
			final int minLead, final int minTail, final boolean polish,
			final Measurer measurer, final ArrayList<Integer> found) {
		int at = start;
		float cursor = x;
		while (at < end) {
			float word = measurer.width(logical, at, end);
			if (cursor > 0f && cursor + word <= lineWidth) {
				return cursor + word;
			}
			float room = cursor > 0f ? lineWidth - cursor : lineWidth;
			if (cursor > 0f) {
				int cut = choose(logical, at, end, room, hyphenWidth, minLead, minTail,
						polish, measurer);
				if (cut < 0) {
					cursor = 0f;
					continue;
				}
				found.add(Integer.valueOf(cut));
				at = cut;
				cursor = 0f;
				continue;
			}
			if (word <= lineWidth) {
				return word;
			}
			int cut = choose(logical, at, end, lineWidth, hyphenWidth, minLead, minTail,
					polish, measurer);
			if (cut < 0) {
				return wrappedTail(logical, at, end, lineWidth, measurer);
			}
			found.add(Integer.valueOf(cut));
			at = cut;
		}
		return cursor;
	}

	/**
	 * Width of the last line after a token the breaker wraps per character.
	 * Digits are not hyphenated, and the piece that spills still takes columns.
	 */
	private static float wrappedTail(final String s, final int start, final int end,
			final float lineWidth, final Measurer measurer) {
		float used = 0f;
		for (int i = start; i < end; i++) {
			float w = measurer.width(s, i, i + 1);
			if (w <= 0f) {
				continue;
			}
			if (used > 0f && used + w > lineWidth) {
				used = 0f;
			}
			used += w;
		}
		return used > lineWidth ? 0f : used;
	}

	private static int choose(final String s, final int start, final int end,
			final float room, final float hyphenWidth, final int minLead,
			final int minTail, final boolean polish, final Measurer measurer) {
		if (room <= 0f || !hasLetter(s, start, end)) {
			return -1;
		}
		if (start + minLead > end - minTail) {
			return -1;
		}
		int best = -1;
		int bestNice = -1;
		int bestTyped = -1;
		int last = end - minTail;
		for (int i = start + minLead; i <= last; i++) {
			if (splitsCluster(s, i)) {
				continue;
			}
			boolean typedHyphen = isTypedHyphen(s.charAt(i - 1));
			if (polish && !typedHyphen && i < s.length() && digraph(s.charAt(i - 1), s.charAt(i))) {
				continue;
			}
			float need = measurer.width(s, start, i);
			if (!typedHyphen) {
				need += hyphenWidth;
			}
			if (need > room) {
				continue;
			}
			best = i;
			if (typedHyphen) {
				bestTyped = i;
			} else if (nice(s, i, polish)) {
				bestNice = i;
			}
		}
		if (bestTyped >= 0) {
			return bestTyped;
		}
		return bestNice >= 0 ? bestNice : best;
	}

	/** A cut here would leave half an emoji or a dangling accent. */
	private static boolean splitsCluster(final String s, final int i) {
		char prev = s.charAt(i - 1);
		char next = s.charAt(i);
		if (Character.isHighSurrogate(prev) || Character.isLowSurrogate(next)) {
			return true;
		}
		if (prev == '\u200d' || next == '\u200d' || prev == '\ufe0f' || next == '\ufe0f') {
			return true;
		}
		int type = Character.getType(next);
		return type == Character.NON_SPACING_MARK
				|| type == Character.ENCLOSING_MARK
				|| type == Character.COMBINING_SPACING_MARK;
	}

	private static boolean nice(final String s, final int i, final boolean polish) {
		if (i <= 0 || i >= s.length()) {
			return false;
		}
		char prev = s.charAt(i - 1);
		char next = s.charAt(i);
		if (!Character.isLetter(prev) || !Character.isLetter(next)) {
			return false;
		}
		if (polish && digraph(prev, next)) {
			return false;
		}
		// After a vowel, before a consonant.
		if (vowel(prev) && !vowel(next)) {
			if (!polish) {
				return true;
			}
			return i + 1 < s.length() && vowel(s.charAt(i + 1));
		}
		// Between two consonants that sit between vowels: super|cal.
		if (i >= 2 && i + 1 < s.length()) {
			char before = s.charAt(i - 2);
			char after = s.charAt(i + 1);
			return vowel(before) && !vowel(prev) && !vowel(next) && vowel(after);
		}
		return false;
	}

	private static boolean digraph(final char a, final char b) {
		char x = Character.toLowerCase(a);
		char y = Character.toLowerCase(b);
		if (x == 's' && y == 'z') {
			return true;
		}
		if (x == 'c' && (y == 'z' || y == 'h')) {
			return true;
		}
		if (x == 'r' && y == 'z') {
			return true;
		}
		return x == 'd' && (y == 'z' || y == '\u017a' || y == '\u017c');
	}

	private static boolean vowel(final char c) {
		switch (Character.toLowerCase(c)) {
		case 'a':
		case 'e':
		case 'i':
		case 'o':
		case 'u':
		case 'y':
		case '\u0105':
		case '\u0119':
		case '\u00f3':
			return true;
		default:
			return false;
		}
	}

	private static boolean isTypedHyphen(final char c) {
		return c == '-' || c == '\u2010';
	}

	private static boolean hasLetter(final String s, final int start, final int end) {
		for (int i = start; i < end; i++) {
			if (Character.isLetter(s.charAt(i))) {
				return true;
			}
		}
		return false;
	}

	private static boolean isGap(final char c) {
		return c != '\n' && Character.isWhitespace(c);
	}
}
