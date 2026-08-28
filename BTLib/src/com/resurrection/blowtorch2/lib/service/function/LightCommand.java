package com.resurrection.blowtorch2.lib.service.function;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.window.LightPaper;

/**
 * Light paper in the game window: {@code .light}, {@code .light on|off|toggle},
 * {@code .light 1–5} / {@code .light shade N}.
 *
 * <p>Same preference as Options → Window → Light theme? and Light paper shade.
 */
public class LightCommand extends SpecialCommand {

	public static final String OPTION_KEY = "light_paper";
	public static final String OPTION_SHADE = "light_paper_shade";

	public LightCommand() {
		this.commandName = "light";
	}

	public Object execute(Object o, Connection c) {
		String arg = o == null ? "" : ((String) o).trim();
		boolean on = c.getMainWindowBooleanOption(OPTION_KEY, false);
		int shade = LightPaper.clampShade(
				c.getMainWindowIntegerOption(OPTION_SHADE, LightPaper.SHADE_DEFAULT));

		if (arg.length() == 0) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Light theme is " + (on ? "on" : "off")
					+ ", shade " + shade + " (" + shadeLabel(shade) + ").\n"
					+ usage()
					+ "Also: Options → Window → Light theme?\n");
			return null;
		}

		String[] parts = arg.split("\\s+");
		String first = parts[0].toLowerCase();
		if ("shade".equals(first)) {
			if (parts.length < 2) {
				c.sendDataToWindow(getErrorMessage("Light command usage:", usage()));
				return null;
			}
			return setShade(c, parseShadeToken(parts[1]), shade, on);
		}

		Integer asShade = parseShadeToken(first);
		if (asShade != null) {
			return setShade(c, asShade, shade, true);
		}

		Boolean desired = DimRepeatCommand.parseOnOff(first);
		if ("toggle".equals(first)) {
			desired = Boolean.valueOf(!on);
		}
		if (desired == null) {
			c.sendDataToWindow(getErrorMessage("Light command usage:", usage()));
			return null;
		}
		if (desired.booleanValue() == on) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Light theme already "
					+ (desired.booleanValue() ? "on" : "off") + ".\n");
			return null;
		}
		if (!c.updateMainWindowBooleanOption(OPTION_KEY, desired.booleanValue())) {
			c.sendDataToWindow(getErrorMessage("Light command error",
					"There is no game window to change yet."));
			return null;
		}
		c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
				+ "Light theme "
				+ (desired.booleanValue() ? "on" : "off")
				+ (desired.booleanValue()
						? ", shade " + shade + " (" + shadeLabel(shade) + ")"
						: "")
				+ ".\n");
		return null;
	}

	private Object setShade(Connection c, Integer parsed, int current, boolean turnOn) {
		if (parsed == null) {
			c.sendDataToWindow(getErrorMessage("Light command usage:", usage()));
			return null;
		}
		int next = LightPaper.clampShade(parsed.intValue());
		boolean wasOn = c.getMainWindowBooleanOption(OPTION_KEY, false);
		if (turnOn && !wasOn) {
			if (!c.updateMainWindowBooleanOption(OPTION_KEY, true)) {
				c.sendDataToWindow(getErrorMessage("Light command error",
						"There is no game window to change yet."));
				return null;
			}
		}
		if (next == current && (wasOn || !turnOn)) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Light paper already shade " + next
					+ " (" + shadeLabel(next) + ").\n");
			return null;
		}
		if (next != current) {
			if (!c.updateMainWindowIntegerOption(OPTION_SHADE, next)) {
				c.sendDataToWindow(getErrorMessage("Light command error",
						"There is no game window to change yet."));
				return null;
			}
		}
		boolean nowOn = c.getMainWindowBooleanOption(OPTION_KEY, false);
		c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
				+ (nowOn ? "Light theme on, shade " : "Light paper shade ")
				+ next + " (" + shadeLabel(next) + ")."
				+ (nowOn ? "" : " Theme is off (.light on).")
				+ "\n");
		return null;
	}

	static Integer parseShadeToken(String token) {
		if (token == null) {
			return null;
		}
		try {
			int n = Integer.parseInt(token.trim());
			if (n < LightPaper.SHADE_MIN || n > LightPaper.SHADE_MAX) {
				return null;
			}
			return Integer.valueOf(n);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	static String shadeLabel(int shade) {
		switch (LightPaper.clampShade(shade)) {
		case 1:
			return "grey";
		case 2:
			return "warm";
		case 3:
			return "ivory";
		case 4:
			return "off-white";
		default:
			return "near-white";
		}
	}

	static String usage() {
		return "Usage: .light on | off | toggle | 1-5 | shade N\n"
				+ "No argument prints on or off and the shade (1 grey … 5 near-white; "
				+ "2 is the original warm paper). Ink darkens as the paper lightens.\n"
				+ "Game colours stay; whites and light greys are darkened so they stay readable.\n"
				+ "Extra-text follows. Launcher, Options, mapper, chat and ⋮ stay dark.\n";
	}
}
