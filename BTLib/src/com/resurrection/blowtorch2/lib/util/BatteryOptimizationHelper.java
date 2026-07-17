package com.resurrection.blowtorch2.lib.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

/**
 * Prompts the user to exempt BlowTorch from battery optimization so background
 * keepalive / foreground-service connections are less likely to be killed.
 */
public final class BatteryOptimizationHelper {

	private static final String PREFS = "BATTERY_OPT_PREFS";
	private static final String KEY_PROMPTED = "prompted_v1";
	private static final String KEY_DONT_ASK = "dont_ask_v1";

	private BatteryOptimizationHelper() {
	}

	public static boolean isIgnoringBatteryOptimizations(Context context) {
		if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
			return true;
		}
		PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
		if (pm == null) {
			return true;
		}
		return pm.isIgnoringBatteryOptimizations(context.getPackageName());
	}

	/** Opens the system battery-exemption screen for this package when possible. */
	public static void openExemptionSettings(Context context) {
		if (context == null) {
			return;
		}
		try {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
				intent.setData(Uri.parse("package:" + context.getPackageName()));
				if (!(context instanceof Activity)) {
					intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				}
				context.startActivity(intent);
				return;
			}
		} catch (Exception ignored) {
		}
		openAppBatterySettings(context);
	}

	/** Fallback: app details / battery settings deep-link. */
	public static void openAppBatterySettings(Context context) {
		if (context == null) {
			return;
		}
		try {
			Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
			if (!(context instanceof Activity)) {
				intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			}
			context.startActivity(intent);
			return;
		} catch (Exception ignored) {
		}
		try {
			Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
			intent.setData(Uri.parse("package:" + context.getPackageName()));
			if (!(context instanceof Activity)) {
				intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			}
			context.startActivity(intent);
		} catch (Exception e) {
			Toast.makeText(context, "Open system settings → Apps → BlowTorch → Battery", Toast.LENGTH_LONG).show();
		}
	}

	/**
	 * Shows a one-shot (or until dismissed permanently) dialog when battery
	 * optimization may kill a background MUD connection.
	 */
	public static void maybePrompt(Activity activity) {
		if (activity == null || activity.isFinishing()) {
			return;
		}
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
			return;
		}
		if (isIgnoringBatteryOptimizations(activity)) {
			return;
		}
		SharedPreferences prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
		if (prefs.getBoolean(KEY_DONT_ASK, false)) {
			return;
		}
		if (prefs.getBoolean(KEY_PROMPTED, false)) {
			return;
		}
		prefs.edit().putBoolean(KEY_PROMPTED, true).apply();

		new AlertDialog.Builder(activity)
				.setTitle("Keep connection alive")
				.setMessage("Android battery optimization can kill background MUD connections. "
						+ "Allow BlowTorch to run unrestricted so Wi‑Fi keepalive and the "
						+ "connection notification keep working when the screen is off.")
				.setPositiveButton("Allow", (d, w) -> openExemptionSettings(activity))
				.setNeutralButton("Don't ask again", (d, w) ->
						prefs.edit().putBoolean(KEY_DONT_ASK, true).apply())
				.setNegativeButton("Not now", null)
				.show();
	}

	/** Force the prompt even if already shown (Options / Help entry points). */
	public static void promptNow(Activity activity) {
		if (activity == null || activity.isFinishing()) {
			return;
		}
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
			Toast.makeText(activity, "Not required on this Android version.", Toast.LENGTH_SHORT).show();
			return;
		}
		if (isIgnoringBatteryOptimizations(activity)) {
			Toast.makeText(activity, "Battery optimization already disabled for BlowTorch.", Toast.LENGTH_SHORT).show();
			return;
		}
		new AlertDialog.Builder(activity)
				.setTitle("Battery optimization")
				.setMessage("Exempt BlowTorch so background connections are less likely to drop.")
				.setPositiveButton("Open settings", (d, w) -> openExemptionSettings(activity))
				.setNegativeButton("Cancel", null)
				.show();
	}
}
