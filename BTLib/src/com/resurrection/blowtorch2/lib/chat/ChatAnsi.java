package com.resurrection.blowtorch2.lib.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.trigger.style.StyleLineModel;
import com.resurrection.blowtorch2.lib.trigger.style.StyleSnapshot;

/**
 * SGR on a copied chat line. The plain body stays the text search and
 * absorb use. {@code colored} is that same text with the world's codes,
 * or codes written in the body as {@code \27[…m}.
 */
public final class ChatAnsi {

	public static final class Piece {
		public final String text;
		/** 0 means the bubble's own text color. */
		public final int fgArgb;
		public final boolean bold;

		Piece(String text, int fgArgb, boolean bold) {
			this.text = text == null ? "" : text;
			this.fgArgb = fgArgb;
			this.bold = bold;
		}
	}

	/** Plain text to store, and SGR for that same text (empty when nothing to paint). */
	public static final class Painted {
		public final String plain;
		public final String colored;

		Painted(String plain, String colored) {
			this.plain = plain == null ? "" : plain;
			this.colored = colored == null ? "" : colored;
		}
	}

	private ChatAnsi() {
	}

	/**
	 * ANSI for {@code body} as it sits in {@code model}, or empty when that
	 * slice has no color and no bold.
	 */
	public static String encodeSlice(StyleLineModel model, String body) {
		return encodeSlice(model, body, -1);
	}

	/**
	 * Same as {@link #encodeSlice(StyleLineModel, String)}. {@code anchor} is
	 * the hit's offset in the line, or {@code -1} when that offset is unknown.
	 */
	public static String encodeSlice(StyleLineModel model, String body, int anchor) {
		if (model == null || body == null || body.length() == 0) {
			return "";
		}
		if (model.plain == null || model.byChar == null) {
			return "";
		}
		int at = placed(model, body, anchor);
		int end = at + body.length();
		if (at < 0 || end > model.byChar.length) {
			return "";
		}
		return encodeRange(model, at, end);
	}

	/**
	 * {@code $n} keeps that capture's color. {@code \27[…m} (also {@code \033},
	 * {@code \x1b}, {@code \e}) sets SGR on the following text until {@code \27[0m}.
	 * {@code pattern} may be null; a repeated word then uses the first copy.
	 * {@code anchor} is the hit's offset of {@code matched} in the line, or
	 * {@code -1} when that offset is unknown.
	 */
	public static Painted paintTemplate(StyleLineModel model, String template,
			Map<String, String> captures, String matched, Pattern pattern) {
		return paintTemplate(model, template, captures, matched, pattern, -1);
	}

	public static Painted paintTemplate(StyleLineModel model, String template,
			Map<String, String> captures, String matched, Pattern pattern,
			int anchor) {
		if (template == null) {
			template = "";
		}
		if (!wantsPaint(template)) {
			int use = matched != null && template.equals(matched) ? anchor : -1;
			return new Painted(template, encodeSlice(model, template, use));
		}
		int base = placed(model, matched, anchor);
		int[] groupStart = null;
		int[] groupEnd = null;
		if (base >= 0 && pattern != null) {
			try {
				Matcher m = pattern.matcher(matched);
				if (m.matches()) {
					int count = m.groupCount();
					groupStart = new int[count + 1];
					groupEnd = new int[count + 1];
					for (int g = 0; g <= count; g++) {
						groupStart[g] = m.start(g);
						groupEnd[g] = m.end(g);
					}
				}
			} catch (RuntimeException ignored) {
				// The hit still copies; spans fall back to the first copy of the text.
				groupStart = null;
				groupEnd = null;
			}
		}
		StringBuilder plain = new StringBuilder();
		StringBuilder colored = new StringBuilder();
		StringBuilder pending = new StringBuilder();
		boolean explicit = false;
		int lastEnd = -1;
		int i = 0;
		int n = template.length();
		while (i < n) {
			if (escapedSgr(template, i)) {
				emit(plain, colored, pending, explicit, model, null, "\\");
				lastEnd = -1;
				i += 2;
				continue;
			}
			int sgrAt = sgrEnd(template, i);
			if (sgrAt > i) {
				String params = paramsOf(template, i, sgrAt);
				if (clearsToInherit(params)) {
					explicit = false;
					pending.setLength(0);
				} else {
					if (!explicit && pending.length() == 0
							&& colored.indexOf("\u001b") >= 0) {
						pending.append("\u001b[0m");
					}
					explicit = true;
					pending.append('\u001b').append('[').append(params).append('m');
					lastEnd = -1;
				}
				i = sgrAt;
				continue;
			}
			if (captureAt(template, i)) {
				int j = i + 1;
				while (j < n && template.charAt(j) >= '0' && template.charAt(j) <= '9') {
					j++;
				}
				String key = template.substring(i + 1, j);
				boolean have = captures != null && captures.containsKey(key);
				String text;
				if (have) {
					text = captures.get(key);
					if (text == null) {
						text = "";
					}
				} else {
					text = template.substring(i, j);
				}
				int[] span = null;
				if (have) {
					span = locate(model, matched, base, groupStart, groupEnd,
							parseInt(key), text);
				}
				if (text.length() == 0) {
					if (span != null) {
						lastEnd = span[1];
					}
				} else {
					emit(plain, colored, pending, explicit, model, span, text);
					lastEnd = span != null ? span[1] : -1;
				}
				i = j;
				continue;
			}
			int j = i + 1;
			while (j < n && !escapedSgr(template, j) && sgrEnd(template, j) < 0
					&& !captureAt(template, j)) {
				j++;
			}
			String literal = template.substring(i, j);
			int[] gap = null;
			if (!explicit) {
				gap = literalGap(model, matched, base, groupStart, groupEnd, lastEnd,
						template, j, captures, literal);
			}
			emit(plain, colored, pending, explicit, model, gap, literal);
			lastEnd = (!explicit && gap != null) ? gap[1] : -1;
			i = j;
		}
		String out = colored.toString();
		if (out.indexOf('\u001b') < 0) {
			out = "";
		}
		return new Painted(plain.toString(), out);
	}

