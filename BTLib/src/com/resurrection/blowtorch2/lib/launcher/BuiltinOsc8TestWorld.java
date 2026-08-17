package com.resurrection.blowtorch2.lib.launcher;

/**
 * One-shot launcher row for the OSC 8 test listener. Separate seed flag from
 * the colour-test worlds: those already seeded on existing phones, so a new
 * row inside that method would never appear.
 */
public final class BuiltinOsc8TestWorld {

	static final String PREF_SEEDED = "osc8_test_world_seeded";
	private static final String PREFS = "SERVICE_INFO";

	public static final String NAME = "OSC 8 links (local test)";
	public static final String HOST = "127.0.0.1";
	public static final String PORT = "4445";
	public static final String DESCRIPTION =
			"Local OSC 8 hyperlink test. On the laptop:\n"
			+ "  python3 .scratch/osc8server.py\n"
			+ "  adb reverse tcp:4445 tcp:4445\n"
			+ "Then connect this world. Tap the marked words.\n"
			+ "Without the listener: .probe osc8 dumps a sample here.";

	private BuiltinOsc8TestWorld() {
	}

	public static MudConnection entry() {
		MudConnection m = new MudConnection();
		m.setDisplayName(NAME);
		m.setHostName(HOST);
		m.setPortString(PORT);
		m.setDescription(DESCRIPTION);
		m.setLastPlayed("never");
		return m;
	}

	public static boolean seedIfNeeded(final android.content.Context context,
			final LauncherSettings settings) {
		if (context == null || settings == null || settings.getList() == null) {
			return false;
		}
		android.content.SharedPreferences prefs =
				context.getSharedPreferences(PREFS, 0);
		if (prefs.getBoolean(PREF_SEEDED, false)) {
			return false;
		}
		boolean changed = BuiltinColorTestWorlds.addIfMissing(settings, entry());
		prefs.edit().putBoolean(PREF_SEEDED, true).commit();
		return changed;
	}
}
