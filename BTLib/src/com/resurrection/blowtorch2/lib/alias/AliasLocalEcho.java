package com.resurrection.blowtorch2.lib.alias;

/**
 * Per-alias override of the connection {@code local_echo} setting.
 *
 * <p>{@link #INHERIT} keeps today's behaviour. {@link #FORCE_ON} / {@link #FORCE_OFF}
 * override the global preference for text produced by that alias. Telnet ECHO
 * password masking ({@code telnetLocalEcho == false}) always wins — FORCE_ON must
 * never put a password in the scrollback.
 */
public enum AliasLocalEcho {
	/** Follow the connection Local Echo? option. */
	INHERIT,
	/** Show this alias's output even when global local echo is off. */
	FORCE_ON,
	/** Hide this alias's output even when global local echo is on. */
	FORCE_OFF;

	/**
	 * Whether a segment should be appended to the local-echo window text.
	 *
	 * @param globalLocalEcho connection {@code local_echo} setting
	 * @param telnetLocalEcho false while the server holds telnet ECHO (password)
	 * @param policy alias override; null treated as {@link #INHERIT}
	 * @return true when the segment may be shown
	 */
	public static boolean shouldDisplay(final boolean globalLocalEcho,
			final boolean telnetLocalEcho, final AliasLocalEcho policy) {
		if (!telnetLocalEcho) {
			return false;
		}
		AliasLocalEcho p = policy != null ? policy : INHERIT;
		switch (p) {
		case FORCE_ON:
			return true;
		case FORCE_OFF:
			return false;
		case INHERIT:
		default:
			return globalLocalEcho;
		}
	}

	/**
	 * Parse an XML / UI token. Missing or blank → {@link #INHERIT}.
	 * Accepts {@code on}/{@code off} and {@code true}/{@code false}.
	 */
	public static AliasLocalEcho fromAttribute(final String raw) {
		if (raw == null) {
			return INHERIT;
		}
		String v = raw.trim();
		if (v.length() == 0 || "inherit".equalsIgnoreCase(v)
				|| "default".equalsIgnoreCase(v)) {
			return INHERIT;
		}
		if ("on".equalsIgnoreCase(v) || "true".equalsIgnoreCase(v)
				|| "show".equalsIgnoreCase(v) || "always".equalsIgnoreCase(v)) {
			return FORCE_ON;
		}
		if ("off".equalsIgnoreCase(v) || "false".equalsIgnoreCase(v)
				|| "hide".equalsIgnoreCase(v) || "never".equalsIgnoreCase(v)) {
			return FORCE_OFF;
		}
		return INHERIT;
	}

	/**
	 * XML attribute value, or null when {@link #INHERIT} (omit from output).
	 */
	public String toAttribute() {
		switch (this) {
		case FORCE_ON:
			return "on";
		case FORCE_OFF:
			return "off";
		case INHERIT:
		default:
			return null;
		}
	}

	/** Stable token for Lua TSV / {@code .alias status}. */
	public String toInspectToken() {
		switch (this) {
		case FORCE_ON:
			return "on";
		case FORCE_OFF:
			return "off";
		case INHERIT:
		default:
			return "inherit";
		}
	}

	public static AliasLocalEcho fromOrdinalSafe(final int ordinal) {
		AliasLocalEcho[] all = values();
		if (ordinal < 0 || ordinal >= all.length) {
			return INHERIT;
		}
		return all[ordinal];
	}
}
