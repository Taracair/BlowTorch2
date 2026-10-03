package com.resurrection.blowtorch2.lib.service;

import java.text.Normalizer;

/**
 * Folds Latin diacritics on the outbound wire. {@code ł}/{@code ß}/{@code æ}
 * need an explicit map: they do not NFD to ASCII.
 */
public final class CommandUnaccent {

	private CommandUnaccent() {
	}

	/**
	 * Strip combining marks and map a small extra set. Null/empty unchanged.
	 * Returns the input instance when the result would be identical.
	 */
	public static String fold(final String s) {
		if (s == null || s.isEmpty()) {
			return s;
		}
		boolean mapped = false;
		StringBuilder mappedBuf = null;
		final int len = s.length();
		int i = 0;
		while (i < len) {
			final int cp = s.codePointAt(i);
			final int n = Character.charCount(cp);
			final String repl = mapExtra(cp);
			if (repl != null) {
				if (mappedBuf == null) {
					mappedBuf = new StringBuilder(len + 8);
					mappedBuf.append(s, 0, i);
				}
				mappedBuf.append(repl);
				mapped = true;
			} else if (mappedBuf != null) {
				mappedBuf.appendCodePoint(cp);
			}
			i += n;
		}
		final String afterMap = mappedBuf == null ? s : mappedBuf.toString();
		final String nfd = Normalizer.normalize(afterMap, Normalizer.Form.NFD);
		StringBuilder stripped = null;
		final int nfdLen = nfd.length();
		int j = 0;
		while (j < nfdLen) {
			final int cp = nfd.codePointAt(j);
			final int n = Character.charCount(cp);
			if (isCombiningMark(cp)) {
				if (stripped == null) {
					stripped = new StringBuilder(nfdLen);
					stripped.append(nfd, 0, j);
				}
			} else if (stripped != null) {
				stripped.appendCodePoint(cp);
			}
			j += n;
		}
		// NFD without dropping a mark is not an accent strip (Hangul syllables).
		if (stripped == null && !mapped) {
			return s;
		}
		final String result = stripped == null ? nfd : stripped.toString();
		if (result.equals(s)) {
			return s;
		}
		return result;
	}

	/**
	 * Fold when the option is on and the password mask is not held.
	 *
	 * @param s outbound wire text
	 * @param enabled Options → Input → Strip accents when sending
	 * @param telnetLocalEcho true while the input bar shows typed text
	 */
	public static String foldForSend(final String s, final boolean enabled,
			final boolean telnetLocalEcho) {
		if (!enabled || !telnetLocalEcho) {
			return s;
		}
		return fold(s);
	}

	private static boolean isCombiningMark(final int cp) {
		final int type = Character.getType(cp);
		return type == Character.NON_SPACING_MARK
				|| type == Character.COMBINING_SPACING_MARK
				|| type == Character.ENCLOSING_MARK;
	}

	private static String mapExtra(final int cp) {
		switch (cp) {
			case 'ł':
				return "l";
			case 'Ł':
				return "L";
			case 'ø':
				return "o";
			case 'Ø':
				return "O";
			case 'ð':
				return "d";
			case 'Ð':
				return "D";
			case 'æ':
				return "ae";
			case 'Æ':
				return "AE";
			case 'ß':
				return "ss";
			case 'đ':
				return "d";
			case 'Đ':
				return "D";
			case 'ı':
				return "i";
			default:
				return null;
		}
	}
}
