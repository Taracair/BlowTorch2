package com.resurrection.blowtorch2.lib.button;

import java.util.Locale;

/**
 * {@code {font:…}} at the start of a button label. Same grammar as
 * {@code buttonlook.lua}. Anything else is left visible.
 */
public final class ButtonLabelPrefix {

	public static final class Result {
		public final String display;
		public final String path;
		public final String style;

		public Result(final String display, final String path, final String style) {
			this.display = display == null ? "" : display;
			this.path = path;
			this.style = style;
		}
	}

	private ButtonLabelPrefix() {
	}

	public static Result parse(final String label) {
		if (label == null) {
			return new Result("", null, null);
		}
		if (!label.startsWith("{font:")) {
			return new Result(label, null, null);
		}
		int end = label.indexOf('}', 6);
		if (end < 0) {
			return new Result(label, null, null);
		}
		String token = label.substring(6, end).trim();
		if (token.length() == 0) {
			return new Result(label, null, null);
		}
		String display = label.substring(end + 1);
		String style = null;
		String faceToken = token;
		int colon = token.indexOf(':');
		if (colon > 0) {
			String headStyle = styleName(token.substring(0, colon));
			String rest = token.substring(colon + 1).trim();
			if (headStyle != null && rest.length() > 0) {
				style = headStyle;
				faceToken = rest;
			}
		}
		if (style == null && colon < 0) {
			String asStyle = styleName(faceToken);
			if (asStyle != null) {
				return new Result(display, null, asStyle);
			}
		}
		String asFace = faceName(faceToken);
		if (asFace != null) {
			return new Result(display, asFace, style);
		}
		if (isPath(faceToken)) {
			return new Result(display, faceToken, style);
		}
		return new Result(label, null, null);
	}

	static String styleName(final String raw) {
		if (raw == null) {
			return null;
		}
		String t = raw.trim().toLowerCase(Locale.US);
		if ("regular".equals(t) || "bold".equals(t) || "italic".equals(t)
				|| "bolditalic".equals(t)) {
			return t;
		}
		if ("bold-italic".equals(t) || "bold italic".equals(t)) {
			return "bolditalic";
		}
		return null;
	}

	static String faceName(final String raw) {
		if (raw == null) {
			return null;
		}
		String t = raw.trim().toLowerCase(Locale.US);
		if ("serif".equals(t)) {
			return "serif";
		}
		if ("sans".equals(t) || "sans serif".equals(t) || "sans-serif".equals(t)
				|| "sansserif".equals(t)) {
			return "sans serif";
		}
		if ("monospace".equals(t) || "mono".equals(t)) {
			return "monospace";
		}
		if ("default".equals(t)) {
			return "default";
		}
		return null;
	}

	static boolean isPath(final String raw) {
		if (raw == null) {
			return false;
		}
		String t = raw.trim();
		if (t.indexOf('/') >= 0 || t.indexOf('\\') >= 0) {
			return true;
		}
		String lower = t.toLowerCase(Locale.US);
		return lower.endsWith(".ttf") || lower.endsWith(".otf");
	}
}
