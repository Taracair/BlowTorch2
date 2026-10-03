package com.resurrection.blowtorch2.lib.service.function;

import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.resurrection.blowtorch2.lib.service.Connection;


/** Utility class providing the .timer command. */
public class TimerCommand extends SpecialCommand {
	/** Acceptable timer action strings. */
	private ArrayList<String> mTimerActions = new ArrayList<String>();
	/** Silent marker. */
	private final int mSilent = 50;

	/**
	 * {@code .timer duration <name> <value> [silent]} where value is a bare
	 * positive seconds count, or a relative {@code 50s}/{@code 2m}/{@code 1h}
	 * (optional leading {@code -}).
	 */
	static final Pattern DURATION_PATTERN = Pattern.compile(
			"^\\s*duration\\s+(\\S+)\\s+(-?\\d+[smh]?)\\s*(\\S*)",
			Pattern.CASE_INSENSITIVE);

	/** {@code .timer duration <name> [window]} — query, not set. */
	static final Pattern DURATION_QUERY_PATTERN = Pattern.compile(
			"^\\s*duration\\s+(\\S+)\\s*(\\S*)\\s*$",
			Pattern.CASE_INSENSITIVE);

	/** {@code .timer info|dump|list} with no name — all timers to the window. */
	static final Pattern BARE_DUMP_PATTERN = Pattern.compile(
			"^\\s*(info|dump|list)\\s*$",
			Pattern.CASE_INSENSITIVE);

	static final Pattern ACTION_PATTERN = Pattern.compile("^\\s*(\\S+)\\s+(\\S+)\\s*(\\S*)");

	/**
	 * Parsed {@code .timer duration} value: bare seconds sets stored length;
	 * unit suffix adjusts remaining only.
	 */
	public static final class DurationValue {
		/** True when the token had an {@code s}/{@code m}/{@code h} unit. */
		public final boolean relative;
		/** Absolute seconds, or signed remaining delta when {@link #relative}. */
		public final int seconds;

		DurationValue(final boolean relative, final int seconds) {
			this.relative = relative;
			this.seconds = seconds;
		}
	}

	/** Carried on {@link Connection#MESSAGE_TIMERDURATION} as {@code msg.obj}. */
	public static final class DurationChange {
		public final String name;
		public final boolean relative;
		public final int seconds;
		public final boolean silent;

		public DurationChange(final String name, final boolean relative,
				final int seconds, final boolean silent) {
			this.name = name;
			this.relative = relative;
			this.seconds = seconds;
			this.silent = silent;
		}
	}

	/** Generic constructor. */
	public TimerCommand() {
		this.commandName = "timer";
		mTimerActions.add("play");
		mTimerActions.add("pause");
		mTimerActions.add("info");
		mTimerActions.add("reset");
		mTimerActions.add("stop");
		mTimerActions.add("duration");
		mTimerActions.add("dump");
		mTimerActions.add("list");
	}

