package com.resurrection.blowtorch2.lib.service.function;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BaseOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BooleanOption;

/**
 * {@code .dobell} — fire the bell reaction now.
 *
 * <p>This is how a trigger makes a noise: a Script action of {@code .dobell}
 * rings whatever Options → Bell has turned on. Worth knowing, because the
 * trigger action list has no "play a sound" entry and this is easy to miss.
 *
 * <p>The three reactions are settings, and all three can be off — the
 * notification and the on-screen bell are off by default. Until now that case
 * was a command that did nothing, said nothing, and gave a player building a
 * combat alert no way to tell a broken trigger from a silent one.
 */
public class BellCommand extends SpecialCommand {

	/** Options → Bell, in the order a player meets them. */
	private static final String[] BELL_KEYS = {
		"bell_vibrate", "bell_notification", "bell_display" };

	public BellCommand() {
		this.commandName = "dobell";
	}

	public Object execute(Object o, Connection c) {
		if (!anyBellReactionOn(c)) {
			c.sendDataToWindow("\n" + Colorizer.getBrightCyanColor()
					+ "The bell rang, but nothing is set to answer it."
					+ Colorizer.getWhiteColor()
					+ "\nTurn on Vibrate, Generate Notification or Display Bell in"
					+ " Options → Bell.\nOnly Vibrate is on by default, and a"
					+ " phone in silent mode will not buzz.\n");
			return null;
		}
		c.getHandler().sendEmptyMessage(Connection.MESSAGE_BELLINC);
		return null;
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
