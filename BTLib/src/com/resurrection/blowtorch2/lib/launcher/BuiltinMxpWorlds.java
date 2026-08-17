package com.resurrection.blowtorch2.lib.launcher;

/**
 * One-shot launcher rows for live MUDs that advertise MXP. Separate seed flag
 * from the colour-test worlds: those already seeded on existing phones, so a
 * new row inside that method would never appear.
 *
 * Hosts and ports come from each world's own docs or MSSP listing, not from a
 * handshake in this client. Deleting a row sticks (the seed flag is already
 * set).
 */
public final class BuiltinMxpWorlds {

	static final String PREF_SEEDED = "mxp_worlds_seeded";
	private static final String PREFS = "SERVICE_INFO";

	public static final String DISCWORLD_NAME = "Discworld MUD (MXP)";
	public static final String DISCWORLD_HOST = "discworld.starturtle.net";
	public static final String DISCWORLD_PORT = "4242";
	public static final String DISCWORLD_DESCRIPTION =
			"Pratchett-themed LPMud. Official connecting page: port 23 or 4242 "
			+ "(this row uses 4242). Their /doc/concepts/mxp describes clickable "
			+ "SEND and menus. Use MXP? on (default). In-game you may need: "
			+ "term mxp   or   options mxp enabled\n"
			+ "Without a live SEND: .probe mxp dumps a sample here.";

	public static final String THRESHOLD_NAME = "Threshold RPG (MXP)";
	public static final String THRESHOLD_HOST = "thresholdrpg.com";
	public static final String THRESHOLD_PORT = "3333";
	public static final String THRESHOLD_DESCRIPTION =
			"Roleplay-enforced LPMud. MSSP listing: MXP=yes, port 3333, "
			+ "MINIMUM AGE 18. Use MXP? on (default).\n"
			+ "Without a live SEND: .probe mxp dumps a sample here.";

	public static final String ANSALON_NAME = "Ansalon (MXP)";
	public static final String ANSALON_HOST = "ansalon.net";
	public static final String ANSALON_PORT = "8679";
	public static final String ANSALON_DESCRIPTION =
			"Dragonlance ROM. Homepage lists MXP Links; MSSP MXP=yes. "
			+ "ansalon.net port 8679. Use MXP? on (default).\n"
			+ "Without a live SEND: .probe mxp dumps a sample here.";

	public static final String MIDNIGHT_SUN_NAME = "Midnight Sun (MXP)";
	public static final String MIDNIGHT_SUN_HOST = "midnightsun2.org";
	public static final String MIDNIGHT_SUN_PORT = "3000";
	public static final String MIDNIGHT_SUN_DESCRIPTION =
			"LPMud. MSSP listing: MXP=yes, midnightsun2.org port 3000. "
			+ "Use MXP? on (default).\n"
			+ "Without a live SEND: .probe mxp dumps a sample here.";

	private BuiltinMxpWorlds() {
	}

	public static MudConnection discworld() {
		return entry(DISCWORLD_NAME, DISCWORLD_HOST, DISCWORLD_PORT,
				DISCWORLD_DESCRIPTION);
	}

	public static MudConnection threshold() {
		return entry(THRESHOLD_NAME, THRESHOLD_HOST, THRESHOLD_PORT,
				THRESHOLD_DESCRIPTION);
	}

	public static MudConnection ansalon() {
		return entry(ANSALON_NAME, ANSALON_HOST, ANSALON_PORT,
				ANSALON_DESCRIPTION);
	}

	public static MudConnection midnightSun() {
		return entry(MIDNIGHT_SUN_NAME, MIDNIGHT_SUN_HOST, MIDNIGHT_SUN_PORT,
				MIDNIGHT_SUN_DESCRIPTION);
	}

	public static MudConnection[] entries() {
		return new MudConnection[] {
				discworld(), threshold(), ansalon(), midnightSun()
		};
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
	 * Add the MXP worlds if this install has never been seeded. Returns true
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
		for (MudConnection entry : entries()) {
			changed |= BuiltinColorTestWorlds.addIfMissing(settings, entry);
		}
		prefs.edit().putBoolean(PREF_SEEDED, true).commit();
		return changed;
	}
}
