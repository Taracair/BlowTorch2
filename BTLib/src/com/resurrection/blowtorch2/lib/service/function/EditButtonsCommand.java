package com.resurrection.blowtorch2.lib.service.function;

import com.resurrection.blowtorch2.lib.service.Connection;

/** Open the button editor: {@code .editbuttons}. */
public class EditButtonsCommand extends SpecialCommand {

	public EditButtonsCommand() {
		this.commandName = "editbuttons";
	}

	public Object execute(Object o, Connection c) {
		if (c != null && c.getService() != null) {
			c.getService().doRunUiAction("editbuttons");
		}
		return null;
	}
}
