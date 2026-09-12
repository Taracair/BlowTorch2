package com.resurrection.blowtorch2.lib.window;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/**
 * Curated game-window faces. The Options picker used to list every TTF under
 * {@code /system/fonts/}, which is dozens of near-identical Noto/Roboto cuts.
 */
public final class FontCatalog {

	public static final class Face {
		public final String path;
		public final String label;

		public Face(final String path, final String label) {
			this.path = path;
			this.label = label;
		}
	}

	/** Picker row that opens the system file picker rather than setting a face. */
	public static final String LOAD_FROM_STORAGE = ":load:";

	public static final String PREVIEW_SAMPLE = "The quick 123 []-+|";

	public static final String DEJAVU_ASSET = "fonts/DejaVuSansMono.ttf";

	private FontCatalog() {
	}

	/**
	 * Built-in picker rows, in order. Vera stays loadable by path for old
	 * profiles but is not listed: it is the same design as DejaVu.
	 */
	public static List<Face> bundledPickerFaces() {
		ArrayList<Face> out = new ArrayList<Face>();
		out.add(new Face(DEJAVU_ASSET, "DejaVu Sans Mono"));
		out.add(new Face("fonts/FairfaxHD.ttf", "Fairfax HD"));
		out.add(new Face("fonts/JetBrainsMonoNL-Regular.ttf", "JetBrains Mono"));
		out.add(new Face("fonts/UbuntuSansMono-Regular.ttf", "Ubuntu Sans Mono"));
		out.add(new Face("fonts/AtkinsonHyperlegibleMono-Regular.ttf",
				"Atkinson Hyperlegible Mono"));
		out.add(new Face("fonts/Inconsolata-Regular.ttf", "Inconsolata"));
		out.add(new Face("fonts/LiberationMono-Regular.ttf", "Liberation Mono"));
		out.add(new Face("fonts/NotoSansMono-Regular.ttf", "Noto Sans Mono"));
		out.add(new Face("monospace", "System monospace"));
		out.add(new Face("sans serif", "Sans serif"));
		out.add(new Face("default", "Android default"));
		return Collections.unmodifiableList(out);
	}

	/**
	 * A few system monospace files, added only when they exist. Not a directory
	 * listing — that is what made the old picker unusable.
	 */
	public static List<Face> optionalSystemFaces() {
		ArrayList<Face> out = new ArrayList<Face>();
		out.add(new Face("/system/fonts/DroidSansMono.ttf", "Droid Sans Mono"));
		out.add(new Face("/system/fonts/RobotoMono-Regular.ttf", "Roboto Mono"));
		out.add(new Face("/system/fonts/CutiveMono.ttf", "Cutive Mono"));
		out.add(new Face("/system/fonts/SourceCodePro-Regular.ttf", "Source Code Pro"));
		return Collections.unmodifiableList(out);
	}

	public static List<Face> assemblePicker(final List<String> existingSystemFiles,
			final List<Face> userFaces, final boolean includeLoadRow) {
		ArrayList<Face> out = new ArrayList<Face>();
		List<Face> bundled = bundledPickerFaces();
		for (int i = 0; i < bundled.size(); i++) {
			addUnique(out, bundled.get(i));
		}
		HashSet<String> exist = new HashSet<String>();
		if (existingSystemFiles != null) {
			exist.addAll(existingSystemFiles);
		}
		List<Face> system = optionalSystemFaces();
		for (int i = 0; i < system.size(); i++) {
			Face f = system.get(i);
			if (exist.contains(f.path)) {
				addUnique(out, f);
			}
		}
		if (userFaces != null) {
			for (int i = 0; i < userFaces.size(); i++) {
				addUnique(out, userFaces.get(i));
			}
		}
		if (includeLoadRow) {
			out.add(new Face(LOAD_FROM_STORAGE, "Load from storage…"));
		}
		return out;
	}

	public static boolean addUnique(final List<Face> into, final Face face) {
		if (into == null || face == null || face.path == null) {
			return false;
		}
		for (int i = 0; i < into.size(); i++) {
			if (face.path.equals(into.get(i).path)) {
				return false;
			}
		}
		into.add(face);
		return true;
	}

	public static boolean isLoadSentinel(final String path) {
		return LOAD_FROM_STORAGE.equals(path);
	}

