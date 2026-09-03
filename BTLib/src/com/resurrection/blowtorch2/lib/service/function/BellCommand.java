package com.resurrection.blowtorch2.lib.service.function;

import java.util.Locale;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BaseOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BooleanOption;

/**
 * {@code .dobell} — fire Options → Bell now. Script {@code .dobell} is the
 * trigger path with no dedicated sound action. {@code vibrate}/{@code alert}
 * fire even if that option is off. All three off used to be silent with no
 * message; that is indistinguishable from a broken trigger.
 */
public class BellCommand extends SpecialCommand {

	/** Options → Bell, in the order a player meets them. */
	private static final String[] BELL_KEYS = {
		"bell_vibrate", "bell_notification", "bell_display" };

	/** {@code MESSAGE_BELLINC} {@code obj}: show the on-screen bell, ignoring Options. */
	public static final String FORCE_ALERT = "alert";
	/** {@code MESSAGE_BELLINC} {@code obj} prefix: buzz now, ignoring Options. */
	public static final String FORCE_VIBRATE_PREFIX = "vibrate:";

	/** Same length the old {@code vibrator.vibrate(long)} path used. */
	public static final int SHORT_MS = 300;
	public static final int LONG_MS = 800;
	/** Strong is a short pulse at max amplitude, not a longer one. */
	public static final int STRONG_MS = 300;
	/** One pulse in a burst. Short enough that three of them read as taps. */
	public static final int BURST_PULSE_MS = 80;
	/** Quiet gap between burst pulses. */
	public static final int BURST_GAP_MS = 90;
	public static final int BURST_COUNT = 3;
	/** {@code VibrationEffect.DEFAULT_AMPLITUDE} — kept as a literal so JVM tests do not load Android. */
	public static final int DEFAULT_AMPLITUDE = -1;
	public static final int STRONG_AMPLITUDE = 255;

	public BellCommand() {
		this.commandName = "dobell";
	}

	public Object execute(Object o, Connection c) {
		String arg = o == null ? "" : ((String) o).trim().toLowerCase(Locale.US);
		String[] parsed = parseDobellArgs(arg);
		if (parsed == null) {
			c.sendDataToWindow("\n" + Colorizer.getBrightCyanColor()
					+ usage()
					+ Colorizer.getWhiteColor());
			return null;
		}
		if (parsed.length == 0) {
			if (!anyBellReactionOn(c)) {
				c.sendDataToWindow("\n" + Colorizer.getBrightCyanColor()
						+ "The bell rang, but nothing is set to answer it."
						+ Colorizer.getWhiteColor()
						+ "\nTurn on Vibrate, Generate Notification or Display Bell in"
						+ " Options → Bell.\nOnly Vibrate is on by default, and a"
						+ " phone in silent mode, Do Not Disturb, or some OS"
						+ " profiles will not buzz.\n");
				return null;
			}
			c.getHandler().sendEmptyMessage(Connection.MESSAGE_BELLINC);
			return null;
		}
		c.getHandler().sendMessage(c.getHandler().obtainMessage(
				Connection.MESSAGE_BELLINC, forceSpec(parsed)));
		return null;
	}

	/**
	 * Parse {@code .dobell} arguments after {@code trim} / {@code toLowerCase}.
	 *
	 * @param raw the command argument, already normalised, or null
	 * @return an empty array for no-args (honor Options); {@code {"vibrate",
	 *         pattern}} or {@code {"alert"}} for a force command; null when
	 *         the argument is not recognised
	 */
	static String[] parseDobellArgs(final String raw) {
		String arg = raw == null ? "" : raw.trim().toLowerCase(Locale.US);
		if (arg.length() == 0) {
			return new String[0];
		}
		String[] parts = arg.split("\\s+");
		if (parts[0].equals("alert") && parts.length == 1) {
			return new String[] { "alert" };
		}
		if (parts[0].equals("vibrate")) {
			if (parts.length == 1) {
				return new String[] { "vibrate", "short" };
			}
			if (parts.length == 2 && isVibratePattern(parts[1])) {
				return new String[] { "vibrate", parts[1] };
			}
		}
		return null;
	}

	static String forceSpec(final String[] parsed) {
		if (parsed == null || parsed.length == 0) {
			return null;
		}
		if (parsed[0].equals("alert")) {
			return FORCE_ALERT;
		}
		return FORCE_VIBRATE_PREFIX + parsed[1];
	}

	static boolean isVibratePattern(final String token) {
		return "short".equals(token) || "long".equals(token)
				|| "strong".equals(token) || "burst".equals(token);
	}

	static String usage() {
		return "Usage:\n"
				+ "  .dobell            — reactions currently on in Options → Bell\n"
				+ "  .dobell vibrate [short|long|strong|burst]\n"
				+ "  .dobell alert      — on-screen bell icon now\n";
	}

	public static int vibrateDurationMs(final String pattern) {
		if ("long".equals(pattern)) {
			return LONG_MS;
		}
		if ("strong".equals(pattern)) {
			return STRONG_MS;
		}
		if ("burst".equals(pattern)) {
			return BURST_PULSE_MS;
		}
		return SHORT_MS;
	}

	public static boolean isBurst(final String pattern) {
		return "burst".equals(pattern);
	}

	public static int vibrateAmplitude(final String pattern) {
		// DEFAULT_AMPLITUDE (-1) is a no-op on some phones. Always max.
		return STRONG_AMPLITUDE;
	}

	/**
	 * Pattern name from a force-vibrate {@code MESSAGE_BELLINC} obj, or null.
	 *
	 * @param spec {@code msg.obj}
	 * @return {@code short}, {@code long}, {@code strong}, {@code burst}, or null
	 */
	public static String forceVibratePattern(final Object spec) {
		if (!(spec instanceof String)) {
			return null;
		}
		String s = (String) spec;
		if (!s.startsWith(FORCE_VIBRATE_PREFIX)) {
			return null;
		}
		return s.substring(FORCE_VIBRATE_PREFIX.length());
	}

	/**
	 * @param c the connection whose profile to read.
	 * @return true when at least one bell reaction would do something. Unknown
	 *         is treated as on: an older profile that predates one of these keys
	 *         should not be told its bell is dead when it is not.
	 */
	private static boolean anyBellReactionOn(final Connection c) {
		if (c == null || c.getSettings() == null) {
			return true;
		}
		for (int i = 0; i < BELL_KEYS.length; i++) {
			Object found = c.getSettings().findOptionByKey(BELL_KEYS[i]);
			if (!(found instanceof BooleanOption)) {
				return true;
			}
			Object value = ((BaseOption) found).getValue();
			if (value instanceof Boolean && ((Boolean) value).booleanValue()) {
				return true;
			}
		}
		return false;
	}
}
