package com.resurrection.blowtorch2.lib.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Asks GitHub whether a newer release exists.
 *
 * <p>Off unless the player turns it on. This is the only thing in the app that
 * contacts anything other than a MUD the player added, and F-Droid installs are
 * updated by F-Droid, so it must never be the default.
 *
 * <p>Failure is silence. No network, GitHub down, rate limited, a tag that does
 * not parse — none of that is the player's problem, and a client that nags
 * about its own update check is worse than one that quietly says nothing.
 */
public final class UpdateChecker {

	/** Where a player is sent to get the new build. */
	public static final String RELEASES_URL =
			"https://github.com/Taracair/BlowTorch2/releases/latest";

	private static final String API_URL =
			"https://api.github.com/repos/Taracair/BlowTorch2/releases/latest";

	private static final String PREFS = "blowtorch_update_check";
	private static final String KEY_LAST_CHECK = "last_check_ms";
	private static final String KEY_SKIPPED = "skipped_version";

	/** At most one check a day: a release does not appear more often than that. */
	private static final long MIN_INTERVAL_MS = 24L * 60L * 60L * 1000L;
	private static final int TIMEOUT_MS = 8000;

	/** Delivered on the UI thread when, and only when, something newer exists. */
	public interface Listener {
		/**
		 * @param latestVersion Version string from the release tag, e.g. {@code 2.1.14}.
		 * @param releaseUrl Page to send the player to.
		 */
		void onUpdateAvailable(String latestVersion, String releaseUrl);
	}

	private UpdateChecker() {
	}

