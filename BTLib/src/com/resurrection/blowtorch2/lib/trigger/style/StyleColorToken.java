package com.resurrection.blowtorch2.lib.trigger.style;

import com.resurrection.blowtorch2.lib.trigger.style.StyleSnapshot.ColorSpace;

/**
 * Wire form used by Lua {@code style_fg}/{@code style_bg} and the trigger
 * editor: {@code ansi:32}, {@code xterm:208}, {@code rgb:#ff8700}. A bare
 * integer is ANSI.
 */
public final class StyleColorToken {

	public final ColorSpace space;
	public final int code;

	private StyleColorToken(final ColorSpace space, final int code) {
		this.space = space;
		this.code = code;
	}

	public static StyleColorToken parse(final String token) {
		if (token == null) {
			return null;
		}
		String t = token.trim();
		if (t.length() == 0) {
			return null;
		}
		int colon = t.indexOf(':');
		if (colon <= 0) {
			try {
				return new StyleColorToken(ColorSpace.ANSI16, Integer.parseInt(t));
			} catch (NumberFormatException e) {
				return null;
			}
		}
		String kind = t.substring(0, colon).trim();
		String rest = t.substring(colon + 1).trim();
		if ("xterm".equalsIgnoreCase(kind) || "xterm256".equalsIgnoreCase(kind)) {
			try {
				return new StyleColorToken(ColorSpace.XTERM256, Integer.parseInt(rest));
			} catch (NumberFormatException e) {
				return null;
			}
		}
		if ("rgb".equalsIgnoreCase(kind)) {
			if (rest.startsWith("#")) {
				rest = rest.substring(1);
			}
			try {
				return new StyleColorToken(ColorSpace.RGB,
						Integer.parseInt(rest, 16) & 0xFFFFFF);
			} catch (NumberFormatException e) {
				return null;
			}
		}
		try {
			return new StyleColorToken(ColorSpace.ANSI16, Integer.parseInt(rest));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	public static String format(final ColorSpace space, final int code) {
		if (space == ColorSpace.XTERM256) {
			return "xterm:" + code;
		}
		if (space == ColorSpace.RGB) {
			return String.format("rgb:#%06x", Integer.valueOf(code & 0xFFFFFF));
		}
		return "ansi:" + code;
	}

	@Override
	public String toString() {
		return format(space, code);
	}
}
