package com.resurrection.blowtorch2.lib.launcher;

/**
 * One-shot launcher rows for colour tests. Added the first time the launcher
 * loads after this ships; deleting them sticks (the seed flag is already set).
 */
public final class BuiltinColorTestWorlds {

	static final String PREF_SEEDED = "color_test_worlds_seeded";
	private static final String PREFS = "SERVICE_INFO";

	public static final String ARX_NAME = "Arx (color test)";
	public static final String ARX_HOST = "play.arxgame.org";
	public static final String ARX_PORT = "3000";
	public static final String ARX_DESCRIPTION =
			"Evennia MUSH for colour tests. At the login screen type: guest\n"
			+ "Then type: color\n"
			+ "That command shows ANSI / 256 / truecolor samples. The game sends "
			+ "24-bit sequences only if the client advertises MTTS truecolor "
			+ "(BlowTorch does not yet). To see 24-bit on this phone now, type: "
			+ ".probe truecolor";

	public static final String TEMPEST_NAME = "Tempest Season (256 color)";
	public static final String TEMPEST_HOST = "game.tempestseason.com";
	public static final String TEMPEST_PORT = "6000";
	public static final String TEMPEST_DESCRIPTION =
			"256-colour test world. Login banner is 16-colour ANSI; in-game may "
			+ "use xterm 256. For a 24-bit sample that does not wait on the game, "
			+ "type: .probe truecolor";

	private BuiltinColorTestWorlds() {
	}

	public static MudConnection arx() {
		return entry(ARX_NAME, ARX_HOST, ARX_PORT, ARX_DESCRIPTION);
	}

	public static MudConnection tempest() {
		return entry(TEMPEST_NAME, TEMPEST_HOST, TEMPEST_PORT, TEMPEST_DESCRIPTION);
	}

	private static MudConnection entry(final String name, final String host,
			final String port, final String description) {
		MudConnection m = new MudConnection();
		m.setDisplayName(name);
		m.setHostName(host);
		m.setPortString(port);
		m.setDescription(description);
		m.setLastPlayed("never");
		return m;
	}

	/**
	 * Add the test worlds if this install has never been seeded. Returns true
	 * when the list changed and should be saved.
	 */
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
		boolean changed = false;
		changed |= addIfMissing(settings, arx());
		changed |= addIfMissing(settings, tempest());
		prefs.edit().putBoolean(PREF_SEEDED, true).commit();
		return changed;
	}

	/** Insert {@code entry} unless the same host:port is already listed. */
	static boolean addIfMissing(final LauncherSettings settings,
			final MudConnection entry) {
		if (settings == null || settings.getList() == null || entry == null) {
			return false;
		}
		String host = entry.getHostName();
		String port = entry.getPortString();
		for (MudConnection existing : settings.getList().values()) {
			if (existing == null) {
				continue;
			}
			if (host.equalsIgnoreCase(existing.getHostName())
					&& port.equals(existing.getPortString())) {
				return false;
			}
		}
		String name = entry.getDisplayName();
		if (settings.getList().containsKey(name)) {
			name = name + " " + host;
			entry.setDisplayName(name);
		}
		settings.getList().put(entry.getDisplayName(), entry);
		return true;
	}
}
