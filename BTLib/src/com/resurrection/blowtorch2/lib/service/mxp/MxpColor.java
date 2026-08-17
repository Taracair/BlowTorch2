package com.resurrection.blowtorch2.lib.service.mxp;

import java.util.HashMap;
import java.util.Locale;

/** Named colours from the MXP spec (ANSI + HTML) to SGR. */
public final class MxpColor {

	private static final HashMap<String, int[]> RGB = new HashMap<String, int[]>();
	private static final HashMap<String, Integer> ANSI_FG = new HashMap<String, Integer>();

	static {
		ansi("black", 30, 0, 0, 0);
		ansi("red", 31, 187, 0, 0);
		ansi("green", 32, 0, 187, 0);
		ansi("yellow", 33, 187, 187, 0);
		ansi("blue", 34, 0, 0, 187);
		ansi("magenta", 35, 187, 0, 187);
		ansi("cyan", 36, 0, 187, 187);
		ansi("white", 37, 187, 187, 187);
		ansi("gray", 90, 128, 128, 128);
		ansi("grey", 90, 128, 128, 128);
		ansi("brightblack", 90, 85, 85, 85);
		ansi("brightred", 91, 255, 85, 85);
		ansi("brightgreen", 92, 85, 255, 85);
		ansi("brightyellow", 93, 255, 255, 85);
		ansi("brightblue", 94, 85, 85, 255);
		ansi("brightmagenta", 95, 255, 85, 255);
		ansi("brightcyan", 96, 85, 255, 255);
		ansi("brightwhite", 97, 255, 255, 255);
		rgb("orange", 255, 165, 0);
		rgb("purple", 128, 0, 128);
		rgb("brown", 165, 42, 42);
		rgb("pink", 255, 192, 203);
		rgb("gold", 255, 215, 0);
		rgb("silver", 192, 192, 192);
		rgb("navy", 0, 0, 128);
		rgb("teal", 0, 128, 128);
		rgb("olive", 128, 128, 0);
		rgb("maroon", 128, 0, 0);
		rgb("lime", 0, 255, 0);
		rgb("aqua", 0, 255, 255);
		rgb("fuchsia", 255, 0, 255);
		rgb("aliceblue", 240, 248, 255);
		rgb("coral", 255, 127, 80);
		rgb("crimson", 220, 20, 60);
		rgb("khaki", 240, 230, 140);
		rgb("indigo", 75, 0, 130);
		rgb("violet", 238, 130, 238);
		rgb("snow", 255, 250, 250);
		rgb("ivory", 255, 255, 240);
	}

	private MxpColor() {
	}

	/**
	 * SGR fragment without the CSI wrapper, e.g. {@code 31} or {@code 38;2;255;0;0}.
	 * Null when the name is empty or unrecognised.
	 */
	public static String foregroundSgr(final String name) {
		return toSgr(name, true);
	}

	public static String backgroundSgr(final String name) {
		return toSgr(name, false);
	}

	private static String toSgr(final String name, final boolean foreground) {
		if (name == null) {
			return null;
		}
		String n = name.trim();
		if (n.length() == 0) {
			return null;
		}
		int comma = n.indexOf(',');
		if (comma > 0) {
			// FONT COLOR=Red,Blink — colour is the first token.
			n = n.substring(0, comma).trim();
		}
		if (n.startsWith("#")) {
			int[] rgb = parseHex(n);
			if (rgb == null) {
				return null;
			}
			return truecolor(foreground, rgb[0], rgb[1], rgb[2]);
		}
		String key = n.toLowerCase(Locale.US);
		if ("blink".equals(key)) {
			return foreground ? "5" : null;
		}
		Integer ansi = ANSI_FG.get(key);
		if (ansi != null) {
			int code = ansi.intValue();
			if (!foreground) {
				if (code >= 90) {
					code = code - 90 + 100;
				} else {
					code = code + 10;
				}
			}
			return Integer.toString(code);
		}
		int[] rgb = RGB.get(key);
		if (rgb != null) {
			return truecolor(foreground, rgb[0], rgb[1], rgb[2]);
		}
		return null;
	}

	private static String truecolor(final boolean fg, final int r, final int g, final int b) {
		return (fg ? "38;2;" : "48;2;") + r + ";" + g + ";" + b;
	}

	private static int[] parseHex(final String hash) {
		String h = hash.substring(1);
		try {
			if (h.length() == 3) {
				int r = Integer.parseInt(h.substring(0, 1), 16) * 17;
				int g = Integer.parseInt(h.substring(1, 2), 16) * 17;
				int b = Integer.parseInt(h.substring(2, 3), 16) * 17;
				return new int[] { r, g, b };
			}
			if (h.length() == 6) {
				int r = Integer.parseInt(h.substring(0, 2), 16);
				int g = Integer.parseInt(h.substring(2, 4), 16);
				int b = Integer.parseInt(h.substring(4, 6), 16);
				return new int[] { r, g, b };
			}
		} catch (NumberFormatException e) {
			return null;
		}
		return null;
	}

	private static void ansi(final String name, final int fg, final int r, final int g,
			final int b) {
		ANSI_FG.put(name, Integer.valueOf(fg));
		RGB.put(name, new int[] { r, g, b });
	}

	private static void rgb(final String name, final int r, final int g, final int b) {
		RGB.put(name, new int[] { r, g, b });
	}
}
