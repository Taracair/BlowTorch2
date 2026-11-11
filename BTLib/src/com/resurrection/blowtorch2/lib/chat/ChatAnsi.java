package com.resurrection.blowtorch2.lib.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.trigger.style.StyleLineModel;
import com.resurrection.blowtorch2.lib.trigger.style.StyleSnapshot;

/**
 * SGR on a copied chat line. The plain body stays the text search and
 * absorb use. {@code colored} is that same text with the world's codes.
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

	private ChatAnsi() {
	}

	/**
	 * ANSI for {@code body} as it sits in {@code model}, or empty when that
	 * slice has no color and no bold.
	 */
	public static String encodeSlice(StyleLineModel model, String body) {
		if (model == null || body == null || body.length() == 0) {
			return "";
		}
		if (model.plain == null || model.byChar == null) {
			return "";
		}
		int at = model.plain.indexOf(body);
		int end = at + body.length();
		if (at < 0 || end > model.byChar.length) {
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
			out.append(body, i - at, j - at);
			i = j;
		}
		return out.toString();
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
