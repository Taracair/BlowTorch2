package com.resurrection.blowtorch2.lib.button;

import java.util.Locale;

/**
 * {@code builtin:set/id}, a copied file path, or {@code named:sword.png}.
 * {@code named:} is a file in the icon folder, maybe in a subfolder: {@code portrait/knight.png}.
 */
public final class ButtonFaceRef {

	public static final class Spec {
		public final String assetPng;
		public final String file;
		/** File in the icon folder, such as {@code sword.png} or {@code portrait/knight.png}. */
		public final String named;

		Spec(final String assetPng, final String file, final String named) {
			this.assetPng = assetPng;
			this.file = file;
			this.named = named;
		}

		public boolean isNone() {
			return assetPng == null && file == null && named == null;
		}
	}

	private static final Spec NONE = new Spec(null, null, null);

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
			return new Spec(asset, null, null);
		}
		if (s.startsWith("named:")) {
			String name = namedPath(s.substring("named:".length()));
			if (name == null) {
				return NONE;
			}
			return new Spec(null, null, name);
		}
		if (s.startsWith("file:")) {
			s = s.substring("file:".length()).trim();
			if (s.length() == 0) {
				return NONE;
			}
		}
		return new Spec(null, s, null);
	}

	/**
	 * One file name in the icon folder: {@code sword.png}, {@code My Icon.png},
	 * {@code épée.svg}. No slash. Not a path.
	 */
	public static String namedFile(final String raw) {
		if (raw == null) {
			return null;
		}
		String name = raw.trim();
		if (name.length() == 0 || name.length() > 240 || name.charAt(0) == '.') {
			return null;
		}
		int dot = name.lastIndexOf('.');
		if (dot <= 0 || dot == name.length() - 1) {
			return null;
		}
		String stem = name.substring(0, dot);
		if (".".equals(stem) || "..".equals(stem) || stem.endsWith(".")) {
			return null;
		}
		String ext = name.substring(dot).toLowerCase(Locale.US);
		if (!".png".equals(ext) && !".svg".equals(ext) && !".jpg".equals(ext)
				&& !".jpeg".equals(ext)) {
			return null;
		}
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if (c == '/' || c == '\\' || c < 32 || c == 127) {
				return null;
			}
		}
		return name;
	}

	/**
	 * A file in the icon folder or below it: {@code knight.png}, {@code portrait/knight.png}.
	 * No leading slash, no {@code .} or {@code ..} segment, no backslash.
	 */
	public static String namedPath(final String raw) {
		if (raw == null) {
			return null;
		}
		String path = raw.trim();
		if (path.length() == 0 || path.length() > 240) {
			return null;
		}
		String[] parts = path.split("/", -1);
		if (parts.length > 8) {
			return null;
		}
		for (int p = 0; p < parts.length - 1; p++) {
			String dir = parts[p];
			if (dir.length() == 0 || dir.trim().length() != dir.length()
					|| ".".equals(dir) || "..".equals(dir)) {
				return null;
			}
			for (int i = 0; i < dir.length(); i++) {
				char c = dir.charAt(i);
				if (c == '\\' || c < 32 || c == 127) {
					return null;
				}
			}
		}
		String file = parts[parts.length - 1];
		if (!file.equals(file.trim()) || namedFile(file) == null) {
			return null;
		}
		return path;
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
