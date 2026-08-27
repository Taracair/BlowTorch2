package com.resurrection.blowtorch2.lib.launcher;

import android.content.Intent;

/**
 * Extras on a home-screen pin. Kept off {@link Launcher} so {@code FreeLauncher}
 * can forward them without resolving AppCompat types.
 */
public final class LauncherShortcutExtras {

	public static final String DISPLAY = "DISPLAY";
	public static final String HOST = "HOST";
	public static final String PORT = "PORT";
	public static final String TLS = "TLS";
	public static final String LAUNCH_FROM_SHORTCUT = "LAUNCH_FROM_SHORTCUT";

	private LauncherShortcutExtras() {
	}

	public static void copy(Intent from, Intent to) {
		if (from == null || to == null) {
			return;
		}
		if (from.hasExtra(DISPLAY)) {
			to.putExtra(DISPLAY, from.getStringExtra(DISPLAY));
		}
		if (from.hasExtra(HOST)) {
			to.putExtra(HOST, from.getStringExtra(HOST));
		}
		if (from.hasExtra(PORT)) {
			to.putExtra(PORT, from.getStringExtra(PORT));
		}
		if (from.hasExtra(TLS)) {
			to.putExtra(TLS, from.getBooleanExtra(TLS, false));
		}
		if (from.hasExtra(LAUNCH_FROM_SHORTCUT)) {
			to.putExtra(LAUNCH_FROM_SHORTCUT, from.getBooleanExtra(LAUNCH_FROM_SHORTCUT, false));
		}
	}
}
