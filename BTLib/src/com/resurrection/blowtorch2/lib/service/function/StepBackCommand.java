package com.resurrection.blowtorch2.lib.service.function;

import com.resurrection.blowtorch2.lib.service.Connection;

/**
 * Sends the reverse of the last number-pad walk. The window remembers that walk.
 */
public final class StepBackCommand extends SpecialCommand {

	public StepBackCommand() {
		commandName = "stepback";
	}

	@Override
	public Object execute(Object o, Connection c) {
		if (c == null) {
			return null;
		}
		if (!DisconnectCommand.isBare(o)) {
			c.sendDataToWindow(getErrorMessage("Step back usage:",
					".stepback        — reverse of the last number-pad walk (no arguments)"));
			return null;
		}
		if (c.getService() != null) {
			c.getService().doStepBack();
		}
		return null;
	}
}