	private static int placed(StyleLineModel model, String text, int anchor) {
		if (model == null || model.plain == null || text == null || text.length() == 0) {
			return -1;
		}
		int room = model.plain.length() - text.length();
		if (anchor >= 0 && anchor <= room
				&& model.plain.regionMatches(anchor, text, 0, text.length())) {
			return anchor;
		}
		return model.plain.indexOf(text);
	}

	private static String encodeRange(StyleLineModel model, int at, int end) {
		if (model == null || model.byChar == null || model.plain == null
				|| at < 0 || end > model.byChar.length || at >= end
				|| end > model.plain.length()) {
			return "";
		}
		boolean any = false;
		for (int i = at; i < end; i++) {
			if (!isDefault(model.byChar[i])) {
				any = true;
				break;
			}
		}
		if (!any) {
			return "";
		}
		StringBuilder out = new StringBuilder();
		int i = at;
		while (i < end) {
			StyleSnapshot snap = model.byChar[i];
			int j = i + 1;
			while (j < end && snap != null && snap.sameRecipe(model.byChar[j])) {
				j++;
			}
			out.append(sgr(snap));
			out.append(model.plain, i, j);
			i = j;
		}
		return out.toString();
	}

	private static boolean wantsPaint(String template) {
		int n = template.length();
		for (int i = 0; i < n; i++) {
			if (captureAt(template, i) || sgrEnd(template, i) > i) {
				return true;
			}
		}
		return false;
	}

	private static boolean captureAt(String s, int i) {
		return i + 1 < s.length() && s.charAt(i) == '$'
				&& s.charAt(i + 1) >= '0' && s.charAt(i + 1) <= '9';
	}

	/** {@code \\} only when it escapes an SGR. A bare {@code \\} stays two characters. */
	private static boolean escapedSgr(String s, int i) {
		return i + 1 < s.length() && s.charAt(i) == '\\' && s.charAt(i + 1) == '\\'
				&& sgrEnd(s, i + 1) > i + 1;
	}

	/** Index just after the {@code m}, or {@code -1}. */
	private static int sgrEnd(String s, int i) {
		int n = s.length();
		if (i >= n) {
			return -1;
		}
		int bracket;
		char c = s.charAt(i);
		if (c == '\u001b') {
			bracket = (i + 1 < n && s.charAt(i + 1) == '[') ? i + 1 : -1;
		} else if (c == '\\' && i + 1 < n) {
			char d = s.charAt(i + 1);
			if ((d == 'e' || d == 'E') && i + 2 < n && s.charAt(i + 2) == '[') {
				bracket = i + 2;
			} else if (d == '2' && i + 3 < n && s.charAt(i + 2) == '7'
					&& s.charAt(i + 3) == '[') {
				bracket = i + 3;
			} else if (d == '0' && i + 4 < n && s.charAt(i + 2) == '3'
					&& s.charAt(i + 3) == '3' && s.charAt(i + 4) == '[') {
				bracket = i + 4;
			} else if ((d == 'x' || d == 'X') && i + 4 < n
					&& s.charAt(i + 2) == '1'
					&& (s.charAt(i + 3) == 'b' || s.charAt(i + 3) == 'B')
					&& s.charAt(i + 4) == '[') {
				bracket = i + 4;
			} else {
				bracket = -1;
			}
		} else {
			bracket = -1;
		}
		if (bracket < 0) {
			return -1;
		}
		int letter = bracket + 1;
		while (letter < n) {
			char e = s.charAt(letter);
			if ((e >= 'A' && e <= 'Z') || (e >= 'a' && e <= 'z')) {
				break;
			}
			letter++;
		}
		if (letter >= n || s.charAt(letter) != 'm') {
			return -1;
		}
		return letter + 1;
	}

