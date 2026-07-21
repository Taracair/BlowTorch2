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
		//example argument " info 0"
		//regex = "^\s+(\S+)\s+(\d+)";
		Pattern p = Pattern.compile("^\\s*(\\S+)\\s+(\\S+)\\s*(\\S*)");
		
		Matcher m = p.matcher((String) o);
		
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
				c.dispatchNoProcess(getErrorMessage("Timer action arguemnt " + action + " is invalid.", "Acceptable arguments are \"play\",\"pause\",\"reset\",\"stop\" and \"info\".").getBytes());
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
			c.dispatchNoProcess(getErrorMessage("Timer command: \".timer " + (String) o + "\" is invalid.", "Timer function format \".timer action index [silent]\"\n"
						+ "Where action is \"play\",\"pause\",\"reset\" or \"info\".\nIndex is the timer index displayed in the timer selection list.").getBytes());
		}
		
		return null;
		
	}
}
