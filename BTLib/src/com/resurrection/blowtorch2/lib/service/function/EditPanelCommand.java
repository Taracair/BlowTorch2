package com.resurrection.blowtorch2.lib.service.function;

import java.util.Locale;

import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.service.StellarService;

/**
 * Edit tools strip (Sel/Cut/Copy/Paste + cursor pad).
 * Bare {@code .editpanel} asks the UI to say whether the strip is open.
 * {@code on}, {@code off}, and {@code toggle} set it. {@code toggle} is the flip.
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
					".editpanel          — say whether the Edit tools strip is open\n"
							+ ".editpanel on | off | toggle — show, hide, or flip\n"
							+ "Edit button: .editbutton on|off"));
			return null;
		}
		c.getService().doInputBarEditTools(mode.intValue());
		return null;
	}

	/**
	 * Blank asks for status. One word: {@code on}, {@code off}, or {@code toggle}.
	 * A second word is not a mode.
	 */
	static Integer parseMode(String arg) {
		if (arg == null) {
			return null;
		}
		String token = arg.trim().toLowerCase(Locale.US);
		if (token.length() == 0) {
			return Integer.valueOf(StellarService.INPUT_EDIT_TOOLS_STATUS);
		}
		if (hasWhitespace(token)) {
			return null;
		}
		if (token.equals("on")) {
			return Integer.valueOf(StellarService.INPUT_EDIT_TOOLS_ON);
		}
		if (token.equals("off")) {
			return Integer.valueOf(StellarService.INPUT_EDIT_TOOLS_OFF);
		}
		if (token.equals("toggle")) {
			return Integer.valueOf(StellarService.INPUT_EDIT_TOOLS_TOGGLE);
		}
		return null;
	}

	private static boolean hasWhitespace(String token) {
		for (int i = 0; i < token.length(); i++) {
			if (Character.isWhitespace(token.charAt(i))) {
				return true;
			}
		}
		return false;
	}
}
