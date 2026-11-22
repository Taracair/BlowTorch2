package com.resurrection.blowtorch2.lib.settings;

import java.util.regex.Pattern;

/**
 * Settings file in getFilesDir() for a display name.
 * Keeps ASCII letters, digits, and underscore, then adds ".xml".
 * ConnectionSettingsIO loads and saves this name.
 */
public final class SettingsFileNames {

	private SettingsFileNames() {
	}

	public static String xmlFileName(String display) {
		String stripped = Pattern.compile("\\W").matcher(display).replaceAll("");
		stripped = stripped.replaceAll("/", "");
		return stripped + ".xml";
	}

	/** The server list file in getFilesDir(). A world must not use this name. */
	public static boolean isLauncherList(String fileName) {
		return "blowtorch_launcher_list.xml".equals(fileName);
	}

	/** Display name to offer, taken from the picked file's name. */
	public static String suggestDisplayName(String fileName) {
		if (fileName == null) {
			return "Imported";
		}
		String base = fileName.trim();
		int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
		if (slash >= 0) {
			base = base.substring(slash + 1);
		}
		if (base.length() >= 4 && base.regionMatches(true, base.length() - 4, ".xml", 0, 4)) {
			base = base.substring(0, base.length() - 4).trim();
		}
		if (base.length() == 0) {
			return "Imported";
		}
		return base;
	}
}