	/** Execute method for this command.
	 *
	 * @param o parameter object.
	 * @param c connection that called this function
	 * @return whatever this function returns.
	 */
	public Object execute(final Object o, final Connection c)  {
		String line = o == null ? "" : (String) o;
		String trimmed = line.trim();
		if (trimmed.length() == 0 || trimmed.equalsIgnoreCase("duration")) {
			c.dispatchNoProcess(getErrorMessage("Timer command needs more than that.",
					usage()).getBytes());
			return null;
		}

		Matcher duration = DURATION_PATTERN.matcher(line);
		if (duration.matches()) {
			String name = duration.group(1);
			DurationValue parsed = parseDurationValue(duration.group(2));
			if (parsed == null) {
				c.dispatchNoProcess(getErrorMessage(
						"Timer duration needs a whole number of seconds, or a relative "
							+ "amount like 50s, 2m, 1h (optional leading -).",
						"Example: .timer duration heal 15\n"
							+ "Or .timer duration heal 50s to add to remaining.").getBytes());
				return null;
			}
			if (!parsed.relative && parsed.seconds <= 0) {
				c.dispatchNoProcess(getErrorMessage("Timer duration must be more than zero.",
						"Example: .timer duration heal 15").getBytes());
				return null;
			}
			boolean silent = false;
			String tail = duration.group(3);
			if (tail != null && tail.length() > 0) {
				silent = true;
			}
			c.getHandler().sendMessage(c.getHandler().obtainMessage(
					Connection.MESSAGE_TIMERDURATION, 0, 0,
					new DurationChange(name, parsed.relative, parsed.seconds, silent)));
			return null;
		}

		Matcher durationQuery = DURATION_QUERY_PATTERN.matcher(line);
		if (durationQuery.matches()) {
			String tail = durationQuery.group(2);
			if (tail != null && tail.length() > 0 && !isWindowToken(tail)) {
				c.dispatchNoProcess(getErrorMessage(
						"Timer duration needs a whole number of seconds, or a relative "
							+ "amount like 50s, 2m, 1h (optional leading -), not \""
							+ tail + "\".",
						"Example: .timer duration heal 15\n"
							+ "Or .timer duration heal to see status.").getBytes());
				return null;
			}
			String name = durationQuery.group(1);
			// Same path as dump: status goes to the game window (toast was useless).
			c.getHandler().sendMessage(c.getHandler().obtainMessage(
					Connection.MESSAGE_TIMERINFO, 1, 0, name));
			return null;
		}

		Matcher bare = BARE_DUMP_PATTERN.matcher(line);
		if (bare.matches()) {
			c.getHandler().sendMessage(c.getHandler().obtainMessage(
					Connection.MESSAGE_TIMERINFO, 1, 0, ""));
			return null;
		}

		Matcher m = ACTION_PATTERN.matcher(line);

		if (m.matches()) {
			String action = m.group(1).toLowerCase(Locale.US);
			String ordinal = m.group(2);
			String tail = "";
			if (m.groupCount() > 2 && m.group(3) != null) {
				tail = m.group(3);
			}
			if (!mTimerActions.contains(action)) {
				c.dispatchNoProcess(getErrorMessage("Timer action argument " + action + " is invalid.",
						usage()).getBytes());
				return null;
			}
			int domsg = mSilent;
			if (tail.length() > 0 && !isWindowToken(tail)) {
				domsg = 0;
			}

			if (action.equals("info") || action.equals("dump") || action.equals("list")
					|| action.equals("duration")) {
				// info aliases dump: both write to the window (toast was useless).
				c.getHandler().sendMessage(c.getHandler().obtainMessage(
						Connection.MESSAGE_TIMERINFO, 1, 0, ordinal));
				return null;
			}
			if (action.equals("reset")) {
				c.getHandler().sendMessage(c.getHandler().obtainMessage(Connection.MESSAGE_TIMERRESET, 0, domsg, ordinal));
				return null;
			}
			if (action.equals("play")) {
				c.getHandler().sendMessage(c.getHandler().obtainMessage(Connection.MESSAGE_TIMERSTART, 0, domsg, ordinal));
				return null;
			}
			if (action.equals("pause")) {
				c.getHandler().sendMessage(c.getHandler().obtainMessage(Connection.MESSAGE_TIMERPAUSE, 0, domsg, ordinal));
				return null;
			}
			if (action.equals("stop")) {
				c.getHandler().sendMessage(c.getHandler().obtainMessage(Connection.MESSAGE_TIMERSTOP, 0, domsg, ordinal));
				return null;
			}
		} else {
			c.dispatchNoProcess(getErrorMessage("Timer command: \".timer " + line + "\" is invalid.",
					usage()).getBytes());
		}

		return null;

	}

	/**
	 * Parse a duration set/adjust token.
	 *
	 * @return null when the token is not a bare positive integer and not a
	 *     relative {@code [-]N[s|m|h]} form.
	 */
	public static DurationValue parseDurationValue(final String token) {
		if (token == null) {
			return null;
		}
		String t = token.trim();
		if (t.length() == 0) {
			return null;
		}
		char last = t.charAt(t.length() - 1);
		boolean hasUnit = last == 's' || last == 'S' || last == 'm' || last == 'M'
				|| last == 'h' || last == 'H';
		if (!hasUnit) {
			// Old form: bare positive seconds sets stored duration and restarts.
			if (t.charAt(0) == '-') {
				return null;
			}
			try {
				int seconds = Integer.parseInt(t);
				if (seconds <= 0) {
					return null;
				}
				return new DurationValue(false, seconds);
			} catch (NumberFormatException e) {
				return null;
			}
		}
		String number = t.substring(0, t.length() - 1);
		if (number.length() == 0 || "-".equals(number)) {
			return null;
		}
		int amount;
		try {
			amount = Integer.parseInt(number);
		} catch (NumberFormatException e) {
			return null;
		}
		if (amount == 0) {
			return null;
		}
		char unit = Character.toLowerCase(last);
		int magnitude;
		if (unit == 'h') {
			magnitude = Math.abs(amount) * 3600;
		} else if (unit == 'm') {
			magnitude = Math.abs(amount) * 60;
		} else {
			magnitude = Math.abs(amount);
		}
		int delta = amount < 0 ? -magnitude : magnitude;
		return new DurationValue(true, delta);
	}

	static boolean isWindowToken(final String token) {
		return token != null && token.equalsIgnoreCase("window");
	}

	private static String usage() {
		return "Timer commands:\n"
				+ "  .timer play|pause|reset|stop <name> [silent]\n"
				+ "  .timer info <name>          status in the game window\n"
				+ "  .timer dump <name>          same as info\n"
				+ "  .timer dump / .timer list / .timer info   every timer, in the window\n"
				+ "  .timer duration <name>      same as info\n"
				+ "  .timer duration <name> <seconds> [silent]   set length and restart from full\n"
				+ "  .timer duration <name> 50s|2m|1h [silent]   add to remaining (keep running)\n"
				+ "  .timer duration <name> -50s|-2m [silent]    subtract from remaining (floor 0)";
	}
}
