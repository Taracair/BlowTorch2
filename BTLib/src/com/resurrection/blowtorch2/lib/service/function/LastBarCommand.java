package com.resurrection.blowtorch2.lib.service.function;

import com.resurrection.blowtorch2.lib.service.Connection;

/**
 * {@code .lastbar} — chips of this world's recent commands.
 * Not sent to the world. The list and the prefs live in the UI process.
 */
public class LastBarCommand extends SpecialCommand {
	public LastBarCommand() {
		this.commandName = "lastbar";
	}

	@Override
	public Object execute(Object o, Connection c) {
		if (c == null || c.getService() == null) {
			return null;
		}
		String argument = o instanceof String ? (String) o : "";
		LastBarRequest req = LastBarRequest.parse(argument);
		if (req.kind == LastBarRequest.MISUSE) {
			c.sendDataToWindow(getErrorMessage(
					"Last command bar:",
					".lastbar N            — show the N newest and turn the bar on (1–100)\n"
							+ ".lastbar on|off       — show or hide it\n"
							+ ".lastbar              — count, length, order, and whether it is on\n"
							+ ".lastbar length N     — characters of each command (3–40)\n"
							+ ".lastbar order left|right — First on the left, or on the right\n"
							+ "A tap sends that command. Nothing here is sent to the world."));
			return null;
		}
		String trimmed = argument == null ? "" : argument.trim();
		String action = trimmed.length() == 0 ? "lastbar" : "lastbar:" + trimmed;
		c.getService().doRunUiAction(action);
		return null;
	}
}
