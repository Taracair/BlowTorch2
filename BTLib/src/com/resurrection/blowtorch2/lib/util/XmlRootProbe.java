package com.resurrection.blowtorch2.lib.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Root element name and attributes only. A live profile is ~250 KB; SAX used
 * to parse it twice. Unparseable prolog → null name, same as a parser exception.
 */
public final class XmlRootProbe {

	/** Give up rather than scan a large file that has no start tag in it. */
	private static final int MAX_PROLOG_CHARS = 64 * 1024;

	/** What the root element turned out to be. */
	public static final class Root {
		private final String name;
		private final Map<String, String> attributes;

		Root(final String name, final Map<String, String> attributes) {
			this.name = name;
			this.attributes = attributes == null
					? Collections.<String, String>emptyMap()
					: Collections.unmodifiableMap(attributes);
		}

		/** The root element's name, or null when the document had no start tag. */
		public String name() {
			return name;
		}

		/** True when a root element was found at all. */
		public boolean found() {
			return name != null;
		}

		/** Raw attribute text, or null when the root does not carry it. */
		public String attribute(final String attributeName) {
			return attributes.get(attributeName);
		}

		/** The attribute read as an integer, or {@code fallback} when it is missing or not a number. */
		public int intAttribute(final String attributeName, final int fallback) {
			String raw = attributes.get(attributeName);
			if (raw == null) {
				return fallback;
			}
			try {
				return Integer.parseInt(raw.trim());
			} catch (NumberFormatException e) {
				return fallback;
			}
		}
	}

	private XmlRootProbe() {
	}

	/** The answer for a document that could not be read at all. */
	public static Root none() {
		return new Root(null, null);
	}

	/** Convenience for the common case: a UTF-8 stream, closed by the caller. */
	public static Root probe(final InputStream in) throws IOException {
		if (in == null) {
			return new Root(null, null);
		}
		return probe(new InputStreamReader(in, "UTF-8"));
	}

	public static Root probe(final Reader reader) throws IOException {
		if (reader == null) {
			return new Root(null, null);
		}
		Scanner s = new Scanner(reader);
		int c = s.read();
		// A byte order mark survives decoding as U+FEFF; it is not whitespace.
		if (c == 0xFEFF) {
			c = s.read();
		}
		while (c != -1) {
			if (c != '<') {
				c = s.read();
				continue;
			}
			int next = s.read();
			if (next == '?') {
				c = skipTo(s, "?>");
			} else if (next == '!') {
				c = skipMarkupDeclaration(s);
			} else if (next == '/' || next == -1) {
				// A close tag before any open tag: not a document we understand.
				return new Root(null, null);
			} else {
				return readStartTag(s, next);
			}
		}
		return new Root(null, null);
	}

	/** Comments end at "-->", everything else at the '>' that closes it. */
	private static int skipMarkupDeclaration(final Scanner s) throws IOException {
		int a = s.read();
		if (a == '-') {
			int b = s.read();
			if (b == '-') {
				return skipTo(s, "-->");
			}
		}
		// A doctype may carry an internal subset in brackets, which can contain
		// '>' characters that do not end the declaration.
		int depth = 0;
		int c = a;
		while (c != -1) {
			if (c == '[') {
				depth++;
			} else if (c == ']') {
				depth--;
			} else if (c == '>' && depth <= 0) {
				return s.read();
			}
			c = s.read();
		}
		return -1;
	}

	/** Consume through {@code terminator}; returns the character after it. */
	private static int skipTo(final Scanner s, final String terminator) throws IOException {
		int matched = 0;
		int c = s.read();
		while (c != -1) {
			if (c == terminator.charAt(matched)) {
				matched++;
				if (matched == terminator.length()) {
					return s.read();
				}
			} else {
				// Restart, but allow the mismatched character to begin a new match.
				matched = c == terminator.charAt(0) ? 1 : 0;
			}
			c = s.read();
		}
		return -1;
	}

	private static Root readStartTag(final Scanner s, final int firstChar) throws IOException {
		StringBuilder name = new StringBuilder();
		name.append((char) firstChar);
		int c = s.read();
		while (c != -1 && !isSpace(c) && c != '>' && c != '/') {
			name.append((char) c);
			c = s.read();
		}
		Map<String, String> attributes = new HashMap<String, String>();
		while (c != -1) {
			while (isSpace(c)) {
				c = s.read();
			}
			if (c == '>' || c == '/' || c == -1) {
				break;
			}
			StringBuilder attr = new StringBuilder();
			while (c != -1 && !isSpace(c) && c != '=' && c != '>' && c != '/') {
				attr.append((char) c);
				c = s.read();
			}
			while (isSpace(c)) {
				c = s.read();
			}
			if (c != '=') {
				// An attribute with no value; nothing we look for takes that shape.
				continue;
			}
			c = s.read();
			while (isSpace(c)) {
				c = s.read();
			}
			if (c != '"' && c != '\'') {
				// Unquoted value: read to the next delimiter rather than give up.
				StringBuilder bare = new StringBuilder();
				while (c != -1 && !isSpace(c) && c != '>' && c != '/') {
					bare.append((char) c);
					c = s.read();
				}
				attributes.put(attr.toString(), bare.toString());
				continue;
			}
			int quote = c;
			StringBuilder value = new StringBuilder();
			c = s.read();
			while (c != -1 && c != quote) {
				value.append((char) c);
				c = s.read();
			}
			attributes.put(attr.toString(), value.toString());
			c = s.read();
		}
		return new Root(name.toString(), attributes);
	}

	private static boolean isSpace(final int c) {
		return c == ' ' || c == '\t' || c == '\n' || c == '\r';
	}

	/** A reader that refuses to run away on a file with no markup in it. */
	private static final class Scanner {
		private final Reader reader;
		private int consumed;

		Scanner(final Reader reader) {
			this.reader = reader;
		}

		int read() throws IOException {
			if (consumed >= MAX_PROLOG_CHARS) {
				return -1;
			}
			consumed++;
			return reader.read();
		}
	}
}
