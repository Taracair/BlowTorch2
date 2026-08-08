package com.resurrection.blowtorch2.lib.service.function;

import java.util.Locale;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BaseOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.BooleanOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.IntegerOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.ListOption;
import com.resurrection.blowtorch2.lib.window.WordSuggestions;

/**
 * {@code .suggest on|off|lines N} — suggest words the game has just used while
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
	public static final String WHERE_KEY = "word_complete_where";
	public static final String LOOSE_KEY = "word_complete_loose";
	public static final String PHRASES_KEY = "word_complete_phrases";
	public static final String GHOST_KEY = "word_complete_ghost";
	public static final String PERSIST_KEY = "word_complete_persist";
	public static final String RANK_KEY = "word_complete_rank";
	public static final String PAIRS_KEY = "word_complete_pairs";
	public static final String OPACITY_KEY = "word_complete_opacity";

	/** Kept where the completer keeps it, so the two cannot drift apart. */
	public static final int MAX_LINES = WordSuggestions.MAX_LINES;

	/** How many chips the bar shows, so how high {@code .suggest N} goes. */
	public static final int MAX_PICK = WordSuggestions.MAX_ON_STRIP;

	/**
	 * What the command is called now. {@code .complete} still works and always
	 * will — it is in old profiles, old buttons and old notes — but "suggestions"
	 * is the word for what this does, and the messages use it.
	 */
	public static final String ALIAS_NAME = "suggest";
	public static final String LONG_ALIAS_NAME = "suggestions";

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
						? "Suggestions on. Type two letters of something the game"
							+ " said and it appears above the input bar; tap to use it."
						: "Suggestions off.")
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
		if (arg.startsWith("phrases")) {
			return setFlag(arg.substring("phrases".length()).trim(), c, PHRASES_KEY,
					"Whole names offered: after a grizzled cave troll walks in, gri"
						+ " now offers \"grizzled cave troll\" first and plain"
						+ " \"grizzled\" under it. Up to three words, and never past"
						+ " the end of a line.",
					"Single words only.");
		}
		if (arg.startsWith("ghost")) {
			return setFlag(arg.substring("ghost".length()).trim(), c, GHOST_KEY,
					"The rest of the top suggestion is now drawn after the cursor."
						+ " It is drawn only — what you send is what you typed.",
					"No suggestion drawn after the cursor.");
		}
		if (arg.startsWith("persist")) {
			return setFlag(arg.substring("persist".length()).trim(), c, PERSIST_KEY,
					"The suggestion bar stays put now, empty or not, so the words"
						+ " stop moving. Empty it shows only its grip — tap that to"
						+ " collapse it, or .suggest persist off to have it hide"
						+ " itself again.",
					"The suggestion bar hides itself when there is nothing to"
						+ " suggest.");
		}
		if (arg.startsWith("pairs")) {
			return setFlag(arg.substring("pairs".length()).trim(), c, PAIRS_KEY,
					"After a command word, what you have aimed that command at before"
						+ " comes first: kill offers what you have killed, wear what you"
						+ " have worn. Needs .suggest rank on as well. It knows nothing"
						+ " until you have played a while, and it only changes the order.",
					"Suggestions no longer take account of which command you are typing.");
		}
		if (arg.startsWith("rank")) {
			return setFlag(arg.substring("rank".length()).trim(), c, RANK_KEY,
					"Suggestions are now ordered by where you are in the line: the"
						+ " words you start commands with come first at the start of"
						+ " a line, the words you aim them at come first after it."
						+ " Learned from what you type, so it knows nothing yet on a"
						+ " world you have just started. Nothing is taken away, only"
						+ " moved.",
					"Suggestions are back to newest first, wherever the cursor is.");
		}
		if (arg.startsWith("where")) {
			return setWhere(arg.substring("where".length()).trim(), c);
		}
		// The verb this replaced. Still registered, and always will be: it is in
		// profiles, buttons and notes. on means floating, off means the strip.
		if (arg.startsWith("overlay")) {
			String rest = arg.substring("overlay".length()).trim();
			if (rest.equals("on")) {
				rest = "floating";
			} else if (rest.equals("off")) {
				rest = "bar";
			}
			return setWhere(rest, c);
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
			c.sendDataToWindow(getErrorMessage("Suggestions usage:",
					"The bar shows at most " + MAX_PICK + " suggestions, so"
					+ " .suggest 1 to .suggest " + MAX_PICK + ".\n"));
			return null;
		}
		if (arg.length() == 0 || arg.equals("status")) {
			c.sendDataToWindow("\nSuggestions are "
					+ (isOn(c) ? "on" : "off")
					+ ", remembering the last " + describeLines(lines(c))
					+ ".\nThe bar is " + describeWhere(where(c))
					+ (where(c) == WordSuggestions.WHERE_FLOATING
						? ", at " + opacity(c) + "% solid" : "")
					+ (where(c) == WordSuggestions.WHERE_NONE ? ""
						: ", " + (flagOn(c, PERSIST_KEY)
							? "always up" : "up only when it has something"))
					+ ".\nWhole names " + (flagOn(c, PHRASES_KEY) ? "offered" : "not offered")
					+ ".\nTypos " + (flagOn(c, LOOSE_KEY) ? "forgiven" : "not forgiven")
					+ ", ghost " + (flagOn(c, GHOST_KEY) ? "on" : "off")
					+ ".\nOrder is " + (flagOn(c, RANK_KEY)
						? "by where you are in the line" : "newest first")
					+ (flagOn(c, RANK_KEY) && flagOn(c, PAIRS_KEY)
						? ", and by what you usually do with that command" : "")
					+ ".\nUse .suggest on|off, lines N, where floating|bar|off,"
					+ " phrases/loose/ghost/persist/rank/pairs on|off, opacity N\n");
			return null;
		}
		c.sendDataToWindow(getErrorMessage("Suggestions usage:",
				".suggest on       — suggest words the game just used\n"
				+ ".suggest off      — stop\n"
				+ ".suggest lines N  — how far back counts as recent (0 = all session)\n"
				+ ".suggest 1.." + MAX_PICK + "     — take that suggestion off the bar\n"
				+ ".suggest phrases on|off  — offer whole names: gri gives\n"
				+ "                           \"grizzled cave troll\", not just \"grizzled\"\n"
				+ ".suggest loose on|off    — grzld finds grizzled\n"
				+ ".suggest ghost on|off    — draw the rest of the word after the cursor\n"
				+ ".suggest where floating|bar|off — where the bar of chips goes,\n"
				+ "                           or off for none; the ghost still works\n"
				+ ".suggest persist on|off  — keep the bar up even when it is empty\n"
				+ ".suggest opacity N       — how solid those chips are\n"
				+ ".suggest          — say which it is\n\n"
				+ "(.complete still works, and means the same thing.)\n\n"
				+ "This completes mob names, player names and item words the\n"
				+ "keyboard will never know, and would rather correct into\n"
				+ "English. Type \"k gri\" after a grizzled cave troll walks in.\n\n"
				+ "Also under Options → Input → Suggestions.\n"));
		return null;
	}

	private Object setLines(String arg, Connection c) {
		if (arg.length() == 0) {
			c.sendDataToWindow("\nSuggestions remember the last "
					+ describeLines(lines(c)) + ".\nUse .suggest lines N (0-"
					+ MAX_LINES + ", 0 = the whole session)\n");
			return null;
		}
		int n;
		try {
			n = Integer.parseInt(arg.split("\\s+")[0]);
		} catch (NumberFormatException e) {
			c.sendDataToWindow(getErrorMessage("Suggestions usage:",
					".suggest lines N — a number from 0 to " + MAX_LINES + ".\n"
					+ "0 means keep everything this session said.\n"));
			return null;
		}
		if (n < 0 || n > MAX_LINES) {
			c.sendDataToWindow(getErrorMessage("Suggestions usage:",
					"Lines must be between 0 and " + MAX_LINES + ".\n"));
			return null;
		}
		c.updateIntegerSetting(LINES_KEY, n);
		c.sendDataToWindow("\n" + Colorizer.getBrightCyanColor()
				+ "Suggestions now remember the last " + describeLines(n) + "."
				+ Colorizer.getWhiteColor() + "\n");
		return null;
	}

	/**
	 * Where the bar of chips goes, or that there is none.
	 *
	 * <p>One setting with three values rather than two switches: "no bar, but
	 * floating" is not a thing, and two switches can say it.
	 */
	private Object setWhere(String arg, Connection c) {
		if (arg.length() == 0) {
			c.sendDataToWindow("\nThe suggestion bar is " + describeWhere(where(c))
					+ ".\nUse .suggest where floating|bar|off\n");
			return null;
		}
		int picked;
		if (arg.equals("floating") || arg.equals("float") || arg.equals("over")) {
			picked = WordSuggestions.WHERE_FLOATING;
		} else if (arg.equals("bar") || arg.equals("strip") || arg.equals("below")) {
			picked = WordSuggestions.WHERE_BAR;
		} else if (arg.equals("off") || arg.equals("none") || arg.equals("nowhere")) {
			picked = WordSuggestions.WHERE_NONE;
		} else {
			c.sendDataToWindow(getErrorMessage("Suggestions usage:",
					".suggest where floating|bar|off\n\n"
					+ "floating — chips over the game text, on the input bar\n"
					+ "bar      — a strip below the game window; it takes height,\n"
					+ "           so the text jumps unless .suggest persist on\n"
					+ "off      — no bar at all. Suggestions still work: the ghost\n"
					+ "           still draws and .suggest 1.." + MAX_PICK
						+ " still picks.\n"));
			return null;
		}
		c.updateIntegerSetting(WHERE_KEY, picked);
		c.sendDataToWindow("\n" + Colorizer.getBrightCyanColor()
				+ "The suggestion bar is now " + describeWhere(picked) + "."
				+ (picked == WordSuggestions.WHERE_NONE
					? " The ghost after the cursor is unaffected — .suggest ghost on"
						+ " if you want it."
					: "")
				+ Colorizer.getWhiteColor() + "\n");
		return null;
	}

	private static String describeWhere(int where) {
		if (where == WordSuggestions.WHERE_BAR) {
			return "a strip below the game window";
		}
		if (where == WordSuggestions.WHERE_NONE) {
			return "off — no bar anywhere";
		}
		return "floating over the game text";
	}

	private Object setOpacity(String arg, Connection c) {
		if (arg.length() == 0) {
			c.sendDataToWindow("\nSuggestion chips are " + opacity(c)
					+ "% solid.\nUse .suggest opacity N ("
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
			c.sendDataToWindow(getErrorMessage("Suggestions usage:",
					".suggest opacity N — a number from "
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
			c.sendDataToWindow(getErrorMessage("Suggestions usage:",
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

	private static int where(Connection c) {
		BaseOption o = findOption(c, WHERE_KEY);
		if (o instanceof ListOption && o.getValue() instanceof Integer) {
			return ((Integer) o.getValue()).intValue();
		}
		return WordSuggestions.DEFAULT_WHERE;
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
