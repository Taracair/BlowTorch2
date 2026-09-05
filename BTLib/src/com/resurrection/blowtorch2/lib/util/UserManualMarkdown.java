package com.resurrection.blowtorch2.lib.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Subset of Markdown used in {@code user-manual.md}, turned into plain text plus
 * runs. Android-free so Help can JVM-test it. Unmatched markers are dropped so
 * the player never sees leftover {@code **} or {@code `}.
 */
public final class UserManualMarkdown {

	public static final int KIND_BOLD = 1;
	public static final int KIND_CODE = 2;
	public static final int KIND_LINK = 3;

	public static final class Run {
		public final int start;
		public final int end;
		public final int kind;
		public final String href;

		public Run(final int start, final int end, final int kind, final String href) {
			this.start = start;
			this.end = end;
			this.kind = kind;
			this.href = href;
		}
	}

	public static final class Result {
		public final String text;
		public final List<Run> runs;

		public Result(final String text, final List<Run> runs) {
			this.text = text == null ? "" : text;
			this.runs = runs == null ? new ArrayList<Run>() : runs;
		}
	}

	private UserManualMarkdown() {
	}

	public static Result render(final String src) {
		if (src == null || src.length() == 0) {
			return new Result("", new ArrayList<Run>());
		}
		StringBuilder out = new StringBuilder(src.length());
		ArrayList<Run> runs = new ArrayList<Run>();
		String[] lines = src.split("\n", -1);
		boolean inFence = false;
		for (int li = 0; li < lines.length; li++) {
			if (li > 0) {
				out.append('\n');
			}
			String line = lines[li];
			if (line.startsWith("```")) {
				inFence = !inFence;
				continue;
			}
			if (inFence) {
				int start = out.length();
				out.append(line);
				if (out.length() > start) {
					runs.add(new Run(start, out.length(), KIND_CODE, null));
				}
				continue;
			}
			String trimmed = ltrim(line);
			if (isRule(trimmed)) {
				continue;
			}
			int hashes = headingLevel(trimmed);
			if (hashes >= 3) {
				int start = out.length();
				inline(trimmed.substring(hashes + 1), out, runs);
				if (out.length() > start) {
					runs.add(new Run(start, out.length(), KIND_BOLD, null));
				}
				continue;
			}
			inline(line, out, runs);
		}
		return new Result(out.toString(), runs);
	}

	private static void inline(final String line, final StringBuilder out,
			final List<Run> runs) {
		int i = 0;
		int n = line.length();
		while (i < n) {
			if (i + 1 < n && line.charAt(i) == '*' && line.charAt(i + 1) == '*') {
				int close = indexOf(line, "**", i + 2);
				if (close >= 0) {
					int start = out.length();
					out.append(line, i + 2, close);
					if (out.length() > start) {
						runs.add(new Run(start, out.length(), KIND_BOLD, null));
					}
					i = close + 2;
					continue;
				}
				i += 2;
				continue;
			}
			if (line.charAt(i) == '`') {
				int close = line.indexOf('`', i + 1);
				if (close > i) {
					int start = out.length();
					out.append(line, i + 1, close);
					if (out.length() > start) {
						runs.add(new Run(start, out.length(), KIND_CODE, null));
					}
					i = close + 1;
					continue;
				}
				i++;
				continue;
			}
			if (line.charAt(i) == '!' && i + 1 < n && line.charAt(i + 1) == '[') {
				int consumed = tryLink(line, i + 1, out, runs, true);
				if (consumed > 0) {
					i = consumed;
					continue;
				}
			}
			if (line.charAt(i) == '[') {
				int consumed = tryLink(line, i, out, runs, false);
				if (consumed > 0) {
					i = consumed;
					continue;
				}
			}
			out.append(line.charAt(i));
			i++;
		}
	}

	/**
	 * @return index after the closing {@code )}, or -1 if this is not a link
	 */
	private static int tryLink(final String line, final int openBracket,
			final StringBuilder out, final List<Run> runs, final boolean image) {
		int rb = line.indexOf(']', openBracket + 1);
		if (rb < 0 || rb + 1 >= line.length() || line.charAt(rb + 1) != '(') {
			return -1;
		}
		int rp = line.indexOf(')', rb + 2);
		if (rp < 0) {
			return -1;
		}
		String label = line.substring(openBracket + 1, rb);
		String href = line.substring(rb + 2, rp);
		int start = out.length();
		out.append(label);
		if (!image && out.length() > start && looksLikeUrl(href)) {
			runs.add(new Run(start, out.length(), KIND_LINK, href));
		} else if (!image && out.length() > start) {
			runs.add(new Run(start, out.length(), KIND_BOLD, null));
		}
		return rp + 1;
	}

	private static boolean looksLikeUrl(final String href) {
		if (href == null) {
			return false;
		}
		String h = href.trim();
		return h.startsWith("http://") || h.startsWith("https://");
	}

	private static int headingLevel(final String trimmed) {
		int n = 0;
		while (n < trimmed.length() && trimmed.charAt(n) == '#') {
			n++;
		}
		if (n == 0 || n >= trimmed.length() || trimmed.charAt(n) != ' ') {
			return 0;
		}
		return n;
	}

	private static boolean isRule(final String trimmed) {
		if (trimmed.length() < 3) {
			return false;
		}
		char c = trimmed.charAt(0);
		if (c != '-' && c != '*') {
			return false;
		}
		for (int i = 0; i < trimmed.length(); i++) {
			char ch = trimmed.charAt(i);
			if (ch != c && ch != ' ') {
				return false;
			}
		}
		return true;
	}

	private static String ltrim(final String s) {
		int i = 0;
		while (i < s.length() && s.charAt(i) == ' ') {
			i++;
		}
		return s.substring(i);
	}

	private static int indexOf(final String s, final String needle, final int from) {
		return s.indexOf(needle, from);
	}
}
