package com.resurrection.blowtorch2.lib.util;

import java.io.BufferedOutputStream;
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
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

import com.resurrection.blowtorch2.lib.ui.SDCardUtils;

/**
 * Incremental plain-text session log (append-only). Default directory is
 * {@code /BlowTorch/session_logs/} (see {@link SDCardUtils}).
 * <p>
 * Writes are kept in an open stream and flushed to disk on a short interval
 * (or when a few KB accumulate), plus on disconnect / disable — so a file
 * manager sees near-live growth without opening the file on every MUD packet.
 */
public final class SessionLogger {

	private static final String TAG = "SessionLogger";
	private static final String PREFS = "SESSION_LOG_PREFS";
	private static final String KEY_ENABLED = "enabled";
	private static final String KEY_CUSTOM_DIR = "custom_dir";
	private static final long MAX_BYTES = 8 * 1024 * 1024;
	/** Flush to OS buffers at least this often while data arrives. */
	private static final long FLUSH_INTERVAL_MS = 750L;
	/** Flush sooner if this much is buffered. */
	private static final int FLUSH_BYTES = 4 * 1024;
	private static final Pattern ANSI = Pattern.compile("\u001B\\[[0-9;]*[A-Za-z]");

	private static File currentFile;
	private static Uri currentDocUri;
	private static String currentProfile;
	private static boolean enabledCached = false;
	private static String customDirCached = "";
	private static boolean prefsLoaded = false;

	private static FileOutputStream currentFos;
	private static OutputStream currentOut;
	private static long pendingBytes;
	private static long lastFlushElapsed;
	private static long bytesWrittenThisFile;
	private static Handler flushHandler;
	private static final Runnable FLUSH_RUNNABLE = new Runnable() {
		@Override
		public void run() {
			synchronized (SessionLogger.class) {
				flushLocked(true);
			}
		}
	};

	private SessionLogger() {
	}

	public static synchronized void setEnabled(Context context, boolean enabled) {
		if (context == null) {
			return;
		}
		ensurePrefs(context);
		boolean wasEnabled = enabledCached;
		context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
				.edit()
				.putBoolean(KEY_ENABLED, enabled)
				.apply();
		enabledCached = enabled;
		prefsLoaded = true;
		if (!enabled && wasEnabled) {
			if (currentOut != null || currentFile != null || currentDocUri != null) {
				appendMarkerUnlocked(context,
						currentProfile != null ? currentProfile : "session",
						"logging disabled");
			}
			closeStreamLocked(true);
			clearCurrentMeta();
		}
	}

