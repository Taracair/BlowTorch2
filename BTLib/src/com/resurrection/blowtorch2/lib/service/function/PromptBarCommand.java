package com.resurrection.blowtorch2.lib.service.function;

import java.util.Locale;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;

/**
 * {@code .prompt on|off} — the world's prompt on its own bar above the input
 * line, instead of repeating down the screen.
 *
 * <p>Off by default: it changes where text appears, and a player who has not
 * asked for that should not have it happen to them.
 */
public class PromptBarCommand extends SpecialCommand {

	public PromptBarCommand() {
		this.commandName = "prompt";
	}

	@Override
	public Object execute(Object o, Connection c) {
		String arg = o == null ? "" : ((String) o).trim().toLowerCase(Locale.US);
		if (arg.equals("on")) {
			c.setPromptBar(true);
			c.sendDataToWindow("\n" + Colorizer.getBrightCyanColor()
					+ "Prompt bar on. The prompt now sits above the input line."
					+ Colorizer.getWhiteColor() + "\n");
			return null;
		}
		if (arg.equals("off")) {
			c.setPromptBar(false);
			c.sendDataToWindow("\n" + Colorizer.getBrightCyanColor()
					+ "Prompt bar off; the prompt goes back into the game window."
					+ Colorizer.getWhiteColor() + "\n");
			return null;
		}
		if (arg.length() == 0 || arg.equals("status")) {
			c.sendDataToWindow("\nPrompt bar is "
					+ (c.isPromptBar() ? "on" : "off") + ". Use .prompt on|off\n");
			return null;
		}
		c.sendDataToWindow(getErrorMessage("Prompt bar usage:",
				".prompt on    — put the prompt on its own bar\n"
				+ ".prompt off   — back to the game window\n"
				+ ".prompt       — say which it is\n\n"
				+ "The prompt is the line the world never finishes. The client\n"
				+ "already holds such a line back so a trigger cannot cut it in\n"
				+ "half, so it knows exactly which line is the prompt without\n"
				+ "guessing at its shape.\n"));
		return null;
	}
}
