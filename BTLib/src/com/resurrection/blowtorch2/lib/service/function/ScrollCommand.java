package com.resurrection.blowtorch2.lib.service.function;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.window.ScrollStep;

/**
 * {@code .scrollup N}, {@code .scrolldown N}, {@code .scrollleft N},
 * {@code .scrollright N}. The window moves on the UI thread.
 */
public class ScrollCommand extends SpecialCommand {

	public ScrollCommand(final String name) {
		this.commandName = name;
	}

	public Object execute(Object o, Connection c) {
		if (c == null) {
			return null;
		}
		int width = c.getMainWindowIntegerOption(
				CanvasWidthCommand.OPTION_KEY, CanvasWidthCommand.MIN_PERCENT);
		ScrollStep.Decision d = ScrollStep.decide(
				commandName, o == null ? "" : String.valueOf(o), width);
		if (d == null) {
			c.sendDataToWindow(getErrorMessage("Scroll command usage:", ScrollStep.helpText()));
			return null;
		}
		if (d.kind == ScrollStep.Kind.NOTHING_SIDEWAYS) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor() + d.message() + "\n");
			return null;
		}
		if (d.kind != ScrollStep.Kind.MOVE) {
			c.sendDataToWindow(getErrorMessage("Scroll command usage:", d.message()));
			return null;
		}
		String action = d.toAction();
		if (action != null) {
			c.getService().doRunUiAction(action);
		}
		return null;
	}
}
