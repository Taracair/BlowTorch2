package com.resurrection.blowtorch2.lib.launcher;

import java.util.regex.Pattern;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.resurrection.blowtorch2.lib.settings.ConfigurationLoader;
import com.resurrection.blowtorch2.lib.window.MainWindow;

/**
 * Open a world from a home-screen pin without showing the server list.
 *
 * <p>The pin targets {@code FreeLauncher} (exported). This starts
 * {@code MainWindow} in-process so an already-running session gets
 * {@code onNewIntent} / {@code switchTo} instead of a second task that is
 * just the launcher list.
 */
public final class WorldLaunch {

	private WorldLaunch() {
	}

	/**
	 * @return true when a DISPLAY (extra or {@code blowtorch://world} URI) was
	 *         found and MainWindow was started
	 */
	public static boolean startFromIntent(Context ctx, Intent source) {
		if (ctx == null || source == null) {
			return false;
		}
		String display = LauncherShortcutExtras.displayName(source);
		if (display == null || display.length() == 0) {
			return false;
		}
		String host = LauncherShortcutExtras.host(source);
		String port = LauncherShortcutExtras.port(source);
		boolean tls = LauncherShortcutExtras.tls(source);

		String prefsname = Pattern.compile("\\W").matcher(display).replaceAll("")
				+ ".PREFS";
		ctx.getSharedPreferences(prefsname, 0).edit()
				.putBoolean("CONNECTED", false)
				.putBoolean("FINISHSTART", true)
				.apply();
		ctx.getSharedPreferences("SERVICE_INFO", 0).edit()
				.putString("SETTINGS_PATH", display)
				.commit();

		String windowAction = ConfigurationLoader.getConfigurationValue(
				"windowAction", ctx);
		Intent world = new Intent(windowAction);
		world.setClass(ctx, MainWindow.class);
		// Same flags as the foreground notification: MainWindow is singleTask,
		// so this brings the existing world to the front instead of stacking
		// another copy. NEW_TASK is required because FreeLauncher is a
		// different activity (and often a different task) than MainWindow.
		world.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
				| Intent.FLAG_ACTIVITY_SINGLE_TOP
				| Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
		world.putExtra(LauncherShortcutExtras.DISPLAY, display);
		if (host != null && host.length() > 0) {
			world.putExtra(LauncherShortcutExtras.HOST, host);
		}
		if (port != null && port.length() > 0) {
			world.putExtra(LauncherShortcutExtras.PORT, port);
		}
		// Missing TLS extra is "no opinion". Writing false here is how a TLS
		// world reconnects in the clear when the launcher dropped extras.
		if (LauncherShortcutExtras.hasTls(source)) {
			world.putExtra(LauncherShortcutExtras.TLS, tls);
		}
		ctx.startActivity(world);
		return true;
	}
}
