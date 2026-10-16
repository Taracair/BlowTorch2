package com.resurrection.blowtorch2.lib.button;

import java.util.Locale;

/** {@code builtin:set/id} or a file path. No directory traversal in a built-in id. */
public final class ButtonFaceRef {

	public static final class Spec {
		public final String assetPng;
		public final String file;

		Spec(final String assetPng, final String file) {
			this.assetPng = assetPng;
			this.file = file;
		}

		public boolean isNone() {
			return assetPng == null && file == null;
		}
	}

	private static final Spec NONE = new Spec(null, null);

	private ButtonFaceRef() {
	}

	public static Spec parse(final String raw) {
		if (raw == null) {
			return NONE;
		}
		String s = raw.trim();
		if (s.length() == 0) {
			return NONE;
		}
		if (s.startsWith("builtin:")) {
			String asset = builtinPng(s.substring("builtin:".length()));
			if (asset == null) {
				return NONE;
			}
			return new Spec(asset, null);
		}
		if (s.startsWith("file:")) {
			s = s.substring("file:".length()).trim();
			if (s.length() == 0) {
				return NONE;
			}
		}
		return new Spec(null, s);
	}

	/** {@code marks/sword} → {@code buttonfaces/marks/sword.png}, or null. */
	public static String builtinPng(final String setAndId) {
		if (setAndId == null) {
			return null;
		}
		int slash = setAndId.indexOf('/');
		if (slash <= 0 || slash != setAndId.lastIndexOf('/')) {
			return null;
		}
		String set = setAndId.substring(0, slash);
		String id = setAndId.substring(slash + 1);
		if (!token(set) || !token(id)) {
			return null;
		}
		return "buttonfaces/" + set + "/" + id + ".png";
	}

	public static boolean isSvg(final String path) {
		return endsWith(path, ".svg");
	}

	public static boolean isRaster(final String path) {
		return endsWith(path, ".png") || endsWith(path, ".jpg") || endsWith(path, ".jpeg");
	}

	private static boolean token(final String s) {
		if (s == null || s.length() == 0) {
			return false;
		}
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-';
			if (!ok) {
				return false;
			}
		}
		return true;
	}

	private static boolean endsWith(final String path, final String suffix) {
		if (path == null) {
			return false;
		}
		return path.toLowerCase(Locale.US).endsWith(suffix);
	}
}
