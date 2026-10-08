package com.resurrection.blowtorch2.lib.service.function;

import java.util.Locale;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BaseOption;

/**
 * {@code .togglefullscreen} flips on every call, including {@code off}.
 * {@code .fullscreen} prints status, and {@code on}, {@code off}, and
 * {@code toggle} set the same preference.
 */
public class FullScreenCommand extends SpecialCommand {

	static final int STATUS = 0;
	static final int ON = 1;
	static final int OFF = 2;
	static final int TOGGLE = 3;
	static final int BAD = -1;

	private final boolean alwaysFlip;

	public FullScreenCommand() {
		this(true);
	}

	public FullScreenCommand(boolean alwaysFlip) {
		this.alwaysFlip = alwaysFlip;
		this.commandName = alwaysFlip ? "togglefullscreen" : "fullscreen";
	}

	boolean flipsOnEveryCall() {
		return alwaysFlip;
	}

	public Object execute(Object o, Connection c) {
		if (alwaysFlip) {
			Boolean current = (Boolean) ((BaseOption) c.getSettings()
					.findOptionByKey("fullscreen")).getValue();
			c.getSettings().setOption("fullscreen", Boolean.valueOf(!current.booleanValue()).toString());
			return null;
		}
		int mode = parseFlag(o == null ? "" : o.toString());
		if (mode == BAD) {
			c.sendDataToWindow(getErrorMessage("Fullscreen command usage:",
					".fullscreen          — say whether the status bar is hidden\n"
							+ ".fullscreen on | off | toggle\n"
							+ ".togglefullscreen flips it every time it is typed."));
			return null;
		}
		Boolean current = read(c);
		if (current == null) {
			c.sendDataToWindow(getErrorMessage("Fullscreen command error",
					"Fullscreen option is not available yet."));
			return null;
		}
		if (mode == STATUS) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Fullscreen (.fullscreen) is currently "
					+ (current.booleanValue() ? "on" : "off") + ".\n"
					+ "Usage: .fullscreen on | off | toggle\n"
					+ ".togglefullscreen flips it every time, including .togglefullscreen off.\n");
			return null;
		}
		boolean want;
		if (mode == ON) {
			want = true;
		} else if (mode == OFF) {
			want = false;
		} else {
			want = !current.booleanValue();
		}
		if (want == current.booleanValue()) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Fullscreen already " + (want ? "on" : "off") + ".\n");
			return null;
		}
		c.getSettings().setOption("fullscreen", Boolean.valueOf(want).toString());
		c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
				+ "Fullscreen " + (want ? "on" : "off") + ".\n");
		return null;
	}

	/** One word: blank, {@code on}, {@code off}, or {@code toggle}. */
	static int parseFlag(String arg) {
		if (arg == null) {
			return BAD;
		}
		String token = arg.trim().toLowerCase(Locale.US);
		if (token.length() == 0) {
			return STATUS;
		}
		for (int i = 0; i < token.length(); i++) {
			if (Character.isWhitespace(token.charAt(i))) {
				return BAD;
			}
		}
		if (token.equals("on")) {
			return ON;
		}
		if (token.equals("off")) {
			return OFF;
		}
		if (token.equals("toggle")) {
			return TOGGLE;
		}
		return BAD;
	}

	private static Boolean read(Connection c) {
		if (c == null || c.getSettings() == null) {
			return null;
		}
		Object found = c.getSettings().findOptionByKey("fullscreen");
		if (!(found instanceof BaseOption)) {
			return null;
		}
		Object value = ((BaseOption) found).getValue();
		if (value instanceof Boolean) {
			return (Boolean) value;
		}
		return null;
	}
}