	private static String paramsOf(String s, int at, int end) {
		int bracket = s.indexOf('[', at);
		if (bracket < 0 || bracket >= end) {
			return "";
		}
		return s.substring(bracket + 1, end - 1);
	}

	/** {@code \27[0m} and {@code \27[m} go back to the line. {@code \27[0;31m} does not. */
	private static boolean clearsToInherit(String params) {
		if (params == null || params.trim().length() == 0) {
			return true;
		}
		boolean sawZero = false;
		String[] parts = params.split(";");
		for (int i = 0; i < parts.length; i++) {
			if (parseInt(parts[i]) == 0) {
				sawZero = true;
				break;
			}
		}
		if (!sawZero) {
			return false;
		}
		int[] state = applySgr(params, 0, 0);
		return state[0] == 0 && state[1] == 0;
	}

	private static int[] locate(StyleLineModel model, String matched, int base,
			int[] groupStart, int[] groupEnd, int group, String text) {
		if (text == null) {
			text = "";
		}
		if (groupStart != null && group >= 0 && group < groupStart.length
				&& groupStart[group] >= 0 && matched != null && base >= 0) {
			int s = groupStart[group];
			int e = groupEnd[group];
			if (s <= e && e <= matched.length() && matched.substring(s, e).equals(text)) {
				return new int[] { base + s, base + e };
			}
		}
		if (text.length() == 0 || model == null || model.plain == null) {
			return null;
		}
		if (matched != null && base >= 0) {
			int rel = matched.indexOf(text);
			if (rel >= 0) {
				return new int[] { base + rel, base + rel + text.length() };
			}
		}
		int at = model.plain.indexOf(text);
		if (at < 0) {
			return null;
		}
		return new int[] { at, at + text.length() };
	}

	private static int[] literalGap(StyleLineModel model, String matched, int base,
			int[] groupStart, int[] groupEnd, int lastEnd, String template, int from,
			Map<String, String> captures, String literal) {
		if (lastEnd < 0 || literal == null || literal.length() == 0
				|| model == null || model.plain == null) {
			return null;
		}
		int fromCapture = from;
		int limit = template.length();
		while (fromCapture < limit) {
			int skip = sgrEnd(template, fromCapture);
			if (skip <= fromCapture) {
				break;
			}
			fromCapture = skip;
		}
		if (!captureAt(template, fromCapture)) {
			return null;
		}
		int j = fromCapture + 1;
		int n = template.length();
		while (j < n && template.charAt(j) >= '0' && template.charAt(j) <= '9') {
			j++;
		}
		String key = template.substring(fromCapture + 1, j);
		if (captures == null || !captures.containsKey(key)) {
			return null;
		}
		String text = captures.get(key);
		if (text == null) {
			text = "";
		}
		int[] span = locate(model, matched, base, groupStart, groupEnd, parseInt(key), text);
		if (span == null || span[0] < lastEnd || span[0] > model.plain.length()) {
			return null;
		}
		if (!model.plain.substring(lastEnd, span[0]).equals(literal)) {
			return null;
		}
		return new int[] { lastEnd, span[0] };
	}

	private static void emit(StringBuilder plain, StringBuilder colored,
			StringBuilder pending, boolean explicit, StyleLineModel model,
			int[] span, String text) {
		if (text == null || text.length() == 0) {
			return;
		}
		plain.append(text);
		if (explicit) {
			colored.append(pending);
			pending.setLength(0);
			colored.append(text);
			return;
		}
		String slice = span == null ? "" : encodeRange(model, span[0], span[1]);
		if (slice.length() == 0) {
			if (colored.indexOf("\u001b") >= 0) {
				colored.append("\u001b[0m");
			}
			colored.append(text);
		} else {
			colored.append(slice);
		}
	}

