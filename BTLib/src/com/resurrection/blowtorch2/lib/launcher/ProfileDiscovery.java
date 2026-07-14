package com.resurrection.blowtorch2.lib.launcher;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Locale;

import android.content.Context;

/**
 * Finds settings XML in internal storage and adds missing launcher entries.
 * Pushing only {@code files/test.xml} is not enough — the launcher list must reference it.
 */
public final class ProfileDiscovery {

	private static final String LAUNCHER_LIST = "blowtorch_launcher_list.xml";

	private ProfileDiscovery() {
	}

	public static int mergeDiscoveredProfiles(Context context, LauncherSettings settings) {
		if (context == null || settings == null) {
			return 0;
		}
		File filesDir = context.getFilesDir();
		if (filesDir == null || !filesDir.isDirectory()) {
			return 0;
		}
		File[] files = filesDir.listFiles();
		if (files == null) {
			return 0;
		}
		int added = 0;
		for (File file : files) {
			if (!isSettingsProfile(file)) {
				continue;
			}
			String displayName = file.getName().replaceFirst("(?i)\\.xml$", "");
			if (settings.getList().containsKey(displayName)) {
				continue;
			}
			MudConnection connection = new MudConnection();
			connection.setDisplayName(displayName);
			connection.setHostName("host not set");
			connection.setPortString("4000");
			settings.getList().put(displayName, connection);
			added++;
		}
		return added;
	}

	private static boolean isSettingsProfile(File file) {
		if (file == null || !file.isFile()) {
			return false;
		}
		String name = file.getName();
		if (!name.toLowerCase(Locale.US).endsWith(".xml")) {
			return false;
		}
		if (LAUNCHER_LIST.equalsIgnoreCase(name) || name.toLowerCase(Locale.US).endsWith(".bak")) {
			return false;
		}
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new FileReader(file));
			char[] header = new char[256];
			int read = reader.read(header);
			if (read <= 0) {
				return false;
			}
			String start = new String(header, 0, read);
			return start.contains("<blowtorch");
		} catch (IOException e) {
			return false;
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException ignored) {
				}
			}
		}
	}
}
