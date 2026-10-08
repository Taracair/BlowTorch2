package com.resurrection.blowtorch2.lib.window;

/**
 * Literal pattern and list name for a trigger opened from the copy widget.
 * A blank selection is null. Newlines stay in the pattern. The name is the
 * first line, trimmed, and cut at {@link #NAME_LIMIT} characters.
 */
public final class SelectionTriggerSeed {

	public static final int NAME_LIMIT = 48;

	public static final class Seed {
		public final String pattern;
		public final String name;

		Seed(final String pattern, final String name) {
			this.pattern = pattern;
			this.name = name;
		}
	}

	private SelectionTriggerSeed() {
	}

	public static Seed from(final String raw) {
		if (raw == null) {
			return null;
		}
		String pattern = trimEnds(raw.replace("\r\n", "\n").replace('\r', '\n'));
		if (pattern.length() == 0) {
			return null;
		}
		String first = pattern;
		int nl = pattern.indexOf('\n');
		if (nl >= 0) {
			first = pattern.substring(0, nl).trim();
		}
		if (first.length() == 0) {
			first = "trigger";
		} else if (first.length() > NAME_LIMIT) {
			first = first.substring(0, NAME_LIMIT).trim();
			if (first.length() == 0) {
				first = "trigger";
			}
		}
		return new Seed(pattern, first);
	}

	private static String trimEnds(final String s) {
		int a = 0;
		int b = s.length();
		while (a < b && isTrim(s.charAt(a))) {
			a++;
		}
		while (b > a && isTrim(s.charAt(b - 1))) {
			b--;
		}
		return s.substring(a, b);
	}

	private static boolean isTrim(final char c) {
		return c == ' ' || c == '\n' || c == '\t' || c == '\r';
	}
}