	public static List<Piece> pieces(String ansi) {
		if (ansi == null || ansi.length() == 0) {
			return Collections.emptyList();
		}
		ArrayList<Piece> out = new ArrayList<Piece>();
		int bold = 0;
		int fg = 0;
		StringBuilder run = new StringBuilder();
		int runBold = 0;
		int runFg = 0;
		int i = 0;
		while (i < ansi.length()) {
			char c = ansi.charAt(i);
			if (c == '\u001B' && i + 1 < ansi.length() && ansi.charAt(i + 1) == '[') {
				int letter = i + 2;
				while (letter < ansi.length()) {
					char e = ansi.charAt(letter);
					if ((e >= 'A' && e <= 'Z') || (e >= 'a' && e <= 'z')) {
						break;
					}
					letter++;
				}
				if (letter >= ansi.length()) {
					break;
				}
				if (ansi.charAt(letter) == 'm') {
					flush(out, run, runFg, runBold);
					int[] state = applySgr(ansi.substring(i + 2, letter), bold, fg);
					bold = state[0];
					fg = state[1];
					runBold = bold;
					runFg = fg;
				}
				i = letter + 1;
				continue;
			}
			run.append(c);
			i++;
		}
		flush(out, run, runFg, runBold);
		return out;
	}

	private static void flush(List<Piece> out, StringBuilder run, int fg, int bold) {
		if (run.length() == 0) {
			return;
		}
		out.add(new Piece(run.toString(), fg, bold != 0));
		run.setLength(0);
	}

	/** @return {@code [bold, fgArgb]} */
	private static int[] applySgr(String params, int bold, int fg) {
		if (params == null || params.length() == 0) {
			return new int[] { 0, 0 };
		}
		String[] parts = params.split(";");
		int i = 0;
		while (i < parts.length) {
			int n = parseInt(parts[i]);
			if (n == 0) {
				bold = 0;
				fg = 0;
			} else if (n == 1) {
				bold = 1;
			} else if (n == 22) {
				bold = 0;
			} else if (n == 39) {
				fg = 0;
			} else if (n >= 30 && n <= 37) {
				fg = foreground(bold, n, false);
			} else if (n >= 90 && n <= 97) {
				fg = foreground(1, 30 + (n - 90), false);
			} else if (n == 38 && i + 2 < parts.length && parseInt(parts[i + 1]) == 5) {
				fg = foreground(bold, parseInt(parts[i + 2]), true);
				i += 2;
			} else if (n == 38 && i + 4 < parts.length && parseInt(parts[i + 1]) == 2) {
				int r = parseInt(parts[i + 2]) & 255;
				int g = parseInt(parts[i + 3]) & 255;
				int b = parseInt(parts[i + 4]) & 255;
				fg = 0xFF000000 | (r << 16) | (g << 8) | b;
				i += 4;
			}
			i++;
		}
		return new int[] { bold, fg };
	}

	private static int foreground(int bright, int code, boolean xterm) {
		int rgb = Colorizer.getColorValue(Integer.valueOf(bright),
				Integer.valueOf(code), xterm);
		if ((rgb & 0xFF000000) == 0) {
			rgb |= 0xFF000000;
		}
		return rgb;
	}

	private static int parseInt(String s) {
		if (s == null) {
			return -1;
		}
		String t = s.trim();
		if (t.length() == 0) {
			return 0;
		}
		try {
			return Integer.parseInt(t);
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	private static boolean isDefault(StyleSnapshot s) {
		if (s == null) {
			return true;
		}
		if (s.bright || s.weight()) {
			return false;
		}
		if (s.fgSpace != StyleSnapshot.ColorSpace.ANSI16) {
			return false;
		}
		return s.fgCode == 37 || s.fgCode == 39;
	}

	private static String sgr(StyleSnapshot s) {
		if (isDefault(s)) {
			return "\u001b[0m";
		}
		// 0 first: a later normal colour must not keep the previous SGR 1.
		StringBuilder b = new StringBuilder();
		b.append("\u001b[0");
		if (s.bright || s.weight()) {
			b.append(";1");
		}
		if (s.fgSpace == StyleSnapshot.ColorSpace.XTERM256) {
			b.append(";38;5;").append(s.fgCode);
		} else if (s.fgSpace == StyleSnapshot.ColorSpace.RGB) {
			int rgb = s.fgCode & 0xFFFFFF;
			b.append(";38;2;");
			b.append((rgb >> 16) & 255).append(';');
			b.append((rgb >> 8) & 255).append(';');
			b.append(rgb & 255);
		} else if (s.bright && (s.fgCode == 37 || s.fgCode == 39)) {
			// SGR 1 on white is #FFFFFF in the game. The bubble's own text is #F4F4F4.
			b.append(";37");
		} else if (s.fgCode != 37 && s.fgCode != 39) {
			b.append(';').append(s.fgCode);
		}
		b.append('m');
		return b.toString();
	}
}
