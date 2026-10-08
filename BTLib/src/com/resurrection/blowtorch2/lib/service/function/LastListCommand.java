package com.resurrection.blowtorch2.lib.service.function;

import com.resurrection.blowtorch2.lib.service.Connection;

/**
 * {@code .lastlist} — this world's recent commands, in their own window.
 * {@code .lastlist float} and {@code .lastfloat} are chips over the game.
 * {@code .lastlist bar} turns on the recent-command bar.
 * {@code .lastlist frame on} shows the title. {@code .lastlist frame off} hides it.
 * Not sent to the world, and not an extra-text slot.
 */
public class LastListCommand extends SpecialCommand {
	public LastListCommand() {
		this("lastlist");
	}

	public LastListCommand(final String name) {
		this.commandName = name;
	}

	@Override
	public Object execute(Object o, Connection c) {
		if (c == null || c.getService() == null) {
			return null;
		}
		String argument = o instanceof String ? (String) o : null;
		argument = LastListRequest.normalize(commandName, argument);
		LastListRequest.Parsed parsed = LastListRequest.read(argument);
		int kind = parsed.kind;
		if (kind == LastListRequest.MISUSE) {
			c.sendDataToWindow(getErrorMessage(
					"Recent commands:",
					".lastlist opens this world's recent commands.\n"
							+ ".lastlist list is that window.\n"
							+ ".lastlist float (or .lastfloat) shows them as chips over the game.\n"
							+ ".lastlist float off (or .lastfloat off) hides those chips.\n"
							+ ".lastlist float on shows them. .lastlist float toggle flips.\n"
							+ ".lastlist float N (or .lastfloat N) shows the N newest (1–100).\n"
							+ ".lastlist float length N (or .lastfloat length N) is characters of each (3–40).\n"
							+ ".lastlist bar turns on the recent-command bar.\n"
							+ ".lastlist frame toggles the title, gear, and close.\n"
							+ ".lastlist frame off hides them.\n"
							+ ".lastlist frame on brings them back.\n"
							+ "Anything else is not sent to the world."));
			return null;
		}
		String action = "lastlist";
		if (kind == LastListRequest.FRAME_ON) {
			action = "lastlist:frame:on";
		} else if (kind == LastListRequest.FRAME_OFF) {
			action = "lastlist:frame:off";
		} else if (kind == LastListRequest.FRAME_TOGGLE) {
			action = "lastlist:frame:toggle";
		} else if (kind == LastListRequest.FLOAT) {
			action = "lastlist:float";
		} else if (kind == LastListRequest.FLOAT_OFF) {
			action = "lastlist:float:off";
		} else if (kind == LastListRequest.FLOAT_TOGGLE) {
			action = "lastlist:float:toggle";
		} else if (kind == LastListRequest.FLOAT_COUNT) {
			action = "lastlist:float:count:" + parsed.number;
		} else if (kind == LastListRequest.FLOAT_LENGTH) {
			action = "lastlist:float:length:" + parsed.number;
		} else if (kind == LastListRequest.BAR) {
			action = "lastlist:bar";
		}
		c.getService().doRunUiAction(action);
		return null;
	}
}
