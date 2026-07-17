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
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

import com.resurrection.blowtorch2.lib.ui.SDCardUtils;

/**
 * Incremental plain-text session log (append-only). Default directory is
 * {@code /BlowTorch/session_logs/} (see {@link SDCardUtils}).
 */
public final class SessionLogger {

	private static final String TAG = "SessionLogger";
	private static final String PREFS = "SESSION_LOG_PREFS";
	private static final String KEY_ENABLED = "enabled";
	private static final String KEY_CUSTOM_DIR = "custom_dir";
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
				if (mapped != null && SDCardUtils.ensureWritableDirectory(mapped)) {
					return mapped;
				}
				return null;
			}
			File custom = new File(customDirCached);
			if (SDCardUtils.ensureWritableDirectory(custom)) {
				return custom;
			}
			BlowTorchLogger.logError(context, TAG,
					"Cannot write session log directory: " + custom.getAbsolutePath());
			return null;
		}
		return SDCardUtils.resolveBlowTorchSubdir(context, SDCardUtils.SUBDIR_SESSION_LOGS);
	}

	public static synchronized void startSession(Context context, String profile) {
		if (context == null || !isEnabled(context)) {
			return;
		}
		String safe = sanitizeProfile(profile);
		String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date());
		String header = "=== BlowTorch session log: " + profile + " @ " + stamp + " ===\n";
		ensurePrefs(context);
		clearCurrent();

		if (SDCardUtils.isContentUri(customDirCached)
				&& SDCardUtils.mapTreeUriToFile(Uri.parse(customDirCached)) == null) {
			DocumentFile tree = DocumentFile.fromTreeUri(context, Uri.parse(customDirCached));
			if (tree != null && tree.canWrite()) {
				DocumentFile file = tree.createFile("text/plain", safe + "_" + stamp + ".txt");
				if (file != null) {
					currentDocUri = file.getUri();
					currentProfile = safe;
					if (!appendRawUri(context, currentDocUri, header)) {
						BlowTorchLogger.logError(context, TAG,
								"Failed to write session log via SAF: " + currentDocUri);
						clearCurrent();
					}
					return;
				}
			}
			BlowTorchLogger.logError(context, TAG,
					"SAF session log tree unusable; falling back to /BlowTorch/session_logs");
		}

		File dir = getLogDirectory(context);
		if (dir == null || !SDCardUtils.ensureWritableDirectory(dir)) {
			dir = SDCardUtils.resolveBlowTorchSubdir(context, SDCardUtils.SUBDIR_SESSION_LOGS);
		}
		if (!SDCardUtils.ensureWritableDirectory(dir)) {
			BlowTorchLogger.logError(context, TAG,
					"Cannot create session log directory: "
							+ (dir != null ? dir.getAbsolutePath() : "(null)")
							+ " — grant All files access (Options → Manage Storage Access)");
			return;
		}
		File target = new File(dir, safe + "_" + stamp + ".txt");
		currentFile = target;
		currentProfile = safe;
		if (!appendRaw(context, currentFile, header)) {
			BlowTorchLogger.logError(context, TAG,
					"Failed to create session log file: " + target.getAbsolutePath());
			clearCurrent();
		} else {
			Log.i(TAG, "Session log started: " + target.getAbsolutePath());
		}
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
			if (!appendRawUri(context, currentDocUri, text)) {
				clearCurrent();
			}
		} else if (currentFile != null) {
			if (!appendRaw(context, currentFile, text)) {
				clearCurrent();
			}
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

	private static boolean appendRaw(Context context, File file, String text) {
		FileOutputStream out = null;
		try {
			File parent = file.getParentFile();
			if (parent != null && !SDCardUtils.ensureWritableDirectory(parent)) {
				Log.e(TAG, "Parent not writable: " + parent.getAbsolutePath());
				return false;
			}
			out = new FileOutputStream(file, true);
			out.write(text.getBytes(StandardCharsets.UTF_8));
			out.flush();
			return true;
		} catch (IOException e) {
			Log.e(TAG, "Write failed: " + file.getAbsolutePath(), e);
			return false;
		} finally {
			if (out != null) {
				try {
					out.close();
				} catch (IOException ignored) {
				}
			}
		}
	}

	private static boolean appendRawUri(Context context, Uri uri, String text) {
		OutputStream out = null;
		try {
			out = context.getContentResolver().openOutputStream(uri, "wa");
			if (out == null) {
				return false;
			}
			out.write(text.getBytes(StandardCharsets.UTF_8));
			out.flush();
			return true;
		} catch (IOException e) {
			Log.e(TAG, "SAF write failed: " + uri, e);
			return false;
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
