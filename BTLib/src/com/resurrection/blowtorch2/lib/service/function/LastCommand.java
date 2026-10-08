package com.resurrection.blowtorch2.lib.service.function;

import com.resurrection.blowtorch2.lib.service.Connection;

/**
 * {@code .last} / {@code .Nlast} — send a recent command again.
 * {@code .last input} puts that line in the bar and does not send it.
 *
 * <p>The history list lives in the UI process. This asks that process to
 * submit the stored line the same way Send does. The literal {@code .last}
 * is not a history entry and is not written to the socket.
 */
public class LastCommand extends SpecialCommand {
	public LastCommand() {
		this.commandName = "last";
	}

	@Override
	public Object execute(Object o, Connection c) {
		if (c == null) {
			return null;
		}
		if (o instanceof LastRecall.Fill) {
			int fill = ((LastRecall.Fill) o).index;
			if (fill >= 1) {
				c.getService().doRunUiAction("lastfill:" + fill);
				return null;
			}
		}
		int n = (o instanceof Integer) ? ((Integer) o).intValue() : LastRecall.BAD_INDEX;
		if (n >= 1) {
			c.getService().doRunUiAction("last:" + n);
			return null;
		}
		c.sendDataToWindow(getErrorMessage(
				"Last command:",
				".last sends the newest command you sent.\n"
						+ ".2last sends the one before it, .3last the one before that.\n"
						+ "You sent north, then look, then inventory:\n"
						+ ".last sends inventory, .2last sends look, .3last sends north.\n"
						+ ".last input puts that command in the bar and does not send it.\n"
						+ ".2last input is the one before it. One space, then input.\n"
						+ ".lastlist opens this world's recent commands.\n"
						+ "N is 1 through the input history size (10–100).\n"
						+ "Nothing is sent when that command is not there.\n"
						+ "2last with no dot is a game line."));
		c.getService().doRunUiAction("last:0");
		return null;
	}
}
