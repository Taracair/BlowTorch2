package com.resurrection.blowtorch2.lib.launcher;

import android.content.Intent;
import android.net.Uri;

/**
 * Extras and URI on a home-screen pin. Kept off {@link Launcher} so the
 * trampolines can forward them without resolving AppCompat types.
 *
 * <p>Do not pin {@code ACTION_MAIN} + {@code CATEGORY_LAUNCHER}, and do not
 * target {@code FreeLauncher}: that is the app-icon component, and home
 * screens drop extras or resume the existing task as-is, so the player
 * lands on the server list. {@link #ACTION_LAUNCH_WORLD} plus a
 * {@code blowtorch://world?n=} URI, aimed at {@link WorldLaunchActivity},
 * survive that. Host, port and TLS also live on the URI ({@code h},
 * {@code p}, {@code tls}) because extras-drop would otherwise open a TLS
 * world in the clear.
 */
public final class LauncherShortcutExtras {

	public static final String ACTION_LAUNCH_WORLD =
			"com.resurrection.blowtorch2.LAUNCH_WORLD";
	public static final String DISPLAY = "DISPLAY";
	public static final String HOST = "HOST";
	public static final String PORT = "PORT";
	public static final String TLS = "TLS";
	public static final String LAUNCH_FROM_SHORTCUT = "LAUNCH_FROM_SHORTCUT";
	public static final String URI_SCHEME = "blowtorch";
	public static final String URI_HOST = "world";

	/** Pin target. Not {@code FreeLauncher}: that component is MAIN/LAUNCHER. */
	public static final String WORLD_LAUNCH_ACTIVITY =
			"com.resurrection.blowtorch2.lib.launcher.WorldLaunchActivity";

	private LauncherShortcutExtras() {
	}

	public static Uri worldUri(String displayName) {
		return worldUri(displayName, null, null, null);
	}

	public static Uri worldUri(String displayName, String host, String port,
			boolean tls) {
		return worldUri(displayName, host, port, Boolean.valueOf(tls));
	}

	private static Uri worldUri(String displayName, String host, String port,
			Boolean tls) {
		String name = displayName == null ? "" : displayName;
		Uri.Builder b = new Uri.Builder()
				.scheme(URI_SCHEME)
				.authority(URI_HOST)
				.appendQueryParameter("n", name);
		if (host != null && host.length() > 0) {
			b.appendQueryParameter("h", host);
		}
		if (port != null && port.length() > 0) {
			b.appendQueryParameter("p", port);
		}
		if (tls != null) {
			b.appendQueryParameter("tls", tls.booleanValue() ? "1" : "0");
		}
		return b.build();
	}

	public static String displayName(Intent from) {
		if (from == null) {
			return null;
		}
		if (from.hasExtra(DISPLAY)) {
			String extra = from.getStringExtra(DISPLAY);
			if (extra != null && extra.length() > 0) {
				return extra;
			}
		}
		return uriQuery(from, "n");
	}

	public static String host(Intent from) {
		if (from == null) {
			return null;
		}
		if (from.hasExtra(HOST)) {
			String extra = from.getStringExtra(HOST);
			if (extra != null && extra.length() > 0) {
				return extra;
			}
		}
		return uriQuery(from, "h");
	}

	public static String port(Intent from) {
		if (from == null) {
			return null;
		}
		if (from.hasExtra(PORT)) {
			String extra = from.getStringExtra(PORT);
			if (extra != null && extra.length() > 0) {
				return extra;
			}
		}
		return uriQuery(from, "p");
	}

	/**
	 * True when the Intent actually names TLS, extra or URI. A missing extra
	 * must not become {@code false} — that is how a TLS world goes in the clear.
	 */
	public static boolean hasTls(Intent from) {
		if (from != null && from.hasExtra(TLS)) {
			return true;
		}
		return uriQuery(from, "tls") != null;
	}

	public static boolean tls(Intent from) {
		if (from == null) {
			return false;
		}
		if (from.hasExtra(TLS)) {
			return from.getBooleanExtra(TLS, false);
		}
		return "1".equals(uriQuery(from, "tls"));
	}

	public static Intent pinIntent(String packageName, String display,
			String host, String port, boolean tls) {
		Intent i = new Intent(ACTION_LAUNCH_WORLD);
		i.setClassName(packageName, WORLD_LAUNCH_ACTIVITY);
		i.addCategory(Intent.CATEGORY_DEFAULT);
		i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		i.setData(worldUri(display, host, port, tls));
		i.putExtra(DISPLAY, display);
		if (host != null) {
			i.putExtra(HOST, host);
		}
		if (port != null) {
			i.putExtra(PORT, port);
		}
		i.putExtra(TLS, tls);
		i.putExtra(LAUNCH_FROM_SHORTCUT, true);
		return i;
	}

	public static void copy(Intent from, Intent to) {
		if (from == null || to == null) {
			return;
		}
		String display = displayName(from);
		if (display != null && display.length() > 0) {
			to.putExtra(DISPLAY, display);
		}
		if (from.getData() != null) {
			to.setData(from.getData());
		} else if (display != null && display.length() > 0) {
			if (hasTls(from)) {
				to.setData(worldUri(display, host(from), port(from), tls(from)));
			} else {
				to.setData(worldUri(display, host(from), port(from), null));
			}
		}
		String h = host(from);
		if (h != null && h.length() > 0) {
			to.putExtra(HOST, h);
		}
		String p = port(from);
		if (p != null && p.length() > 0) {
			to.putExtra(PORT, p);
		}
		if (hasTls(from)) {
			to.putExtra(TLS, tls(from));
		}
		if (from.hasExtra(LAUNCH_FROM_SHORTCUT)) {
			to.putExtra(LAUNCH_FROM_SHORTCUT, from.getBooleanExtra(LAUNCH_FROM_SHORTCUT, false));
		}
	}

	private static String uriQuery(Intent from, String key) {
		if (from == null) {
			return null;
		}
		Uri data = from.getData();
		if (data == null || !URI_SCHEME.equals(data.getScheme())
				|| !URI_HOST.equals(data.getHost())) {
			return null;
		}
		return data.getQueryParameter(key);
	}
}
