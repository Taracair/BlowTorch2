package com.resurrection.blowtorch2.lib.service.mxp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;

/** One {@code <...>} collected from the stream. */
public final class MxpTag {

	public final String name;
	public final boolean closing;
	public final boolean definition;
	public final boolean empty;
	public final LinkedHashMap<String, String> named;
	public final ArrayList<String> positional;
	public final ArrayList<String> flags;

	private MxpTag(final String name, final boolean closing, final boolean definition,
			final boolean empty, final LinkedHashMap<String, String> named,
			final ArrayList<String> positional, final ArrayList<String> flags) {
		this.name = name;
		this.closing = closing;
		this.definition = definition;
		this.empty = empty;
		this.named = named;
		this.positional = positional;
		this.flags = flags;
	}

	public String attr(final String key) {
		if (key == null) {
			return null;
		}
		String v = named.get(key.toLowerCase(Locale.US));
		if (v != null) {
			return v;
		}
		return null;
	}

	public String attrOrPos(final String key, final int pos) {
		String v = attr(key);
		if (v != null) {
			return v;
		}
		if (pos >= 0 && pos < positional.size()) {
			return positional.get(pos);
		}
		return null;
	}

	public boolean hasFlag(final String flag) {
		String want = flag.toLowerCase(Locale.US);
		for (int i = 0; i < flags.size(); i++) {
			if (flags.get(i).equals(want)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * @param inner text between {@code <} and {@code >}
	 * @return null when the tag does not start with a letter (or {@code /!} then letter)
	 */
	public static MxpTag parse(final String inner) {
		if (inner == null) {
			return null;
		}
		String s = inner.trim();
		if (s.length() == 0) {
			return null;
		}
		boolean closing = false;
		boolean definition = false;
		boolean empty = false;
		if (s.charAt(0) == '/') {
			closing = true;
			s = s.substring(1).trim();
		} else if (s.charAt(0) == '!') {
			definition = true;
			s = s.substring(1).trim();
		}
		if (s.endsWith("/")) {
			empty = true;
			s = s.substring(0, s.length() - 1).trim();
		}
		if (s.length() == 0) {
			return null;
		}
		int i = 0;
		if (!isNameStart(s.charAt(0))) {
			return null;
		}
		i = 1;
		while (i < s.length() && isNameChar(s.charAt(i))) {
			i++;
		}
		String name = s.substring(0, i);
		String rest = s.substring(i).trim();
		if (closing && rest.length() > 0) {
			// Closing tags do not take arguments — malformed.
			return null;
		}
		LinkedHashMap<String, String> named = new LinkedHashMap<String, String>();
		ArrayList<String> positional = new ArrayList<String>();
		ArrayList<String> flags = new ArrayList<String>();
		if (!parseArgs(rest, named, positional, flags)) {
			return null;
		}
		if (flagsContains(flags, "empty")) {
			empty = true;
		}
		return new MxpTag(name, closing, definition, empty, named, positional, flags);
	}

	public String canonical() {
		return name.toLowerCase(Locale.US);
	}

	private static boolean parseArgs(final String rest,
			final LinkedHashMap<String, String> named,
			final ArrayList<String> positional,
			final ArrayList<String> flags) {
		int i = 0;
		final int n = rest.length();
		while (i < n) {
			while (i < n && isSpace(rest.charAt(i))) {
				i++;
			}
			if (i >= n) {
				break;
			}
			char c = rest.charAt(i);
			if (c == '\'' || c == '"') {
				String quoted = readQuoted(rest, i);
				if (quoted == null) {
					return false;
				}
				positional.add(unquote(quoted));
				i += quoted.length();
				continue;
			}
			if (!isNameStart(c)) {
				return false;
			}
			int start = i;
			i++;
			while (i < n && isNameChar(rest.charAt(i))) {
				i++;
			}
			String word = rest.substring(start, i);
			while (i < n && isSpace(rest.charAt(i))) {
				i++;
			}
			if (i < n && rest.charAt(i) == '=') {
				i++;
				while (i < n && isSpace(rest.charAt(i))) {
					i++;
				}
				if (i >= n) {
					named.put(word.toLowerCase(Locale.US), "");
					break;
				}
				String val;
				if (rest.charAt(i) == '\'' || rest.charAt(i) == '"') {
					String quoted = readQuoted(rest, i);
					if (quoted == null) {
						return false;
					}
					val = unquote(quoted);
					i += quoted.length();
				} else {
					int vs = i;
					while (i < n && !isSpace(rest.charAt(i))) {
						i++;
					}
					val = rest.substring(vs, i);
				}
				named.put(word.toLowerCase(Locale.US), val);
			} else if (isBareFlag(word)) {
				flags.add(word.toLowerCase(Locale.US));
			} else {
				positional.add(word);
			}
		}
		return true;
	}

	private static boolean isBareFlag(final String word) {
		String w = word.toLowerCase(Locale.US);
		return w.equals("open") || w.equals("empty") || w.equals("delete")
				|| w.equals("private") || w.equals("publish") || w.equals("add")
				|| w.equals("remove") || w.equals("gag") || w.equals("enable")
				|| w.equals("disable") || w.equals("internal") || w.equals("redirect")
				|| w.equals("eof") || w.equals("eol") || w.equals("ismap")
				|| w.equals("prompt");
	}

	private static boolean flagsContains(final ArrayList<String> flags, final String want) {
		for (int i = 0; i < flags.size(); i++) {
			if (flags.get(i).equals(want)) {
				return true;
			}
		}
		return false;
	}

	private static String readQuoted(final String s, final int start) {
		char q = s.charAt(start);
		int i = start + 1;
		while (i < s.length()) {
			if (s.charAt(i) == q) {
				return s.substring(start, i + 1);
			}
			i++;
		}
		return null;
	}

	private static String unquote(final String quoted) {
		if (quoted.length() >= 2) {
			char a = quoted.charAt(0);
			char b = quoted.charAt(quoted.length() - 1);
			if ((a == '"' || a == '\'') && a == b) {
				return quoted.substring(1, quoted.length() - 1);
			}
		}
		return quoted;
	}

	private static boolean isSpace(final char c) {
		return c == ' ' || c == '\t' || c == '\r';
	}

	private static boolean isNameStart(final char c) {
		return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
	}

	private static boolean isNameChar(final char c) {
		return isNameStart(c) || (c >= '0' && c <= '9') || c == '_' || c == '-'
				|| c == '.' || c == '*';
	}
}
