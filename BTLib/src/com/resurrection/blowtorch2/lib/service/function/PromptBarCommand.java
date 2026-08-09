package com.resurrection.blowtorch2.lib.service.function;

import java.util.Locale;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BaseOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BooleanOption;

/**
 * {@code .prompt on|off} — the world's prompt on its own bar above the input
 * line, instead of repeating down the screen.
 *
 * <p>Off by default: it changes where text appears, and a player who has not
 * asked for that should not have it happen to them. The setting lives in the
 * profile (Options → Input), so this writes the option rather than a runtime
 * flag.
 */
public class PromptBarCommand extends SpecialCommand {

	public static final String OPTION_KEY = "prompt_bar";

	public PromptBarCommand() {
		this.commandName = "prompt";
	}

	@Override
	public Object execute(Object o, Connection c) {
		String arg = o == null ? "" : ((String) o).trim().toLowerCase(Locale.US);
		if (arg.equals("on") || arg.equals("off")) {
			boolean on = arg.equals("on");
			c.updateBooleanSetting(OPTION_KEY, on);
			c.sendDataToWindow("\n" + Colorizer.getBrightCyanColor()
					+ (on
						? "Prompt bar on. The prompt now sits above the input line."
						: "Prompt bar off; the prompt goes back into the game window.")
					+ Colorizer.getWhiteColor() + "\n" + seenLine(c));
			return null;
		}
		if (arg.length() == 0 || arg.equals("status")) {
			c.sendDataToWindow("\nPrompt bar is "
					+ (isOn(c) ? "on" : "off") + ". Use .prompt on|off\n"
					+ seenLine(c));
			return null;
		}
		c.sendDataToWindow(getErrorMessage("Prompt bar usage:",
				".prompt on    — put the prompt on its own bar\n"
				+ ".prompt off   — back to the game window\n"
				+ ".prompt       — say which it is, and how many prompts were seen\n\n"
				+ "The prompt is the line the world never finishes. The client\n"
				+ "already holds such a line back so a trigger cannot cut it in\n"
				+ "half, so it knows exactly which line is the prompt without\n"
				+ "guessing at its shape.\n\n"
				+ "Also under Options → Input.\n"));
		return null;
	}

	/**
	 * The diagnostic the bar cannot give you. An empty bar has two causes that
	 * look identical — the feature is broken, or this world simply never sends a
	 * prompt (many MOOs do not). A count settles it without guessing.
	 */
	private static String seenLine(Connection c) {
		int seen = c == null ? 0 : c.getPromptsSeen();
		if (seen == 0) {
			return "Prompts seen: 0 — this world has not sent one yet."
					+ " Some worlds never do.\n";
		}
		return "Prompts seen: " + seen + "\n";
	}

	private static boolean isOn(Connection c) {
		if (c == null || c.getSettings() == null) {
			return false;
		}
		Object o = c.getSettings().findOptionByKey(OPTION_KEY);
		if (o instanceof BooleanOption
				&& ((BaseOption) o).getValue() instanceof Boolean) {
			return ((Boolean) ((BaseOption) o).getValue()).booleanValue();
		}
		return false;
	}
}
