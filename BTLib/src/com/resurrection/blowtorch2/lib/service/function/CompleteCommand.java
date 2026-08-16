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
	/** Plain word before the whole name built on it — reached by .suggest plain. */
	public static final String SHORT_FIRST_KEY = "word_complete_short_first";
	/** Shorter completions before longer ones — reached by .suggest short. */
	public static final String SHORTER_KEY = "word_complete_shorter_first";
	public static final String GHOST_KEY = "word_complete_ghost";
	public static final String GHOST_LINES_KEY = "word_complete_ghost_lines";
	/** How many suggestions the bar and ghost may show at once. */
	public static final String SHOW_KEY = "word_complete_show";
	/** Ghost rows the input bar will grow to carry, plus the inline one. */
	public static final int MAX_GHOST_LINES = 6;
	public static final String PERSIST_KEY = "word_complete_persist";
	public static final String RANK_KEY = "word_complete_rank";
	public static final String PAIRS_KEY = "word_complete_pairs";
	public static final String OPACITY_KEY = "word_complete_opacity";

	private static final String BAG_EDIT_USAGE =
			".suggest forget <word>            — drop that word from this session\n"
			+ "                                  and from what your commands taught\n"
			+ ".suggest unpair <verb> <target>  — drop that pairing only\n"
			+ ".suggest weight <verb> <target> N — how often that pairing has been\n"
			+ "                                  seen (0 is the same as unpair)\n"
			+ ".suggest clear                   — throw the whole bag away\n";

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
		if (arg.startsWith("show")) {
			return setShow(arg.substring("show".length()).trim(), c);
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
		// Before "ghost", or ".suggest ghostlines 3" is read as ".suggest ghost".
		if (arg.startsWith("short")) {
			return setFlag(arg.substring("short".length()).trim(), c, SHORTER_KEY,
					"Shorter suggestions now come first: cr offers crate before"
						+ " crime-and-punishment, whatever the world said last. Order by"
						+ " place in the line still decides which group leads; this"
						+ " decides the order inside it.",
					"Newest first again.");
		}
		// This is what .suggest short used to do, and it is a different thing:
		// one word against the whole name built on that same word, nothing else.
		// The name moved on 9 Aug because "short" reads as "shorter first" and
		// was reported as broken for not being it. The option key did not move,
		// so nobody's saved choice changed meaning.
		if (arg.startsWith("plain")) {
			return setFlag(arg.substring("plain".length()).trim(), c, SHORT_FIRST_KEY,
					"The plain word now comes before the whole name built on it: expl"
						+ " offers explosive, then explosive crates. Only those two swap"
						+ " places; nothing else moves. Needs .suggest phrases on to mean"
						+ " anything.",
					"Whole names come first again.");
		}
		if (arg.startsWith("ghostlines")) {
			return setGhostLines(arg.substring("ghostlines".length()).trim(), c);
		}
		if (arg.startsWith("ghost")) {
			return setFlag(arg.substring("ghost".length()).trim(), c, GHOST_KEY,
					"The rest of the top suggestion is now drawn after the cursor."
						+ " It is drawn only — what you send is what you typed."
						+ " Tap it to take it. With .suggest ghostlines above 1 the"
						+ " others are listed beside it, each one tappable too.",
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
		if (arg.equals("learned") || arg.equals("bag")) {
			return showLearned(c);
		}
		if (arg.equals("clear")) {
			return forgetLearned(c);
		}
		String[] tokens = arg.split("\\s+");
		if (tokens.length > 0 && tokens[0].equals("forget")) {
			return forgetOne(tokens, c);
		}
		if (tokens.length > 0 && tokens[0].equals("unpair")) {
			return unpairOne(tokens, c);
		}
		if (tokens.length > 0 && tokens[0].equals("weight")) {
			return weightOne(tokens, c);
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
			int maxPick = showCount(c);
			if (pick >= 1 && pick <= maxPick) {
				c.pickCompletion(pick);
				return null;
			}
			c.sendDataToWindow(getErrorMessage("Suggestions usage:",
					"Showing at most " + maxPick + " suggestions, so"
					+ " .suggest 1 to .suggest " + maxPick + ".\n"));
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
					+ (flagOn(c, PHRASES_KEY) && flagOn(c, SHORT_FIRST_KEY)
						? ", after the plain word" : "")
					+ ".\nShorter suggestions " + (flagOn(c, SHORTER_KEY)
						? "first" : "not lifted")
					+ ".\nTypos " + (flagOn(c, LOOSE_KEY) ? "forgiven" : "not forgiven")
					+ ", ghost " + (flagOn(c, GHOST_KEY) ? "on" : "off")
					+ (!flagOn(c, GHOST_KEY) ? ""
						: ghostLines(c) > 1
							? ", listing the others on the rest of the line and up to "
								+ (ghostLines(c) - 1) + " row"
								+ (ghostLines(c) == 2 ? "" : "s") + " under it"
							: ", listing the others on the rest of the line")
					+ ".\nShowing at most " + showCount(c) + " suggestions"
					+ " (bar, ghost, and .suggest N — use .suggest show N)."
					+ "\nOrder is " + (flagOn(c, RANK_KEY)
						? "by where you are in the line" : "newest first")
					+ (flagOn(c, RANK_KEY) && flagOn(c, PAIRS_KEY)
						? ", and by what you usually do with that command" : "")
					+ ".\nUse .suggest on|off, lines N, where floating|bar|off,"
					+ " phrases/loose/ghost/persist/rank/pairs/short/plain on|off,"
					+ " ghostlines N, show N, opacity N,"
					+ " learned, clear, forget, unpair, weight\n");
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
				+ ".suggest show N          — at most N suggestions (bar + ghost), 1-8\n"
				+ ".suggest ghostlines N    — extra rows the field may grow by, 1-6.\n"
				+ "                           At 1 the others still fill the rest of\n"
				+ "                           the line. It is not the count — that is show\n"
				+ ".suggest where floating|bar|off — where the bar of chips goes,\n"
				+ "                           or off for none; the ghost still works\n"
				+ ".suggest persist on|off  — keep the bar up even when it is empty\n"
				+ ".suggest opacity N       — how solid those chips are\n"
				+ ".suggest learned         — what your commands have taught\n"
				+ ".suggest clear           — throw the whole bag away\n"
				+ ".suggest forget <word>   — drop that word (typo, bad pairing)\n"
				+ ".suggest unpair <verb> <target> — drop that pairing only\n"
				+ ".suggest weight <verb> <target> N — set that pairing's count\n"
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
					+ ".\nUse .suggest where floating|bar|off, or"
					+ " .suggest where next to step through them.\n");
			return null;
		}
		int picked;
		// One press that goes round the three. On a button this is the whole
		// point: floating for a fight, the strip while reading, nothing at all
		// when the ghost is doing the work — without three buttons for it.
		if (arg.equals("next") || arg.equals("cycle") || arg.equals("toggle")) {
			int now = where(c);
			if (now == WordSuggestions.WHERE_FLOATING) {
				picked = WordSuggestions.WHERE_BAR;
			} else if (now == WordSuggestions.WHERE_BAR) {
				picked = WordSuggestions.WHERE_NONE;
			} else {
				picked = WordSuggestions.WHERE_FLOATING;
			}
			c.updateIntegerSetting(WHERE_KEY, picked);
			c.sendDataToWindow("\n" + Colorizer.getBrightCyanColor()
					+ "Suggestion bar: " + describeWhere(picked)
					+ Colorizer.getWhiteColor() + "\n");
			return null;
		}
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
						+ " still picks.\n"
					+ "next     — step round the three; good on a button\n"));
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
	/**
	 * Print what this world's commands have taught.
	 *
	 * <p>Read from the file the UI process keeps rather than asked of it across
	 * the binder: the knowledge is already written out every ten seconds and at
	 * every pause, both processes see the same settings folder, and a report is
	 * not worth a new round trip on a path that has to stay quiet.
	 */
	private Object showLearned(Connection c) {
		if (c == null || c.getServiceContext() == null) {
			// Said out loud rather than reported as an empty bag. "Nothing
			// learned" and "could not look" read identically to a player and
			// mean opposite things.
			if (c != null) {
				c.sendDataToWindow("\nCould not read what this world has taught"
						+ " — the service is not up.\n");
			}
			return null;
		}
		com.resurrection.blowtorch2.lib.window.WordSuggestions w =
				new com.resurrection.blowtorch2.lib.window.WordSuggestions();
		com.resurrection.blowtorch2.lib.window.CommandKnowledgeStore.load(
				c.getServiceContext(), c.getDisplayName(), w);
		c.sendDataToWindow("\n" + Colorizer.getBrightCyanColor()
				+ "What your commands have taught on this world"
				+ Colorizer.getWhiteColor() + "\n"
				+ w.describeLearned(12, 6)
				+ "(" + w.describeCommandKnowledge() + ")\n"
				+ "Kept per world, and it travels with the world when you export it."
				+ " .suggest clear throws it all away; .suggest forget <word> drops"
				+ " one word; .suggest unpair / weight edit one pairing.\n");
		return null;
	}

	/**
	 * {@code .suggest forget} with no word is usage, not a wipe — that footgun
	 * is why surgical edits exist. With a word, tell the UI: the live bag is
	 * there, and a file snapshot can be ten seconds behind.
	 */
	private Object forgetOne(String[] tokens, Connection c) {
		if (tokens.length != 2) {
			c.sendDataToWindow(getErrorMessage("Suggestions usage:", BAG_EDIT_USAGE));
			return null;
		}
		c.forgetVocabulary("forget " + tokens[1]);
		c.sendDataToWindow("\n" + Colorizer.getBrightCyanColor()
				+ "Forgotten: " + tokens[1] + "."
				+ Colorizer.getWhiteColor() + "\n");
		return null;
	}

	private Object unpairOne(String[] tokens, Connection c) {
		if (tokens.length != 3) {
			c.sendDataToWindow(getErrorMessage("Suggestions usage:", BAG_EDIT_USAGE));
			return null;
		}
		c.forgetVocabulary("unpair " + tokens[1] + " " + tokens[2]);
		c.sendDataToWindow("\n" + Colorizer.getBrightCyanColor()
				+ "Unpaired: " + tokens[1] + " " + tokens[2] + "."
				+ Colorizer.getWhiteColor() + "\n");
		return null;
	}

	private Object weightOne(String[] tokens, Connection c) {
		if (tokens.length != 4) {
			c.sendDataToWindow(getErrorMessage("Suggestions usage:", BAG_EDIT_USAGE));
			return null;
		}
		int n;
		try {
			n = Integer.parseInt(tokens[3]);
		} catch (NumberFormatException e) {
			n = -1;
		}
		if (n < 0) {
			c.sendDataToWindow(getErrorMessage("Suggestions usage:", BAG_EDIT_USAGE));
			return null;
		}
		c.forgetVocabulary("weight " + tokens[1] + " " + tokens[2] + " " + n);
		c.sendDataToWindow("\n" + Colorizer.getBrightCyanColor()
				+ (n == 0
					? "Unpaired: " + tokens[1] + " " + tokens[2] + "."
					: tokens[1] + " " + tokens[2] + " now counts as " + n + ".")
				+ Colorizer.getWhiteColor() + "\n");
		return null;
	}

	/** Throw away the vocabulary and the learned pairings, on disk as well. */
	private Object forgetLearned(Connection c) {
		// The file first, then the reset: the UI reloads this world's pairings
		// when it takes a vocabulary reset, so a file still there would come
		// straight back in. The UI also erases on the input path for the same
		// command, because that path runs first and a dirty save between the
		// two would otherwise resurrect the bag; this side stays for buttons
		// and any route that never went through that input handler.
		com.resurrection.blowtorch2.lib.window.CommandKnowledgeStore.erase(
				c.getServiceContext(), c.getDisplayName());
		c.resetVocabulary();
		c.sendDataToWindow("\n" + Colorizer.getBrightCyanColor()
				+ "Forgotten: the words this session had picked up, and everything"
				+ " your commands had taught on this world."
				+ Colorizer.getWhiteColor() + "\n");
		return null;
	}

	private Object setShow(String arg, Connection c) {
		if (arg.length() == 0) {
			c.sendDataToWindow("\nShowing at most " + showCount(c)
					+ " suggestions at once.\n");
			return null;
		}
		int n;
		try {
			n = Integer.parseInt(arg);
		} catch (NumberFormatException e) {
			c.sendDataToWindow(getErrorMessage("Suggestions usage:",
					".suggest show N — a number from 1 to " + MAX_PICK + ".\n"));
			return null;
		}
		if (n < 1) {
			n = 1;
		}
		if (n > MAX_PICK) {
			n = MAX_PICK;
		}
		c.updateIntegerSetting(SHOW_KEY, n);
		c.sendDataToWindow("\n" + Colorizer.getBrightCyanColor()
				+ "At most " + n + " suggestions on the bar and in the ghost."
				+ Colorizer.getWhiteColor() + "\n");
		return null;
	}

	private Object setGhostLines(String arg, Connection c) {
		int n;
		try {
			n = Integer.parseInt(arg);
		} catch (NumberFormatException e) {
			c.sendDataToWindow(getErrorMessage("Suggestions usage:",
					".suggest ghostlines N — a number from 1 to " + MAX_GHOST_LINES
					+ ". 1 is the single word after the cursor.\n"));
			return null;
		}
		if (n < 1) {
			n = 1;
		}
		if (n > MAX_GHOST_LINES) {
			n = MAX_GHOST_LINES;
		}
		c.updateIntegerSetting(GHOST_LINES_KEY, n);
		c.sendDataToWindow("\n" + Colorizer.getBrightCyanColor()
				+ (n == 1
					? "The input bar keeps its height: the other suggestions"
						+ " fill what is left of the line you are typing on, and"
						+ " a +N at the end counts any that did not fit."
					: "The input bar may now grow by up to " + (n - 1) + " row"
						+ (n == 2 ? "" : "s") + " to list the other suggestions under"
						+ " what you are typing, side by side and each tappable. It"
						+ " takes only the rows it needs. Needs .suggest ghost on.")
				+ Colorizer.getWhiteColor() + "\n");
		return null;
	}

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

	private static int ghostLines(Connection c) {
		BaseOption o = findOption(c, GHOST_LINES_KEY);
		if (o instanceof IntegerOption && o.getValue() instanceof Integer) {
			return ((Integer) o.getValue()).intValue();
		}
		return 1;
	}

	private static int showCount(Connection c) {
		BaseOption o = findOption(c, SHOW_KEY);
		if (o instanceof IntegerOption && o.getValue() instanceof Integer) {
			int n = ((Integer) o.getValue()).intValue();
			if (n >= 1 && n <= MAX_PICK) {
				return n;
			}
		}
		return MAX_PICK;
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
