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

	/** {@code .timer duration <name> <seconds> [silent]} */
	static final Pattern DURATION_PATTERN = Pattern.compile(
			"^\\s*duration\\s+(\\S+)\\s+(\\d+)\\s*(\\S*)",
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
			int seconds;
			try {
				seconds = Integer.parseInt(duration.group(2));
			} catch (NumberFormatException e) {
				c.dispatchNoProcess(getErrorMessage("Timer duration must be a positive number of seconds.",
						"Example: .timer duration heal 15").getBytes());
				return null;
			}
			if (seconds <= 0) {
				c.dispatchNoProcess(getErrorMessage("Timer duration must be more than zero.",
						"Example: .timer duration heal 15").getBytes());
				return null;
			}
			int domsg = mSilent;
			String tail = duration.group(3);
			if (tail != null && tail.length() > 0) {
				domsg = 0;
			}
			c.getHandler().sendMessage(c.getHandler().obtainMessage(
					Connection.MESSAGE_TIMERDURATION, seconds, domsg, name));
			return null;
		}

		Matcher durationQuery = DURATION_QUERY_PATTERN.matcher(line);
		if (durationQuery.matches()) {
			String tail = durationQuery.group(2);
			if (tail != null && tail.length() > 0 && !isWindowToken(tail)) {
				c.dispatchNoProcess(getErrorMessage(
						"Timer duration needs a whole number of seconds, not \"" + tail + "\".",
						"Example: .timer duration heal 15\n"
							+ "Or .timer duration heal to see status.").getBytes());
				return null;
			}
			String name = durationQuery.group(1);
			int toWindow = isWindowToken(tail) ? 1 : 0;
			c.getHandler().sendMessage(c.getHandler().obtainMessage(
					Connection.MESSAGE_TIMERINFO, toWindow, 0, name));
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
				int toWindow = action.equals("dump") || action.equals("list")
						|| isWindowToken(tail) ? 1 : 0;
				c.getHandler().sendMessage(c.getHandler().obtainMessage(
						Connection.MESSAGE_TIMERINFO, toWindow, 0, ordinal));
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

	static boolean isWindowToken(final String token) {
		return token != null && token.equalsIgnoreCase("window");
	}

	private static String usage() {
		return "Timer commands:\n"
				+ "  .timer play|pause|reset|stop <name> [silent]\n"
				+ "  .timer info <name>          status as a toast (state, set for, elapsed, remaining, repeat)\n"
				+ "  .timer info <name> window   same text in the game window\n"
				+ "  .timer dump <name>          same as info … window\n"
				+ "  .timer dump / .timer list / .timer info   every timer, in the window\n"
				+ "  .timer duration <name>      same as info (how long it is set for, and the rest)\n"
				+ "  .timer duration <name> <seconds> [silent]   change how long it runs";
	}
}
