package com.resurrection.blowtorch2.lib.launcher;

import java.util.regex.Pattern;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import com.resurrection.blowtorch2.lib.settings.ConfigurationLoader;
import com.resurrection.blowtorch2.lib.window.MainWindow;

/**
 * Open a world from a home-screen pin. Do not set
 * {@link Intent#FLAG_ACTIVITY_RESET_TASK_IF_NEEDED} — that flag brings the
 * server list forward as-is. Trampoline is not {@code excludeFromRecents}
 * (that hid the game); {@code noHistory} still drops it from the back stack.
 */
public final class WorldLaunch {

	/**
	 * Flags for starting {@link MainWindow} from a pin trampoline.
	 *
	 * <p>{@code NEW_TASK} is required when the caller is not sitting in
	 * MainWindow's task (the trampoline, or {@link #startFromIntent} via
	 * the application context so finishing the trampoline cannot cancel
	 * the start). {@code SINGLE_TOP} + {@code CLEAR_TOP} reuse a live
	 * {@code singleTask} MainWindow instead of stacking another copy.
	 */
	public static final int MAIN_WINDOW_LAUNCH_FLAGS =
			Intent.FLAG_ACTIVITY_NEW_TASK
					| Intent.FLAG_ACTIVITY_SINGLE_TOP
					| Intent.FLAG_ACTIVITY_CLEAR_TOP;

	private WorldLaunch() {
	}

	/**
	 * Leave the game window for the server list. When MainWindow is the task
	 * root (home-screen pin), {@code CLEAR_TASK} replaces that task with
	 * Launcher — {@code finish()} of a task root would otherwise drop the
	 * Launcher that was just started. When it is not root (app icon → list →
	 * world), reuse the existing list with {@code CLEAR_TOP} and do not
	 * {@code CLEAR_TASK}.
	 *
	 * <p>The Intent has no DISPLAY extras: those would look like a pin and
	 * re-fire the world.
	 */
	public static int returnToServerListFlags(boolean taskRoot) {
		if (taskRoot) {
			return Intent.FLAG_ACTIVITY_NEW_TASK
					| Intent.FLAG_ACTIVITY_CLEAR_TASK;
		}
		return Intent.FLAG_ACTIVITY_NEW_TASK
				| Intent.FLAG_ACTIVITY_CLEAR_TOP
				| Intent.FLAG_ACTIVITY_SINGLE_TOP;
	}

	public static void returnToServerList(Activity from) {
		if (from == null) {
			return;
		}
		Intent launch = new Intent(from, Launcher.class);
		launch.addFlags(returnToServerListFlags(from.isTaskRoot()));
		from.startActivity(launch);
	}

	/**
	 * Invisible trampoline entry: start the named world, or the server list
	 * when the Intent has no DISPLAY.
	 */
	public static void handoff(Activity trampoline) {
		if (trampoline == null) {
			return;
		}
		Intent incoming = trampoline.getIntent();
		if (startFromIntent(trampoline, incoming)) {
			trampoline.overridePendingTransition(0, 0);
			trampoline.finish();
			return;
		}
		Intent launch = new Intent(trampoline, Launcher.class);
		LauncherShortcutExtras.copy(incoming, launch);
		launch.putExtra("LAUNCH_MODE", trampoline.getPackageName());
		trampoline.startActivity(launch);
		trampoline.overridePendingTransition(0, 0);
		trampoline.finish();
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
		world.addFlags(MAIN_WINDOW_LAUNCH_FLAGS);
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
		world.putExtra(LauncherShortcutExtras.LAUNCH_FROM_SHORTCUT, true);
		// Application context so the trampoline can finish (windowNoDisplay)
		// without cancelling this start.
		Context start = ctx.getApplicationContext();
		if (start == null) {
			start = ctx;
		}
		start.startActivity(world);
		return true;
	}
}
