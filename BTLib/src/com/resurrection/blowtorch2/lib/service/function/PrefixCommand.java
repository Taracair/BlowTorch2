package com.resurrection.blowtorch2.lib.service.function;

import java.util.Locale;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BaseOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BooleanOption;

/**
 * {@code .prefix} — Up and Down stay on commands that start with the bar.
 * Off, they step through every command. Off is the start.
 */
public class PrefixCommand extends SpecialCommand {

	public static final String OPTION_KEY = "shortcut_prefix";

	static final int STATUS = 0;
	static final int ON = 1;
	static final int OFF = 2;
	static final int TOGGLE = 3;

	public PrefixCommand() {
		this.commandName = "prefix";
	}

	public Object execute(Object o, Connection c) {
		if (c == null) {
			return null;
		}
		Integer mode = parse(o == null ? "" : String.valueOf(o));
		BooleanOption opt = find(c);
		if (opt == null || !(opt.getValue() instanceof Boolean)) {
			c.sendDataToWindow(getErrorMessage("Prefix command error",
					"Up finds a command that starts with what you typed is not available yet."));
			return null;
		}
		if (mode == null) {
			c.sendDataToWindow(getErrorMessage("Prefix command usage:", usage()));
			return null;
		}
		boolean current = ((Boolean) opt.getValue()).booleanValue();
		if (mode.intValue() == STATUS) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor() + status(current));
			return null;
		}
		boolean want = apply(current, mode.intValue()).booleanValue();
		if (want == current) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ ".prefix is already " + (want ? "on" : "off") + ".\n");
			return null;
		}
		c.updateBooleanSetting(OPTION_KEY, want);
		c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
				+ ".prefix is " + (want ? "on" : "off") + ".\n");
		return null;
	}

	/**
	 * Blank asks for status. One word: {@code on}, {@code off}, or {@code toggle}.
	 * A second word is not a mode.
	 */
	static Integer parse(String arg) {
		if (arg == null) {
			return null;
		}
		String token = arg.trim().toLowerCase(Locale.US);
		if (token.length() == 0) {
			return Integer.valueOf(STATUS);
		}
		for (int i = 0; i < token.length(); i++) {
			if (Character.isWhitespace(token.charAt(i))) {
				return null;
			}
		}
		if (token.equals("on")) {
			return Integer.valueOf(ON);
		}
		if (token.equals("off")) {
			return Integer.valueOf(OFF);
		}
		if (token.equals("toggle")) {
			return Integer.valueOf(TOGGLE);
		}
		return null;
	}

	/** Status leaves the switch alone. */
	static Boolean apply(boolean current, int mode) {
		if (mode == ON) {
			return Boolean.TRUE;
		}
		if (mode == OFF) {
			return Boolean.FALSE;
		}
		if (mode == TOGGLE) {
			return Boolean.valueOf(!current);
		}
		return null;
	}

	private static String status(boolean on) {
		return ".prefix is " + (on ? "on" : "off") + ".\n"
				+ "On: type ki and Up finds kill goblin.\n"
				+ "Off: Up and Down step through every command, even when the bar says ki.\n"
				+ "The up and down on the Edit strip do the same.\n"
				+ "Usage: .prefix on | .prefix off | .prefix toggle\n"
				+ "Also: Options → Typing → Up finds a command that starts with what you typed\n";
	}

	static String usage() {
		return ".prefix\n"
				+ ".prefix on | off | toggle\n"
				+ "On: type ki and Up finds kill goblin.\n"
				+ "Off: Up and Down step through every command.\n"
				+ "Also: Options → Typing → Up finds a command that starts with what you typed";
	}

	private static BooleanOption find(Connection c) {
		if (c.getSettings() == null) {
			return null;
		}
		BaseOption o = (BaseOption) c.getSettings().findOptionByKey(OPTION_KEY);
		if (o instanceof BooleanOption) {
			return (BooleanOption) o;
		}
		return null;
	}
}
