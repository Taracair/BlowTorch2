package com.resurrection.blowtorch2.lib.service.function;

import java.util.Locale;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BaseOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BooleanOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.IntegerOption;
import com.resurrection.blowtorch2.lib.window.WordSuggestions;

/**
 * {@code .complete on|off|lines N} — suggest words the game has just used while
 * you type.
 *
 * <p>Off by default, and while off the incoming text is not sent to the UI for
 * this at all, so it costs nothing.
 *
 * <p>Both settings live in the profile (Options → Input), so the command writes
 * the option rather than a runtime flag — otherwise the menu and the command
 * would disagree, and neither would survive a restart.
 */
public class CompleteCommand extends SpecialCommand {

	public static final String OPTION_KEY = "word_complete";
	public static final String LINES_KEY = "word_complete_lines";
	public static final String OVERLAY_KEY = "word_complete_overlay";
	public static final String LOOSE_KEY = "word_complete_loose";
	public static final String GHOST_KEY = "word_complete_ghost";
	public static final String OPACITY_KEY = "word_complete_opacity";

	/** Kept where the completer keeps it, so the two cannot drift apart. */
	public static final int MAX_LINES = WordSuggestions.MAX_LINES;

	/** How many chips the strip shows, so how high {@code .complete N} goes. */
	public static final int MAX_PICK = WordSuggestions.MAX_ON_STRIP;

	public CompleteCommand() {
		this.commandName = "complete";
	}

	@Override
	public Object execute(Object o, Connection c) {
		String arg = o == null ? "" : ((String) o).trim().toLowerCase(Locale.US);
		if (arg.equals("on") || arg.equals("off")) {
			boolean on = arg.equals("on");
			c.updateBooleanSetting(OPTION_KEY, on);
			c.sendDataToWindow("\n" + Colorizer.getBrightCyanColor()
					+ (on
						? "Word completion on. Type two letters of something the game"
							+ " said and it appears above the input bar; tap to use it."
						: "Word completion off.")
					+ Colorizer.getWhiteColor() + "\n");
			return null;
		}
		if (arg.startsWith("lines")) {
			return setLines(arg.substring("lines".length()).trim(), c);
		}
		if (arg.startsWith("loose")) {
			return setFlag(arg.substring("loose".length()).trim(), c, LOOSE_KEY,
					"Typos forgiven: grzld now finds grizzled when the exact"
						+ " spelling finds nothing.",
					"Exact spelling only.");
		}
		if (arg.startsWith("ghost")) {
			return setFlag(arg.substring("ghost".length()).trim(), c, GHOST_KEY,
					"The rest of the top suggestion is now drawn after the cursor."
						+ " It is drawn only — what you send is what you typed.",
					"No suggestion drawn after the cursor.");
		}
		if (arg.startsWith("overlay")) {
			return setOverlay(arg.substring("overlay".length()).trim(), c);
		}
		if (arg.startsWith("opacity")) {
			return setOpacity(arg.substring("opacity".length()).trim(), c);
		}
		// A bare number picks that chip. Before the usage message, and after
		// "lines", so ".complete lines 50" is never read as ".complete 50".
		if (arg.length() > 0 && isDigits(arg)) {
			int pick;
			try {
				pick = Integer.parseInt(arg);
			} catch (NumberFormatException e) {
				pick = 0;
			}
			if (pick >= 1 && pick <= MAX_PICK) {
				c.pickCompletion(pick);
				return null;
			}
			c.sendDataToWindow(getErrorMessage("Word completion usage:",
					"The strip shows at most " + MAX_PICK + " suggestions, so"
					+ " .complete 1 to .complete " + MAX_PICK + ".\n"));
			return null;
		}
		if (arg.length() == 0 || arg.equals("status")) {
			c.sendDataToWindow("\nWord completion is "
					+ (isOn(c) ? "on" : "off")
					+ ", remembering the last " + describeLines(lines(c))
					+ ".\nChips " + (overlayOn(c) ? "float over the game text at "
						+ opacity(c) + "% solid" : "sit in a strip below it")
					+ ".\nTypos " + (flagOn(c, LOOSE_KEY) ? "forgiven" : "not forgiven")
					+ ", ghost " + (flagOn(c, GHOST_KEY) ? "on" : "off")
					+ ".\nUse .complete on|off, lines N, loose/ghost/overlay on|off,"
					+ " opacity N\n");
			return null;
		}
		c.sendDataToWindow(getErrorMessage("Word completion usage:",
				".complete on      — suggest words the game just used\n"
				+ ".complete off     — stop\n"
				+ ".complete lines N — how far back counts as recent (0 = all session)\n"
				+ ".complete 1.." + MAX_PICK + "    — take that suggestion off the strip\n"
				+ ".complete loose on|off   — grzld finds grizzled\n"
				+ ".complete ghost on|off   — draw the rest of the word after the cursor\n"
				+ ".complete overlay on|off — chips over the game text, nothing moves\n"
				+ ".complete opacity N      — how solid those chips are\n"
				+ ".complete         — say which it is\n\n"
				+ "This completes mob names, player names and item words the\n"
				+ "keyboard will never know, and would rather correct into\n"
				+ "English. Type \"k gri\" after a grizzled cave troll walks in.\n\n"
				+ "Also under Options → Input.\n"));
		return null;
	}

