package com.resurrection.blowtorch2.lib.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Pattern;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;

import androidx.documentfile.provider.DocumentFile;

import com.resurrection.blowtorch2.lib.ui.SDCardUtils;

/**
 * Incremental plain-text session log (append-only). Keeps RAM scrollback bounded
 * while still letting players keep a longer history on disk.
 */
public final class SessionLogger {

	private static final String PREFS = "SESSION_LOG_PREFS";
	private static final String KEY_ENABLED = "enabled";
	private static final String KEY_CUSTOM_DIR = "custom_dir";
	private static final String LOG_DIR = "session_logs";
	private static final long MAX_BYTES = 8 * 1024 * 1024;
	private static final Pattern ANSI = Pattern.compile("\u001B\\[[0-9;]*[A-Za-z]");

	private static File currentFile;
	private static Uri currentDocUri;
	private static String currentProfile;
	private static boolean enabledCached = false;
	private static String customDirCached = "";
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
			clearCurrent();
		}
	}

	public static synchronized void setCustomDirectory(Context context, String path) {
		if (context == null) {
			return;
		}
		String normalized = path == null ? "" : path.trim();
		context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
				.edit()
				.putString(KEY_CUSTOM_DIR, normalized)
				.apply();
		customDirCached = normalized;
		prefsLoaded = true;
		// Force reopen on next append so path changes take effect.
		clearCurrent();
	}

	public static synchronized boolean isEnabled(Context context) {
		if (context == null) {
			return false;
		}
		ensurePrefs(context);
		return enabledCached;
	}

	public static synchronized File getCurrentLogFile() {
		return currentFile;
	}

	/** Display path for UI: filesystem path, SAF document URI, or directory label. */
	public static synchronized String getLogLocationLabel(Context context) {
		if (currentDocUri != null) {
			return currentDocUri.toString();
		}
		if (currentFile != null) {
			return currentFile.getAbsolutePath();
		}
		ensurePrefs(context);
		if (SDCardUtils.isContentUri(customDirCached)) {
			return customDirCached;
		}
		File dir = getLogDirectory(context);
		return dir != null ? dir.getAbsolutePath() : "";
	}

	public static synchronized File getLogDirectory(Context context) {
		ensurePrefs(context);
		if (!TextUtils.isEmpty(customDirCached)) {
			if (SDCardUtils.isContentUri(customDirCached)) {
				File mapped = SDCardUtils.mapTreeUriToFile(Uri.parse(customDirCached));
				if (mapped != null) {
					if (!mapped.exists()) {
						//noinspection ResultOfMethodCallIgnored
						mapped.mkdirs();
					}
					if (mapped.isDirectory()) {
						return mapped;
					}
				}
				// Unmapped content tree — no File; callers should use getLogLocationLabel.
				return null;
			}
			File custom = new File(customDirCached);
			if (!custom.exists()) {
				custom.mkdirs();
			}
			if (custom.isDirectory()) {
				return custom;
			}
		}
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
		String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date());
		String header = "=== BlowTorch session log: " + profile + " @ " + stamp + " ===\n";
		ensurePrefs(context);

		if (SDCardUtils.isContentUri(customDirCached)
				&& SDCardUtils.mapTreeUriToFile(Uri.parse(customDirCached)) == null) {
			DocumentFile tree = DocumentFile.fromTreeUri(context, Uri.parse(customDirCached));
			if (tree != null && tree.canWrite()) {
				DocumentFile file = tree.createFile("text/plain", safe + "_" + stamp + ".txt");
				if (file != null) {
					currentDocUri = file.getUri();
					currentFile = null;
					currentProfile = safe;
					appendRawUri(context, currentDocUri, header);
					return;
				}
			}
			// Fall back to private folder if SAF tree is unusable.
		}

		File dir = getLogDirectory(context);
		if (dir == null) {
			dir = new File(context.getFilesDir(), LOG_DIR);
			if (!dir.exists()) {
				dir.mkdirs();
			}
		}
		currentFile = new File(dir, safe + "_" + stamp + ".txt");
		currentDocUri = null;
		currentProfile = safe;
		appendRaw(currentFile, header);
	}

	public static synchronized void appendIncoming(Context context, String profile, String text) {
		if (context == null || text == null || text.length() == 0 || !isEnabled(context)) {
			return;
		}
		ensureOpen(context, profile);
		if (currentFile == null && currentDocUri == null) {
			return;
		}
		if (currentLength() > MAX_BYTES) {
			rotate(context, profile);
		}
		String plain = ANSI.matcher(text).replaceAll("");
		plain = plain.replace('\r', '\n');
		appendCurrent(context, plain);
	}

	public static synchronized void appendMarker(Context context, String profile, String marker) {
		if (context == null || !isEnabled(context)) {
			return;
		}
		ensureOpen(context, profile);
		if (currentFile == null && currentDocUri == null) {
			return;
		}
		String stamp = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
		appendCurrent(context, "\n--- " + stamp + " " + marker + " ---\n");
	}

	private static void clearCurrent() {
		currentFile = null;
		currentDocUri = null;
		currentProfile = null;
	}

	private static long currentLength() {
		if (currentFile != null) {
			return currentFile.length();
		}
		return 0L;
	}

	private static void appendCurrent(Context context, String text) {
		if (currentDocUri != null) {
			appendRawUri(context, currentDocUri, text);
		} else if (currentFile != null) {
			appendRaw(currentFile, text);
		}
	}

	private static void ensurePrefs(Context context) {
		if (prefsLoaded || context == null) {
			return;
		}
		SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
		enabledCached = prefs.getBoolean(KEY_ENABLED, false);
		customDirCached = prefs.getString(KEY_CUSTOM_DIR, "");
		if (customDirCached == null) {
			customDirCached = "";
		}
		prefsLoaded = true;
	}

	private static void ensureOpen(Context context, String profile) {
		String safe = sanitizeProfile(profile);
		if ((currentFile == null && currentDocUri == null)
				|| currentProfile == null
				|| !currentProfile.equals(safe)) {
			startSession(context, profile);
		}
	}

	private static void rotate(Context context, String profile) {
		appendCurrent(context, "\n=== log rotated (size limit) ===\n");
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

	private static void appendRawUri(Context context, Uri uri, String text) {
		OutputStream out = null;
		try {
			out = context.getContentResolver().openOutputStream(uri, "wa");
			if (out == null) {
				return;
			}
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
