package com.resurrection.blowtorch2.lib.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Pattern;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Incremental plain-text session log (append-only). Keeps RAM scrollback bounded
 * while still letting players keep a longer history on disk.
 */
public final class SessionLogger {

	private static final String PREFS = "SESSION_LOG_PREFS";
	private static final String KEY_ENABLED = "enabled";
	private static final String LOG_DIR = "session_logs";
	private static final long MAX_BYTES = 8 * 1024 * 1024;
	private static final Pattern ANSI = Pattern.compile("\u001B\\[[0-9;]*[A-Za-z]");

	private static File currentFile;
	private static String currentProfile;
	private static boolean enabledCached = false;
	private static boolean prefsLoaded = false;

	private SessionLogger() {
	}

	public static synchronized void setEnabled(Context context, boolean enabled) {
		if (context == null) {
			return;
		}
		context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
				.edit()
				.putBoolean(KEY_ENABLED, enabled)
				.apply();
		enabledCached = enabled;
		prefsLoaded = true;
		if (!enabled) {
			currentFile = null;
			currentProfile = null;
		}
	}

	public static synchronized boolean isEnabled(Context context) {
		if (context == null) {
			return false;
		}
		if (!prefsLoaded) {
			enabledCached = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
					.getBoolean(KEY_ENABLED, false);
			prefsLoaded = true;
		}
		return enabledCached;
	}

	public static synchronized File getCurrentLogFile() {
		return currentFile;
	}

	public static synchronized File getLogDirectory(Context context) {
		File dir = new File(context.getFilesDir(), LOG_DIR);
		if (!dir.exists()) {
			dir.mkdirs();
		}
		return dir;
	}

	public static synchronized void startSession(Context context, String profile) {
		if (context == null || !isEnabled(context)) {
			return;
		}
		String safe = sanitizeProfile(profile);
		File dir = getLogDirectory(context);
		String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date());
		currentFile = new File(dir, safe + "_" + stamp + ".txt");
		currentProfile = safe;
		appendRaw(currentFile, "=== BlowTorch session log: " + profile + " @ " + stamp + " ===\n");
	}

	public static synchronized void appendIncoming(Context context, String profile, String text) {
		if (context == null || text == null || text.length() == 0 || !isEnabled(context)) {
			return;
		}
		ensureOpen(context, profile);
		if (currentFile == null) {
			return;
		}
		if (currentFile.length() > MAX_BYTES) {
			rotate(context, profile);
		}
		String plain = ANSI.matcher(text).replaceAll("");
		plain = plain.replace('\r', '\n');
		appendRaw(currentFile, plain);
	}

	public static synchronized void appendMarker(Context context, String profile, String marker) {
		if (context == null || !isEnabled(context)) {
			return;
		}
		ensureOpen(context, profile);
		if (currentFile == null) {
			return;
		}
		String stamp = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
		appendRaw(currentFile, "\n--- " + stamp + " " + marker + " ---\n");
	}

	private static void ensureOpen(Context context, String profile) {
		String safe = sanitizeProfile(profile);
		if (currentFile == null || currentProfile == null || !currentProfile.equals(safe)) {
			startSession(context, profile);
		}
	}

	private static void rotate(Context context, String profile) {
		appendRaw(currentFile, "\n=== log rotated (size limit) ===\n");
		startSession(context, profile);
	}

	private static void appendRaw(File file, String text) {
		FileOutputStream out = null;
		try {
			out = new FileOutputStream(file, true);
			out.write(text.getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (out != null) {
				try {
					out.close();
				} catch (IOException ignored) {
				}
			}
		}
	}

	private static String sanitizeProfile(String profile) {
		if (profile == null || profile.trim().isEmpty()) {
			return "session";
		}
		return profile.replaceAll("[^A-Za-z0-9._-]+", "_");
	}
}
