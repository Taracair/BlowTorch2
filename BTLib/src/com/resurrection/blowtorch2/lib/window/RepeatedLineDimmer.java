package com.resurrection.blowtorch2.lib.window;

import java.util.Iterator;
import java.util.LinkedHashSet;

/**
 * Line-level memory for "dim repeated room text". Remembers the last
 * {@link #WINDOW_SIZE} long stripped lines and says whether the one just
 * offered was already in that window.
 *
 * <p>Pure Java: the caller strips ANSI and passes plain text. Short lines
 * (prompts, "Ok.", "You sit.") are neither remembered nor dimmed.
 */
public final class RepeatedLineDimmer {

	public static final int WINDOW_SIZE = 60;
	public static final int MIN_CHARS = 24;

	private final LinkedHashSet<String> window = new LinkedHashSet<String>();

	/**
	 * @param stripped plain text of one finished line (no ANSI). Whitespace is
	 *        collapsed here.
	 * @return true when this long line was already in the window
	 */
	public boolean rememberAndShouldDim(final String stripped) {
		if (stripped == null) {
			return false;
		}
		final String key = collapseWhitespace(stripped);
		if (key.length() == 0) {
			return false;
		}
		if (key.length() < MIN_CHARS) {
			return false;
		}
		if (window.contains(key)) {
			return true;
		}
		if (window.size() >= WINDOW_SIZE) {
			final Iterator<String> it = window.iterator();
			it.next();
			it.remove();
		}
		window.add(key);
		return false;
	}

	/** Trim, then collapse any run of whitespace to a single space. */
	static String collapseWhitespace(final String s) {
		final int n = s.length();
		final StringBuilder out = new StringBuilder(n);
		boolean pendingSpace = false;
		boolean any = false;
		for (int i = 0; i < n; i++) {
			final char c = s.charAt(i);
			if (c <= ' ' || Character.isWhitespace(c)) {
				if (any) {
					pendingSpace = true;
				}
			} else {
				if (pendingSpace) {
					out.append(' ');
					pendingSpace = false;
				}
				out.append(c);
				any = true;
			}
		}
		return out.toString();
	}
}