	public static boolean isDejaVuAsset(final String path) {
		return DEJAVU_ASSET.equals(path);
	}

	public static File importedFontsDir(final File filesDir) {
		return new File(filesDir, "fonts");
	}

	public static String sanitizeImportFileName(final String raw) {
		if (raw == null) {
			return "imported.ttf";
		}
		String name = raw.trim();
		int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
		if (slash >= 0 && slash < name.length() - 1) {
			name = name.substring(slash + 1);
		}
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if (Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_') {
				b.append(c);
			}
		}
		name = b.toString();
		if (name.length() == 0) {
			return "imported.ttf";
		}
		if (!isFontFileName(name)) {
			name = name + ".ttf";
		}
		if (name.equals(".ttf") || name.equals(".otf")
				|| name.equalsIgnoreCase(".ttf") || name.equalsIgnoreCase(".otf")) {
			return "imported" + name.toLowerCase();
		}
		return name;
	}

	public static File uniqueImportTarget(final File dir, final String sanitizedName) {
		File dest = new File(dir, sanitizedName);
		if (!dest.exists()) {
			return dest;
		}
		String base = sanitizedName;
		String ext = "";
		int dot = sanitizedName.lastIndexOf('.');
		if (dot > 0) {
			base = sanitizedName.substring(0, dot);
			ext = sanitizedName.substring(dot);
		}
		for (int n = 2; n < 1000; n++) {
			dest = new File(dir, base + "-" + n + ext);
			if (!dest.exists()) {
				return dest;
			}
		}
		return dest;
	}

	public static String displayName(final String path) {
		if (path == null || path.length() == 0) {
			return "";
		}
		if (isLoadSentinel(path)) {
			return "Load from storage…";
		}
		List<Face> known = new ArrayList<Face>();
		known.addAll(bundledPickerFaces());
		known.addAll(optionalSystemFaces());
		known.add(new Face("fonts/VeraMono.ttf", "Bitstream Vera Sans Mono"));
		known.add(new Face("none", "none"));
		for (int i = 0; i < known.size(); i++) {
			Face f = known.get(i);
			if (path.equals(f.path)) {
				return f.label;
			}
		}
		if ("sans serrif".equals(path)) {
			return "Sans serif";
		}
		String name = path;
		int slash = path.lastIndexOf('/');
		if (slash >= 0 && slash < path.length() - 1) {
			name = path.substring(slash + 1);
		}
		if (name.length() > 4) {
			String lower = name.toLowerCase();
			if (lower.endsWith(".ttf") || lower.endsWith(".otf")) {
				name = name.substring(0, name.length() - 4);
			}
		}
		if ("DejaVuSansMono".equals(name)) {
			return "DejaVu Sans Mono";
		}
		if ("LiberationMono-Regular".equals(name)) {
			return "Liberation Mono";
		}
		if ("VeraMono".equals(name)) {
			return "Bitstream Vera Sans Mono";
		}
		if ("NotoSansMono-Regular".equals(name)) {
			return "Noto Sans Mono";
		}
		if ("FairfaxHD".equals(name)) {
			return "Fairfax HD";
		}
		if ("JetBrainsMonoNL-Regular".equals(name)) {
			return "JetBrains Mono";
		}
		if ("UbuntuSansMono-Regular".equals(name)) {
			return "Ubuntu Sans Mono";
		}
		if ("AtkinsonHyperlegibleMono-Regular".equals(name)) {
			return "Atkinson Hyperlegible Mono";
		}
		if ("Inconsolata-Regular".equals(name)) {
			return "Inconsolata";
		}
		if ("DroidSansMono".equals(name)) {
			return "Droid Sans Mono";
		}
		if ("RobotoMono-Regular".equals(name)) {
			return "Roboto Mono";
		}
		return name.replace('-', ' ').replace('_', ' ');
	}

	public static boolean isFontFileName(final String filename) {
		if (filename == null) {
			return false;
		}
		String lower = filename.toLowerCase();
		return lower.endsWith(".ttf") || lower.endsWith(".otf");
	}

	public static boolean isBundledAssetPath(final String path) {
		if (path == null) {
			return false;
		}
		if (!path.startsWith("fonts/")) {
			return false;
		}
		return isFontFileName(path);
	}
}
