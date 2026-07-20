package com.resurrection.blowtorch2.lib.launcher;

import android.text.format.Time;

/**
 * Built-in offline Starter Tutorial launcher entry. Always offered as the first
 * row so new players can open the guide without connecting to a MUD.
 */
public final class BuiltinTutorial {

	public static final String DISPLAY_NAME = "Starter Tutorial";
	/** Special host: Connection skips TCP and never auto-reconnects. */
	public static final String HOST = "offline";
	public static final String PORT = "0";
	public static final String DESCRIPTION =
			"Interactive BlowTorch guide — buttons, gestures, aliases, triggers, "
			+ ".commands, and more. Opens offline (no MUD). Tap to begin. "
			+ "Hide from the launcher ⋮ menu (Show/Hide Starter Tutorial).";

	/** SharedPreferences flag: when true, the built-in row is kept but not listed. */
	public static final String PREF_HIDDEN = "starter_tutorial_hidden";
	private static final String PREFS = "SERVICE_INFO";

	private BuiltinTutorial() {
	}

	public static boolean isHidden(android.content.Context context) {
		if (context == null) {
			return false;
		}
		return context.getSharedPreferences(PREFS, 0).getBoolean(PREF_HIDDEN, false);
	}

	public static void setHidden(android.content.Context context, boolean hidden) {
		if (context == null) {
			return;
		}
		context.getSharedPreferences(PREFS, 0).edit().putBoolean(PREF_HIDDEN, hidden).commit();
	}

	public static boolean isTutorialHost(String host) {
		return host != null && host.trim().equalsIgnoreCase(HOST);
	}

	public static boolean isTutorialEntry(MudConnection m) {
		if (m == null) {
			return false;
		}
		if (isTutorialHost(m.getHostName())) {
			return true;
		}
		return DISPLAY_NAME.equals(m.getDisplayName());
	}

	/** Create or refresh the built-in row (description / offline flags). */
	public static MudConnection buildEntry() {
		MudConnection m = new MudConnection();
		m.setDisplayName(DISPLAY_NAME);
		m.setHostName(HOST);
		m.setPortString(PORT);
		m.setDescription(DESCRIPTION);
		m.setOffline(true);
		Time t = new Time();
		t.setToNow();
		// Far-future-ish relative to never: keep Tutorial above "never" and near the top
		// until the user plays real MUDs; comparator also pins offline first.
		m.setLastPlayed(t.format2445());
		return m;
	}

	/**
	 * Ensure the Tutorial row exists and looks correct. Returns true if the list changed.
	 */
	public static boolean ensureIn(LauncherSettings settings) {
		if (settings == null || settings.getList() == null) {
			return false;
		}
		MudConnection existing = settings.getList().get(DISPLAY_NAME);
		if (existing == null) {
			// Recover older/misnamed offline rows
			for (MudConnection m : settings.getList().values()) {
				if (isTutorialHost(m.getHostName())) {
					existing = m;
					break;
				}
			}
		}
		if (existing == null) {
			settings.getList().put(DISPLAY_NAME, buildEntry());
			return true;
		}
		boolean changed = false;
		if (!DISPLAY_NAME.equals(existing.getDisplayName())) {
			settings.getList().remove(existing.getDisplayName());
			existing.setDisplayName(DISPLAY_NAME);
			settings.getList().put(DISPLAY_NAME, existing);
			changed = true;
		}
		if (!HOST.equals(existing.getHostName())) {
			existing.setHostName(HOST);
			changed = true;
		}
		if (!PORT.equals(existing.getPortString())) {
			existing.setPortString(PORT);
			changed = true;
		}
		if (!existing.isOffline()) {
			existing.setOffline(true);
			changed = true;
		}
		String desc = existing.getDescription();
		if (desc == null || desc.length() == 0) {
			existing.setDescription(DESCRIPTION);
			changed = true;
		}
		return changed;
	}
}