	/**
	 * Check in the background, and call back only if there is a newer release.
	 *
	 * @param context Application context.
	 * @param force Ignore the once-a-day limit (for a manual "check now").
	 * @param listener Called on the UI thread; never called on failure.
	 */
	public static void checkAsync(final Context context, final boolean force,
			final Listener listener) {
		if (context == null || listener == null) {
			return;
		}
		final Context app = context.getApplicationContext();
		if (!isReleaseBuild(app)) {
			// The test flavour is rebuilt many times a day and its versionName
			// carries a -test suffix, so every published release looks newer than
			// it and it would nag on every launch. Blocked here rather than by a
			// setting: a build that should never phone home should not depend on
			// a checkbox being right.
			return;
		}
		if (!force && !dueForCheck(app)) {
			return;
		}
		final Handler ui = new Handler(Looper.getMainLooper());
		Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					String current = currentVersion(app);
					String latest = fetchLatestTag();
					if (current == null || latest == null) {
						return;
					}
					markChecked(app);
					if (!force && latest.equals(skippedVersion(app))) {
						return;
					}
					if (compareVersions(latest, current) <= 0) {
						return;
					}
					final String found = latest;
					ui.post(new Runnable() {
						@Override
						public void run() {
							listener.onUpdateAvailable(found, RELEASES_URL);
						}
					});
				} catch (Exception e) {
					// Silence is the contract. Logcat only, never the error log:
					// a failed update check is not a fault a player should see.
					android.util.Log.i("BlowTorch",
							"update check did not complete: " + e);
				}
			}
		}, "update-check");
		t.setDaemon(true);
		t.start();
	}

	/** Remember that the player does not want to be told about this one again. */
	public static void skipVersion(final Context context, final String version) {
		if (context == null || version == null) {
			return;
		}
		prefs(context).edit().putString(KEY_SKIPPED, version).apply();
	}

	private static SharedPreferences prefs(final Context context) {
		return context.getApplicationContext().getSharedPreferences(PREFS, 0);
	}

	private static String skippedVersion(final Context context) {
		return prefs(context).getString(KEY_SKIPPED, "");
	}

	private static boolean dueForCheck(final Context context) {
		long last = prefs(context).getLong(KEY_LAST_CHECK, 0L);
		return System.currentTimeMillis() - last >= MIN_INTERVAL_MS;
	}

	private static void markChecked(final Context context) {
		prefs(context).edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply();
	}

	/** The one package id that GitHub releases are published for. */
	private static final String RELEASE_PACKAGE = "com.resurrection.blowtorch2";

	/**
	 * @param context Application context.
	 * @return true only for the production package. The {@code .test} flavour,
	 *     and anything renamed downstream, never checks.
	 */
	static boolean isReleaseBuild(final Context context) {
		return context != null && RELEASE_PACKAGE.equals(context.getPackageName());
	}

	/** @return This build's versionName, or null if it cannot be read. */
	public static String currentVersion(final Context context) {
		try {
			return context.getPackageManager()
					.getPackageInfo(context.getPackageName(), 0).versionName;
		} catch (PackageManager.NameNotFoundException e) {
			return null;
		}
	}

	private static String fetchLatestTag() throws Exception {
		HttpURLConnection conn = null;
		try {
			conn = (HttpURLConnection) new URL(API_URL).openConnection();
			conn.setRequestMethod("GET");
			conn.setConnectTimeout(TIMEOUT_MS);
			conn.setReadTimeout(TIMEOUT_MS);
			conn.setRequestProperty("Accept", "application/vnd.github+json");
			conn.setRequestProperty("User-Agent", "BlowTorch2");
			if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
				return null;
			}
			StringBuilder sb = new StringBuilder();
			BufferedReader in = new BufferedReader(new InputStreamReader(
					conn.getInputStream(), StandardCharsets.UTF_8));
			try {
				String line;
				while ((line = in.readLine()) != null) {
					sb.append(line);
					// A release body can be long and none of it is needed.
					if (sb.length() > 64 * 1024) {
						break;
					}
				}
			} finally {
				in.close();
			}
			JSONObject o = new JSONObject(sb.toString());
			if (o.optBoolean("draft", false) || o.optBoolean("prerelease", false)) {
				return null;
			}
			String tag = o.optString("tag_name", "");
			return tag.length() == 0 ? null : normalizeVersion(tag);
		} finally {
			if (conn != null) {
				conn.disconnect();
			}
		}
	}

	/** Strip a leading {@code v} and surrounding space: {@code v2.1.14} → {@code 2.1.14}. */
	static String normalizeVersion(final String raw) {
		if (raw == null) {
			return null;
		}
		String s = raw.trim();
		if (s.length() > 1 && (s.charAt(0) == 'v' || s.charAt(0) == 'V')) {
			s = s.substring(1);
		}
		return s.trim();
	}

	/**
	 * Compare dotted version strings numerically, shorter padded with zeros, so
	 * {@code 2.2} beats {@code 2.1.13} and {@code 2.1.2} beats {@code 2.1.10}
	 * never happens the way string comparison would have it.
	 *
	 * <p>Anything non-numeric in a segment makes that segment sort as 0 rather
	 * than throwing: a tag like {@code 2.1.14-beta} should not crash a check,
	 * and treating the suffix as older is the safe direction.
	 *
	 * @return negative if {@code a} is older, 0 if equal, positive if newer.
	 */
	static int compareVersions(final String a, final String b) {
		String[] left = normalizeVersion(a == null ? "" : a).split("\\.");
		String[] right = normalizeVersion(b == null ? "" : b).split("\\.");
		int n = Math.max(left.length, right.length);
		for (int i = 0; i < n; i++) {
			int l = i < left.length ? numericPrefix(left[i]) : 0;
			int r = i < right.length ? numericPrefix(right[i]) : 0;
			if (l != r) {
				return l < r ? -1 : 1;
			}
		}
		return 0;
	}

	/** Leading digits of a segment; 0 when it does not start with any. */
	private static int numericPrefix(final String segment) {
		if (segment == null) {
			return 0;
		}
		int end = 0;
		while (end < segment.length() && Character.isDigit(segment.charAt(end))) {
			end++;
		}
		if (end == 0) {
			return 0;
		}
		try {
			return Integer.parseInt(segment.substring(0, end));
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}