	private Object setLines(String arg, Connection c) {
		if (arg.length() == 0) {
			c.sendDataToWindow("\nCompletion remembers the last "
					+ describeLines(lines(c)) + ".\nUse .complete lines N (0-"
					+ MAX_LINES + ", 0 = the whole session)\n");
			return null;
		}
		int n;
		try {
			n = Integer.parseInt(arg.split("\\s+")[0]);
		} catch (NumberFormatException e) {
			c.sendDataToWindow(getErrorMessage("Word completion usage:",
					".complete lines N — a number from 0 to " + MAX_LINES + ".\n"
					+ "0 means keep everything this session said.\n"));
			return null;
		}
		if (n < 0 || n > MAX_LINES) {
			c.sendDataToWindow(getErrorMessage("Word completion usage:",
					"Lines must be between 0 and " + MAX_LINES + ".\n"));
			return null;
		}
		c.updateIntegerSetting(LINES_KEY, n);
		c.sendDataToWindow("\n" + Colorizer.getBrightCyanColor()
				+ "Completion now remembers the last " + describeLines(n) + "."
				+ Colorizer.getWhiteColor() + "\n");
		return null;
	}

	private Object setOverlay(String arg, Connection c) {
		boolean current = overlayOn(c);
		if (arg.length() == 0) {
			c.sendDataToWindow("\nSuggestions are "
					+ (current ? "floating over the game text" : "in a strip below it")
					+ ".\nUse .complete overlay on|off\n");
			return null;
		}
		if (!arg.equals("on") && !arg.equals("off")) {
			c.sendDataToWindow(getErrorMessage("Word completion usage:",
					".complete overlay on|off\n\n"
					+ "On draws the chips over the game text. The strip below takes\n"
					+ "height while it shows, so the game window shrinks and the text\n"
					+ "jumps every time a suggestion appears; floating does not.\n"));
			return null;
		}
		boolean on = arg.equals("on");
		c.updateBooleanSetting(OVERLAY_KEY, on);
		c.sendDataToWindow("\n" + Colorizer.getBrightCyanColor()
				+ (on
					? "Suggestions now float over the game text; nothing moves when"
						+ " they appear."
					: "Suggestions back in a strip below the game window.")
				+ Colorizer.getWhiteColor() + "\n");
		return null;
	}

	private Object setOpacity(String arg, Connection c) {
		if (arg.length() == 0) {
			c.sendDataToWindow("\nSuggestion chips are " + opacity(c)
					+ "% solid.\nUse .complete opacity N ("
					+ WordSuggestions.MIN_OPACITY + "-100)\n");
			return null;
		}
		int n;
		try {
			n = Integer.parseInt(arg.split("\\s+")[0]);
		} catch (NumberFormatException e) {
			n = -1;
		}
		if (n < WordSuggestions.MIN_OPACITY || n > 100) {
			c.sendDataToWindow(getErrorMessage("Word completion usage:",
					".complete opacity N — a number from "
					+ WordSuggestions.MIN_OPACITY + " to 100.\n"
					+ "Lower lets more game text through behind the chips. The words\n"
					+ "themselves stay fully readable at every setting.\n"));
			return null;
		}
		c.updateIntegerSetting(OPACITY_KEY, n);
		c.sendDataToWindow("\n" + Colorizer.getBrightCyanColor()
				+ "Suggestion chips now " + n + "% solid."
				+ Colorizer.getWhiteColor() + "\n");
		return null;
	}

	/** on|off for one of the plain switches, with its own two sentences. */
	private Object setFlag(String arg, Connection c, String key,
			String onText, String offText) {
		if (arg.length() == 0) {
			c.sendDataToWindow("\n" + (flagOn(c, key) ? onText : offText) + "\n");
			return null;
		}
		if (!arg.equals("on") && !arg.equals("off")) {
			c.sendDataToWindow(getErrorMessage("Word completion usage:",
					"Use on or off.\n"));
			return null;
		}
		boolean on = arg.equals("on");
		c.updateBooleanSetting(key, on);
		c.sendDataToWindow("\n" + Colorizer.getBrightCyanColor()
				+ (on ? onText : offText) + Colorizer.getWhiteColor() + "\n");
		return null;
	}

	private static boolean flagOn(Connection c, String key) {
		BaseOption o = findOption(c, key);
		if (o instanceof BooleanOption && o.getValue() instanceof Boolean) {
			return ((Boolean) o.getValue()).booleanValue();
		}
		return false;
	}

	private static boolean overlayOn(Connection c) {
		BaseOption o = findOption(c, OVERLAY_KEY);
		if (o instanceof BooleanOption && o.getValue() instanceof Boolean) {
			return ((Boolean) o.getValue()).booleanValue();
		}
		return false;
	}

	private static int opacity(Connection c) {
		BaseOption o = findOption(c, OPACITY_KEY);
		if (o instanceof IntegerOption && o.getValue() instanceof Integer) {
			return ((Integer) o.getValue()).intValue();
		}
		return WordSuggestions.DEFAULT_OPACITY;
	}

	private static boolean isDigits(String s) {
		for (int i = 0; i < s.length(); i++) {
			if (!Character.isDigit(s.charAt(i))) {
				return false;
			}
		}
		return s.length() > 0;
	}

	private static String describeLines(int n) {
		return n <= 0 ? "whole session" : (n + " lines");
	}

	private static boolean isOn(Connection c) {
		BaseOption o = findOption(c, OPTION_KEY);
		if (o instanceof BooleanOption && o.getValue() instanceof Boolean) {
			return ((Boolean) o.getValue()).booleanValue();
		}
		return false;
	}

	private static int lines(Connection c) {
		BaseOption o = findOption(c, LINES_KEY);
		if (o instanceof IntegerOption && o.getValue() instanceof Integer) {
			return ((Integer) o.getValue()).intValue();
		}
		return 0;
	}

	private static BaseOption findOption(Connection c, String key) {
		if (c == null || c.getSettings() == null) {
			return null;
		}
		Object o = c.getSettings().findOptionByKey(key);
		return o instanceof BaseOption ? (BaseOption) o : null;
	}
}
