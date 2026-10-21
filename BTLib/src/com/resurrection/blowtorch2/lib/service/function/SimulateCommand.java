package com.resurrection.blowtorch2.lib.service.function;

import java.io.UnsupportedEncodingException;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;

/**
 * {@code .simulate <text>} feeds text in as if the server sent it: triggers, gauges and the
 * mapper see it, the server does not. Same path as Lua {@code SimulateInput}.
 */
public class SimulateCommand extends SpecialCommand {

	public SimulateCommand() {
		this.commandName = "simulate";
	}

	public Object execute(Object o, Connection c) {
		String arg = o == null ? "" : ((String) o);
		if (arg.trim().length() == 0) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Usage: .simulate <text>   (\\n starts a new line)\n");
			return null;
		}
		try {
			c.simulateIncoming(incomingText(arg));
		} catch (UnsupportedEncodingException e) {
			c.sendDataToWindow(getErrorMessage("Simulate command", "The world's encoding cannot carry that text."));
		}
		return null;
	}

	/** {@code \n} typed as two characters is a line break; the last line is always finished. */
	static String incomingText(final String arg) {
		String text = arg.replace("\\n", "\n");
		return text.endsWith("\n") ? text : text + "\n";
	}
}
