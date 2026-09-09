package com.resurrection.blowtorch2.lib.service.function;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.window.TimestampCommandParser;
import com.resurrection.blowtorch2.lib.window.TimestampFormat;

/**
 * Per-line arrival time: {@code .timestamp on|off}, {@code .timestamp log on|off},
 * {@code .timestamp hour|minute|second|month|year}.
 *
 * <p>Painted on the right of the first row of each line. Session log is a
 * separate left-hand prefix. Same preference as Options → Window.
 */
public class TimestampCommand extends SpecialCommand {

	public static final String OPTION_ENABLED = "line_stamps";
	public static final String OPTION_LOG = "line_stamps_log";
	public static final String OPTION_FIELDS = "line_stamps_fields";

	public TimestampCommand() {
		this.commandName = "timestamp";
	}

	public Object execute(Object o, Connection c) {
		TimestampCommandParser.Result r = TimestampCommandParser.parse(
				o == null ? "" : ((String) o));
		if (r.error != null) {
			c.sendDataToWindow(getErrorMessage("Timestamp command usage:", r.error));
			return null;
		}
		boolean on = c.getMainWindowBooleanOption(OPTION_ENABLED, false);
		boolean log = c.getMainWindowBooleanOption(OPTION_LOG, false);
		int fields = TimestampFormat.clamp(
				c.getMainWindowIntegerOption(OPTION_FIELDS, TimestampFormat.DEFAULT));

		if (TimestampCommandParser.ACTION_STATUS.equals(r.action)) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Line timestamps " + (on ? "on" : "off")
					+ ", log " + (log ? "on" : "off")
					+ ", parts " + TimestampFormat.formatParts(fields) + ".\n"
					+ TimestampCommandParser.usage()
					+ "Also: Options → Window → Line timestamps?\n");
			return null;
		}
		if (TimestampCommandParser.ACTION_SHOW.equals(r.action)
				|| TimestampCommandParser.ACTION_HIDE.equals(r.action)
				|| TimestampCommandParser.ACTION_TOGGLE.equals(r.action)) {
			boolean desired = TimestampCommandParser.ACTION_SHOW.equals(r.action);
			if (TimestampCommandParser.ACTION_TOGGLE.equals(r.action)) {
				desired = !on;
			}
			if (desired == on) {
				c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
						+ "Line timestamps already "
						+ (desired ? "on" : "off") + ".\n");
				return null;
			}
			if (!c.updateMainWindowBooleanOption(OPTION_ENABLED, desired)) {
				c.sendDataToWindow(getErrorMessage("Timestamp command error",
						"There is no game window to change yet."));
				return null;
			}
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Line timestamps " + (desired ? "on" : "off") + ".\n");
			return null;
		}
		if (TimestampCommandParser.ACTION_LOG.equals(r.action)) {
			boolean desired = r.logOn != null ? r.logOn.booleanValue() : !log;
			if (desired == log) {
				c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
						+ "Session-log timestamps already "
						+ (desired ? "on" : "off") + ".\n");
				return null;
			}
			if (!c.updateMainWindowBooleanOption(OPTION_LOG, desired)) {
				c.sendDataToWindow(getErrorMessage("Timestamp command error",
						"There is no game window to change yet."));
				return null;
			}
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Session-log timestamps "
					+ (desired ? "on" : "off") + ".\n");
			return null;
		}
		if (TimestampCommandParser.ACTION_PART.equals(r.action)) {
			int next;
			if (r.partOn == null) {
				next = TimestampFormat.toggle(fields, r.partBit);
			} else {
				next = TimestampFormat.withPart(fields, r.partBit, r.partOn.booleanValue());
			}
			if (next == fields) {
				c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
						+ "Timestamp parts already "
						+ TimestampFormat.formatParts(fields)
						+ " (need at least one part).\n");
				return null;
			}
			if (!c.updateMainWindowIntegerOption(OPTION_FIELDS, next)) {
				c.sendDataToWindow(getErrorMessage("Timestamp command error",
						"There is no game window to change yet."));
				return null;
			}
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Timestamp parts "
					+ TimestampFormat.formatParts(next) + ".\n");
			return null;
		}
		c.sendDataToWindow(getErrorMessage("Timestamp command usage:",
				TimestampCommandParser.usage()));
		return null;
	}
}