	public static synchronized void setCustomDirectory(Context context, String path) {
		if (context == null) {
			return;
		}
		String normalized = path == null ? "" : path.trim();
		ensurePrefs(context);
		boolean changed = !normalized.equals(customDirCached);
		context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
				.edit()
				.putString(KEY_CUSTOM_DIR, normalized)
				.apply();
		customDirCached = normalized;
		prefsLoaded = true;
		// Only start a new file when the directory actually changes — re-applying
		// the same Options value must not truncate an active session log.
		if (changed) {
			closeStreamLocked(true);
			clearCurrentMeta();
		}
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
		closeStreamLocked(true);
		clearCurrentMeta();

		if (SDCardUtils.isContentUri(customDirCached)
				&& SDCardUtils.mapTreeUriToFile(Uri.parse(customDirCached)) == null) {
			DocumentFile tree = DocumentFile.fromTreeUri(context, Uri.parse(customDirCached));
			if (tree != null && tree.canWrite()) {
				DocumentFile file = tree.createFile("text/plain", safe + "_" + stamp + ".txt");
				if (file != null) {
					currentDocUri = file.getUri();
					currentProfile = safe;
					if (!openStreamLocked(context) || !writeLocked(context, header, true)) {
						BlowTorchLogger.logError(context, TAG,
								"Failed to write session log via SAF: " + currentDocUri);
						closeStreamLocked(false);
						clearCurrentMeta();
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
		if (!openStreamLocked(context) || !writeLocked(context, header, true)) {
			BlowTorchLogger.logError(context, TAG,
					"Failed to create session log file: " + target.getAbsolutePath());
			closeStreamLocked(false);
			clearCurrentMeta();
		} else {
			Log.i(TAG, "Session log started: " + target.getAbsolutePath());
		}
	}

	/**
	 * Keep writing to the current profile file after a reconnect; otherwise
	 * {@link #startSession}.
	 */
	public static synchronized void continueOrStartSession(Context context, String profile) {
		if (context == null || !isEnabled(context)) {
			return;
		}
		String safe = sanitizeProfile(profile);
		boolean sameProfile = currentProfile != null && currentProfile.equals(safe);
		boolean haveFile = (currentFile != null && currentFile.exists()) || currentDocUri != null;
		if (sameProfile && haveFile) {
			if (currentOut == null && !openStreamLocked(context)) {
				startSession(context, profile);
			}
			return;
		}
		startSession(context, profile);
	}

	/** True if a log file is already associated with this profile (open or after flush). */
	public static synchronized boolean hasActiveSessionFor(String profile) {
		String safe = sanitizeProfile(profile);
		if (currentProfile == null || !currentProfile.equals(safe)) {
			return false;
		}
		return (currentFile != null && currentFile.exists()) || currentDocUri != null;
	}

	public static synchronized void appendIncoming(Context context, String profile, String text) {
		if (context == null || text == null || text.length() == 0 || !isEnabled(context)) {
			return;
		}
		ensureOpen(context, profile);
		if (currentFile == null && currentDocUri == null) {
			return;
		}
		if (bytesWrittenThisFile > MAX_BYTES) {
			rotate(context, profile);
		}
		String plain = ANSI.matcher(text).replaceAll("");
		plain = plain.replace('\r', '\n');
		writeLocked(context, plain, false);
	}

	public static synchronized void appendMarker(Context context, String profile, String marker) {
		if (context == null || !isEnabled(context)) {
			return;
		}
		ensureOpen(context, profile);
		if (currentFile == null && currentDocUri == null) {
			return;
		}
		appendMarkerUnlocked(context, profile, marker);
	}

	/**
	 * Push buffered log bytes to disk now (and fsync when possible). Safe to call
	 * often; used on disconnect and when the app backgrounds.
	 */
	public static synchronized void flush(Context context) {
		flushLocked(true);
	}

	/** Flush and close the open stream without starting a new file. */
	public static synchronized void endSession(Context context) {
		if (context != null && (currentFile != null || currentDocUri != null)) {
			appendMarkerUnlocked(context,
					currentProfile != null ? currentProfile : "session", "disconnected");
		}
		closeStreamLocked(true);
		// Keep currentFile/currentDocUri so UI can still show the last path;
		// next startSession/ensureOpen will replace them.
	}

	private static void appendMarkerUnlocked(Context context, String profile, String marker) {
		if (marker == null) {
			return;
		}
		String stamp = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
		writeLocked(context, "\n--- " + stamp + " " + marker + " ---\n", true);
	}

	private static void clearCurrentMeta() {
		currentFile = null;
		currentDocUri = null;
		currentProfile = null;
		bytesWrittenThisFile = 0L;
		pendingBytes = 0L;
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
				|| !currentProfile.equals(safe)
				|| currentOut == null) {
			startSession(context, profile);
		}
	}

	private static void rotate(Context context, String profile) {
		writeLocked(context, "\n=== log rotated (size limit) ===\n", true);
		startSession(context, profile);
	}

	private static boolean openStreamLocked(Context context) {
		closeStreamLocked(false);
		pendingBytes = 0L;
		bytesWrittenThisFile = 0L;
		lastFlushElapsed = SystemClock.elapsedRealtime();
		try {
			if (currentDocUri != null) {
				OutputStream raw = context.getContentResolver()
						.openOutputStream(currentDocUri, "wa");
				if (raw == null) {
					return false;
				}
				currentFos = null;
				currentOut = new BufferedOutputStream(raw, 8192);
				return true;
			}
			if (currentFile != null) {
				File parent = currentFile.getParentFile();
				if (parent != null && !SDCardUtils.ensureWritableDirectory(parent)) {
					Log.e(TAG, "Parent not writable: " + parent.getAbsolutePath());
					return false;
				}
				currentFos = new FileOutputStream(currentFile, true);
				currentOut = new BufferedOutputStream(currentFos, 8192);
				bytesWrittenThisFile = currentFile.length();
				return true;
			}
		} catch (IOException e) {
			Log.e(TAG, "Open stream failed", e);
			currentOut = null;
			currentFos = null;
			return false;
		}
		return false;
	}

	private static boolean writeLocked(Context context, String text, boolean forceFlush) {
		if (text == null || text.length() == 0) {
			return true;
		}
		if (currentOut == null && !openStreamLocked(context)) {
			return false;
		}
		try {
			byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
			currentOut.write(bytes);
			pendingBytes += bytes.length;
			bytesWrittenThisFile += bytes.length;
			long now = SystemClock.elapsedRealtime();
			if (forceFlush
					|| pendingBytes >= FLUSH_BYTES
					|| (now - lastFlushElapsed) >= FLUSH_INTERVAL_MS) {
				flushLocked(true);
			} else {
				scheduleFlushLocked();
			}
			return true;
		} catch (IOException e) {
			Log.e(TAG, "Write failed", e);
			closeStreamLocked(false);
			clearCurrentMeta();
			return false;
		}
	}

	private static void scheduleFlushLocked() {
		if (flushHandler == null) {
			flushHandler = new Handler(Looper.getMainLooper());
		}
		flushHandler.removeCallbacks(FLUSH_RUNNABLE);
		long delay = Math.max(50L, FLUSH_INTERVAL_MS - (SystemClock.elapsedRealtime() - lastFlushElapsed));
		flushHandler.postDelayed(FLUSH_RUNNABLE, delay);
	}

	private static void flushLocked(boolean syncToDisk) {
		if (flushHandler != null) {
			flushHandler.removeCallbacks(FLUSH_RUNNABLE);
		}
		if (currentOut == null) {
			pendingBytes = 0L;
			return;
		}
		try {
			currentOut.flush();
			if (syncToDisk && currentFos != null) {
				try {
					currentFos.getFD().sync();
				} catch (IOException ignored) {
					// Some filesystems reject sync; flush is still enough for most readers.
				}
			}
			pendingBytes = 0L;
			lastFlushElapsed = SystemClock.elapsedRealtime();
		} catch (IOException e) {
			Log.e(TAG, "Flush failed", e);
			closeStreamLocked(false);
			clearCurrentMeta();
		}
	}

	private static void closeStreamLocked(boolean flushFirst) {
		if (flushHandler != null) {
			flushHandler.removeCallbacks(FLUSH_RUNNABLE);
		}
		if (currentOut == null) {
			currentFos = null;
			pendingBytes = 0L;
			return;
		}
		try {
			if (flushFirst) {
				currentOut.flush();
				if (currentFos != null) {
					try {
						currentFos.getFD().sync();
					} catch (IOException ignored) {
					}
				}
			}
		} catch (IOException e) {
			Log.e(TAG, "Close flush failed", e);
		}
		try {
			currentOut.close();
		} catch (IOException e) {
			Log.e(TAG, "Close failed", e);
		}
		currentOut = null;
		currentFos = null;
		pendingBytes = 0L;
	}

	private static String sanitizeProfile(String profile) {
		if (profile == null || profile.trim().isEmpty()) {
			return "session";
		}
		return profile.replaceAll("[^A-Za-z0-9._-]+", "_");
	}
}
