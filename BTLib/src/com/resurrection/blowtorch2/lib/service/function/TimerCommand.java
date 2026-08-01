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
	/** Ordinal capture group. */
	private final int mOrdinalGroupIndex = 3;
	/** Silent marker. */
	private final int mSilent = 50;

	/** {@code .timer duration <name> <seconds> [silent]} */
	static final Pattern DURATION_PATTERN = Pattern.compile(
			"^\\s*duration\\s+(\\S+)\\s+(\\d+)\\s*(\\S*)",
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
	}
	/** Execute method for this command.
	 * 
	 * @param o parameter object.
	 * @param c connection that called this function
	 * @return whatever this function returns.
	 */
	public Object execute(final Object o, final Connection c)  {
		String line = (String) o;
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

		Matcher m = ACTION_PATTERN.matcher(line);
		
		if (m.matches()) {
			//extract arguments
			String action = m.group(1).toLowerCase(Locale.US);
			String ordinal = m.group(2);
			String silent = "";
			if (m.groupCount() > 2) {
				silent = m.group(mOrdinalGroupIndex);
			}
			if (!mTimerActions.contains(action)) {
				//error with bad action.
				c.dispatchNoProcess(getErrorMessage("Timer action arguemnt " + action + " is invalid.",
						"Acceptable arguments are \"play\", \"pause\", \"reset\", \"stop\", \"info\", and \"duration\".").getBytes());
				return null;
			}
			int domsg = mSilent;
			if (!silent.equals("")) {
				domsg = 0;
			}
			
			if (action.equals("info")) {
				c.getHandler().sendMessage(c.getHandler().obtainMessage(Connection.MESSAGE_TIMERINFO, ordinal));
				return null;
			}
			if (action.equals("reset")) {
				c.getHandler().sendMessage(c.getHandler().obtainMessage(Connection.MESSAGE_TIMERRESET, 0, domsg, ordinal));
				return null;
			}
			if (action.equals("play")) {
				//play
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
					"Timer function format \".timer action name [silent]\"\n"
						+ "Where action is \"play\", \"pause\", \"reset\", \"stop\", or \"info\".\n"
						+ "Or \".timer duration name seconds [silent]\" to change how long a timer runs.").getBytes());
		}
		
		return null;
		
	}
}
