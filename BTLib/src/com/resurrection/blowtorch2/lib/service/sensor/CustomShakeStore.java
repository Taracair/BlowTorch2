package com.resurrection.blowtorch2.lib.service.sensor;

import android.content.Context;

/**
 * Recorded shakes for this install, not the world profile. Read and written
 * in {@code :stellar}; the UI asks the service rather than opening the file
 * itself, because the two processes do not share a preference cache.
 */
public final class CustomShakeStore {

	private static final String PREFS = "bt_custom_shakes";
	private static final String KEY = "library";

	private CustomShakeStore() {
	}

	public static CustomShakeLibrary load(final Context context) {
		if (context == null) {
			return CustomShakeLibrary.empty();
		}
		try {
			String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
					.getString(KEY, "");
			return CustomShakeLibrary.decode(raw);
		} catch (Exception e) {
			return CustomShakeLibrary.empty();
		}
	}

	public static void save(final Context context, final String encoded) {
		if (context == null) {
			return;
		}
		CustomShakeLibrary decoded = CustomShakeLibrary.decode(encoded);
		if (dropsClaimedEntries(encoded, decoded)) {
			return;
		}
		String canonical = decoded.encode();
		try {
			context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
					.putString(KEY, canonical).apply();
		} catch (Exception ignored) {
		}
	}

	/**
	 * True when {@code encoded} claims shakes that {@code decoded} threw away.
	 * Writing that empty result would erase the library already on the phone.
	 */
	static boolean dropsClaimedEntries(final String encoded,
			final CustomShakeLibrary decoded) {
		if (decoded != null && !decoded.entries().isEmpty()) {
			return false;
		}
		if (encoded == null || encoded.trim().length() == 0) {
			return false;
		}
		String[] lines = encoded.split("\n", 3);
		if (lines.length < 2 || !"1".equals(lines[0].trim())) {
			return true;
		}
		try {
			return Integer.parseInt(lines[1].trim()) != 0;
		} catch (NumberFormatException bad) {
			return true;
		}
	}
}
