package com.resurrection.blowtorch2.lib.window;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Screen-pick tokens: letters and digits only. Hyphen, apostrophe and
 * punctuation split ({@code iron-helmet} is two words). Not
 * {@link WordSuggestions#isWordChar}, which keeps {@code '} and {@code -}.
 */
public final class AlnumWordAt {

	public static final class Span {
		public final int start;
		public final int end;
		public final String text;

		Span(final int start, final int end, final String text) {
			this.start = start;
			this.end = end;
			this.text = text;
		}
	}

	private AlnumWordAt() {
	}

	static boolean isWordChar(final char c) {
		return Character.isLetterOrDigit(c);
	}

	/**
	 * Word covering {@code index}, or null if that cell is not a letter or
	 * digit (space, hyphen, out of range).
	 */
	public static String at(final String line, final int index) {
		Span span = spanAt(line, index);
		return span == null ? null : span.text;
	}

	public static Span spanAt(final String line, final int index) {
		if (line == null || index < 0 || index >= line.length()) {
			return null;
		}
		if (!isWordChar(line.charAt(index))) {
			return null;
		}
		int start = index;
		while (start > 0 && isWordChar(line.charAt(start - 1))) {
			start--;
		}
		int end = index + 1;
		while (end < line.length() && isWordChar(line.charAt(end))) {
			end++;
		}
		return new Span(start, end, line.substring(start, end));
	}

	public static List<Span> all(final String line) {
		if (line == null || line.length() == 0) {
			return Collections.emptyList();
		}
		ArrayList<Span> out = new ArrayList<Span>();
		int i = 0;
		while (i < line.length()) {
			if (!isWordChar(line.charAt(i))) {
				i++;
				continue;
			}
			int start = i;
			i++;
			while (i < line.length() && isWordChar(line.charAt(i))) {
				i++;
			}
			out.add(new Span(start, i, line.substring(start, i)));
		}
		return out;
	}
}
