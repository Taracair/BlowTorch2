package com.resurrection.blowtorch2.lib.service.function;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;

/** Client-only echo: {@code .note <text>} prints to the window, never to the MUD. */
public class NoteCommand extends SpecialCommand {

	public NoteCommand() {
		this.commandName = "note";
	}

	public Object execute(Object o, Connection c) {
		String arg = o == null ? "" : ((String) o);
		if (arg.trim().length() == 0) {
			c.sendDataToWindow("\n" + Colorizer.getWhiteColor()
					+ "Usage: .note <text>\n");
			return null;
		}
		String text = Colorizer.getBrightCyanColor() + arg + Colorizer.getWhiteColor();
		c.sendDataToWindow("\n" + text + "\n");
		return null;
	}
}
