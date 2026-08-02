package com.resurrection.blowtorch2.lib.service.function;

import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.service.StellarService;

/**
 * Show/hide the Edit tools strip (Sel/Cut/Copy/Paste + cursor pad):
 * {@code .editpanel [on|off]}. No argument toggles.
 */
public class EditPanelCommand extends SpecialCommand {

	public EditPanelCommand() {
		this.commandName = "editpanel";
	}

	public Object execute(Object o, Connection c) {
		String arg = o == null ? "" : ((String) o).trim();
		Integer mode = parseMode(arg);
		if (mode == null) {
			c.sendDataToWindow(getErrorMessage("Editpanel command usage:",
					".editpanel          — toggle the Edit tools strip\n"
							+ ".editpanel on | off — show or hide\n"
							+ "Edit button: .editbutton on|off"));
			return null;
		}
		c.getService().doInputBarEditTools(mode.intValue());
		return null;
	}

	/**
	 * No args → toggle. Only {@code on}/{@code off} otherwise (no synonym aliases).
	 */
	static Integer parseMode(String arg) {
		if (arg == null) {
			return null;
		}
		String token = arg.trim().toLowerCase();
		if (token.length() == 0) {
			return Integer.valueOf(StellarService.INPUT_EDIT_TOOLS_TOGGLE);
		}
		int sp = token.indexOf(' ');
		if (sp > 0) {
			token = token.substring(0, sp);
		}
		if (token.equals("on")) {
			return Integer.valueOf(StellarService.INPUT_EDIT_TOOLS_ON);
		}
		if (token.equals("off")) {
			return Integer.valueOf(StellarService.INPUT_EDIT_TOOLS_OFF);
		}
		return null;
	}
}
