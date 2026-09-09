package com.resurrection.blowtorch2.lib.service.function;

import com.resurrection.blowtorch2.lib.ping.PingCommandParser;
import com.resurrection.blowtorch2.lib.ping.PingHudLayout;
import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;

/**
 * RTT overlay: {@code .ping show|hide}, {@code .ping opacity N}, {@code .ping size N}.
 */
public class PingCommand extends SpecialCommand {

	public static final String OPTION_ENABLED = "ping_hud";
	public static final String OPTION_OPACITY = "ping_opacity";
	public static final String OPTION_SIZE = "ping_size";
	public static final String OPTION_X = "ping_x";
	public static final String OPTION_Y = "ping_y";

	public PingCommand() {
		this.commandName = "ping";
	}

	public Object execute(Object o, Connection c) {
		PingCommandParser.Result r = PingCommandParser.parse(
				o == null ? "" : ((String) o));
		if (r.error != null) {
			c.sendDataToWindow(getErrorMessage("Ping command usage:", r.error));
			return null;
		}
		boolean on = c.getMainWindowBooleanOption(OPTION_ENABLED, false);
		int opacity = PingHudLayout.clampOpacity(
				c.getMainWindowIntegerOption(OPTION_OPACITY, PingHudLayout.DEFAULT_OPACITY));
		int size = PingHudLayout.clampSize(
				c.getMainWindowIntegerOption(OPTION_SIZE, PingHudLayout.DEFAULT_SIZE));
		int x = PingHudLayout.clampPercent(
				c.getMainWindowIntegerOption(OPTION_X, PingHudLayout.DEFAULT_X));
		int y = PingHudLayout.clampPercent(
				c.getMainWindowIntegerOption(OPTION_Y, PingHudLayout.DEFAULT_Y));

		if (PingCommandParser.ACTION_STATUS.equals(r.action)) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Ping overlay is " + (on ? "on" : "off")
					+ ", opacity " + opacity + "%, size " + size
					+ ", pos " + x + " " + y + ".\n"
					+ PingCommandParser.usage()
					+ "Type a command; the chip is round-trip until the next game line.\n"
					+ "Also: Options → Window → Ping overlay?\n");
			return null;
		}
		if (PingCommandParser.ACTION_SHOW.equals(r.action)
				|| PingCommandParser.ACTION_HIDE.equals(r.action)
				|| PingCommandParser.ACTION_TOGGLE.equals(r.action)) {
			boolean desired = PingCommandParser.ACTION_SHOW.equals(r.action);
			if (PingCommandParser.ACTION_TOGGLE.equals(r.action)) {
				desired = !on;
			}
			if (desired == on) {
				c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
						+ "Ping overlay already "
						+ (desired ? "on" : "off") + ".\n");
				return null;
			}
			if (!c.updateMainWindowBooleanOption(OPTION_ENABLED, desired)) {
				c.sendDataToWindow(getErrorMessage("Ping command error",
						"There is no game window to change yet."));
				return null;
			}
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Ping overlay " + (desired ? "on" : "off") + ".\n");
			return null;
		}
		if (PingCommandParser.ACTION_OPACITY.equals(r.action)) {
			int next = r.opacity.intValue();
			if (next == opacity) {
				c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
						+ "Ping opacity already " + next + "%.\n");
				return null;
			}
			if (!c.updateMainWindowIntegerOption(OPTION_OPACITY, next)) {
				c.sendDataToWindow(getErrorMessage("Ping command error",
						"There is no game window to change yet."));
				return null;
			}
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Ping opacity " + next + "%.\n");
			return null;
		}
		if (PingCommandParser.ACTION_SIZE.equals(r.action)) {
			int next = r.size.intValue();
			if (next == size) {
				c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
						+ "Ping size already " + next + ".\n");
				return null;
			}
			if (!c.updateMainWindowIntegerOption(OPTION_SIZE, next)) {
				c.sendDataToWindow(getErrorMessage("Ping command error",
						"There is no game window to change yet."));
				return null;
			}
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Ping size " + next + ".\n");
			return null;
		}
		if (PingCommandParser.ACTION_POS.equals(r.action)) {
			int nx = r.x.intValue();
			int ny = r.y.intValue();
			if (nx == x && ny == y) {
				c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
						+ "Ping already at " + nx + " " + ny + ".\n");
				return null;
			}
			if (!c.updateMainWindowIntegerOption(OPTION_X, nx)
					|| !c.updateMainWindowIntegerOption(OPTION_Y, ny)) {
				c.sendDataToWindow(getErrorMessage("Ping command error",
						"There is no game window to change yet."));
				return null;
			}
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Ping pos " + nx + " " + ny + ".\n");
			return null;
		}
		c.sendDataToWindow(getErrorMessage("Ping command usage:", PingCommandParser.usage()));
		return null;
	}
}
