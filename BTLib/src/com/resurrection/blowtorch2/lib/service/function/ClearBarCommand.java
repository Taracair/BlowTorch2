package com.resurrection.blowtorch2.lib.service.function;

import com.resurrection.blowtorch2.lib.service.Connection;

/**
 * One press of the input-bar Escape key. The window restores a recalled draft,
 * clears other text, or hides the keyboard when the bar is already empty.
 */
public final class ClearBarCommand extends SpecialCommand {

	public ClearBarCommand() {
		commandName = "clearbar";
	}

	@Override
	public Object execute(Object o, Connection c) {
		if (c == null) {
			return null;
		}
		if (!DisconnectCommand.isBare(o)) {
			c.sendDataToWindow(getErrorMessage("Clear bar usage:",
					".clearbar        — recalled draft back, otherwise clear, empty bar hides the keyboard (no arguments)"));
			return null;
		}
		if (c.getService() != null) {
			c.getService().doClearInputBar();
		}
		return null;
	}
}
