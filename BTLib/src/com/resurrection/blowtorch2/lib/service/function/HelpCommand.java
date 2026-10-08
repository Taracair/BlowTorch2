package com.resurrection.blowtorch2.lib.service.function;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.service.OptionNegotiator;
import com.resurrection.blowtorch2.lib.service.Processor;
import com.resurrection.blowtorch2.lib.gauge.WidgetCommandParser;

/**
 * {@code .help} — every dot command, one line each, into the game window.
 *
 * <p>The commands are the fastest way to work this app from a phone, and until
 * now the only way to find one was the manual. This does not solve typing them;
 * it makes the answer always one command away.
 *
 * <p>The <em>names</em> are read from the live command table rather than typed
 * out here, so a command added later cannot quietly go missing from its own
 * help. The one-line descriptions are a table below, and anything registered
 * without one is still listed — marked, so the gap is visible instead of silent.
 */
public class HelpCommand extends SpecialCommand {

	/** The other name it answers to. Both were free; see the scratch note. */
	public static final String ALIAS_NAME = "commands";

	public HelpCommand() {
		this.commandName = "help";
	}

	/** Headings, in the order they are printed. */
	private static final String[] SECTIONS = {
		"Playing", "The window", "Input and suggestions", "Triggers and scripts",
		"The world and its protocols", "The map", "Buttons", "Other",
	};

	private static final Map<String, String> WHAT = new HashMap<String, String>();
	private static final Map<String, String> WHERE = new HashMap<String, String>();

	private static void cmd(final String name, final String section,
			final String what) {
		WHAT.put(name, what);
		WHERE.put(name, section);
	}

	static {
		cmd("help", "Other", "this list; .help word shows matches, and one close miss names that command");
		cmd("echo", "Playing", "says whether what you type is shown when the server has "
				+ "masked it");
		cmd("run", "Playing", "walk a speedwalk string, like 4n2e");
		cmd("rev", "Playing", "walk that speedwalk string backwards");
		cmd("disconnect", "Playing", "close the connection");
		cmd("reconnect", "Playing", "close it and open it again");
		cmd("switch", "Playing", "already-open session by exact display name");
		cmd("options", "Playing", "open the Options screen, as the menu does");
		cmd("settings", "Playing", "back up the settings file, or put the kept "
				+ "copy back");
		cmd("note", "Playing", "print a line in the window, without sending it");
		cmd("tutorial", "Playing", "lessons; .tutorial <topic> in any world");
		cmd("tips", "Playing", "short reminders when you type .commands (.tips on|always|off)");

		cmd("font", "The window", "game font size; +n and -n step from where you are");
		cmd("width", "The window", "text canvas width as a percent of the screen");
		cmd("avoidbuttons", "The window",
				"wrap text around buttons; .avoidbuttons letters|words");
		cmd("jumpsend", "The window", "after a send, jump to the newest text");
		cmd("dimrepeat", "The window", "dim a long line that comes back identical");
		cmd("light", "The window", "light paper and dark ink; .light on|off|1-5");
		cmd("when", "The window", "day/time to the left of ⋮ in history; .when opacity N");
		cmd("timestamp", "The window", "time each line arrived, on the right; .timestamp log");
		cmd("split", "The window", "two views of the same buffer; .split 40 | horizontal | vertical | off");
		cmd("ping", "The window", "one ICMP echo to this world's host; not sent to the world");
		cmd("osc8", "The window", "words the game marks (OSC 8); send:/prompt:/http; .osc8 on|off");
		cmd("wrap", "The window", "let the input bar grow to more than one line");
		cmd("togglefullscreen", "The window", "flips the status bar");
		cmd("fullscreen", "The window", "says whether the status bar is hidden");
		cmd("window", "The window", "prints help; .window show opens an extra text window");
		cmd("widget", "The window", "prints help; .widget show shows a gauge");
		cmd("gauge", "The window", "same as .widget");
		cmd("closewindow", "The window", "leave the game window (dirty exit)");
		cmd("search", "The window", "find text in the scrollback or old session logs");
		cmd("chat", "The window", "toggles the chat drawer; .chat open stays open");
		cmd("tapmenu", "The window", "how solid the menu a tapped word opens is");
		cmd("frame", "The window", "frames a server opens; .frame list|close|reopen");

		cmd("keyboard", "Input and suggestions",
				"prints usage; .kb insert and .kb add put text in the bar");
		cmd("last", "Input and suggestions",
				"resend a recent command (.last, .2last); .last input fills the bar; not sent");
		cmd("lastlist", "Input and suggestions",
				"opens this world's recent commands; .lastlist float N|length N|off|bar|frame");
		cmd("lastfloat", "Input and suggestions",
				"recent commands as chips over the game; .lastfloat N|length N|off");
		cmd("lastbar", "Input and suggestions",
				"chips of recent commands; .lastbar N|on|off|length|order; a tap sends; not sent");
		cmd("complete", "Input and suggestions",
				"same as .suggest (older name); also .suggestions");
		cmd("suggest", "Input and suggestions",
				"words the game just used; also .suggestions and .complete");
		cmd("suggestions", "Input and suggestions",
				"same as .suggest (alias); also .complete");
		cmd("prompt", "Input and suggestions", "pin the world's prompt above the input bar");
		cmd("editpanel", "Input and suggestions", "says whether the editing strip is open");
		cmd("editrows", "Input and suggestions",
				"two rows for the editing strip in landscape");
		cmd("editbutton", "Input and suggestions", "show or hide the Edit button");
		cmd("editbuttons", "Buttons", "open Edit buttons");
		cmd("gesture", "The window", "screen swipes on the game text; .gesture mode classic|1|2|both");
		cmd("sendbutton", "Input and suggestions", "show or hide the Send button");
		cmd("pick", "Input and suggestions",
				"prefix plus a word from the game text; insert leaves that line in the bar; .pick loupe size/zoom");
		cmd("copy", "Input and suggestions",
				"copy-widget magnifier; .copy loupe size/zoom");
		cmd("unaccent", "Input and suggestions",
				"strip accents when sending (usiądź → usiadz); .unaccent on|off");
		cmd("hyphen", "Input and suggestions",
				"break a long input word with a drawn hyphen; not sent");

		cmd("trigger", "Triggers and scripts", "says how many triggers are on; .trigger help for the rest");
		cmd("alias", "Triggers and scripts", "says how many aliases are on; .alias list shows them");
		cmd("timer", "Triggers and scripts", "play, pause, info, dump, duration");
		cmd("wait", "Triggers and scripts",
				"pause the rest of this line (.wait 5s / #wait 5m10s); stop cancels; show lists the queue");
		cmd("sound", "Triggers and scripts",
				"which volume a trigger's sound uses, and warning when it is off");
		cmd("dobell", "Triggers and scripts",
				"fire the bell reaction now; .dobell vibrate / .dobell alert ignore Options");
		cmd("probe", "Triggers and scripts",
				"prints the list of probes; .probe connection dumps a freeze");
		cmd("sensor", "Triggers and scripts",
				"what this phone can measure, and what triggers do with it");
		cmd("colordebug", "Triggers and scripts", "show the colour codes in a line");
		cmd("debug", "The window",
				"says whether the font-renderer label is on; .debug renderer show on|off");
		cmd("grabber", "Triggers and scripts",
				"inspect colour/style under a finger; copy layers or open a trigger");

		cmd("gmcp", "The world and its protocols", "says whether GMCP is on");
		cmd("mcp", "The world and its protocols", "says whether MCP is on");
		cmd("msdp", "The world and its protocols", "dumps the MSDP cache");
		cmd("mssp", "The world and its protocols", "dumps what the world says about itself");
		cmd("mxp", "The world and its protocols", "MXP SEND/colours/SOUND; .mxp on|off");
		cmd("protocols", "The world and its protocols",
				"prints what this world offered vs what is on; .protocols enable");

		cmd("map", "The map", "prints help; .map open shows the map");

		cmd("loadset", "Buttons", "prints usage; .loadset name loads that set");
		cmd("layoutwizard", "Buttons",
				"open the button layout wizard (packs, set names, size)");
		cmd("clearbuttons", "Buttons", "take the buttons away until the next set");
		cmd("heatmap", "Buttons",
				"shows the button heatmap; brighter tiles are used more");
		cmd("buttonopacity", "Buttons",
				"force every tile's alpha (.buttonopacity 100) until .buttonopacity restore");
		cmd("buttonsopacity", "Buttons", "same as .buttonopacity");
	}

	@Override
	public Object execute(Object o, Connection c) {
		String filter = o == null ? "" : ((String) o).trim().toLowerCase(Locale.US);
		int width = wrapWidth(c);
		List<String> names = c == null ? new ArrayList<String>() : c.getSystemCommands();
		if (names == null) {
			names = new ArrayList<String>();
		}
		// One entry per command, not per name it answers to: .kb and .keyboard
		// are the same command and two lines for them is noise. The exception is
		// a spelled-out synonym that has its own description above.
		HashSet<String> seen = new HashSet<String>();
		LinkedHashMap<String, List<String>> bySection =
				new LinkedHashMap<String, List<String>>();
		for (String section : SECTIONS) {
			bySection.put(section, new ArrayList<String>());
		}
		List<String> sorted = new ArrayList<String>(names);
		Collections.sort(sorted);
		for (String name : sorted) {
			if (!seen.add(name)) {
				continue;
			}
			String canonical = name;
			if (c != null) {
				SpecialCommand bound = c.systemCommand(name);
				if (bound != null && bound.commandName != null) {
					canonical = bound.commandName;
				}
			}
			String display = rowName(name, canonical, filter);
			if (display == null) {
				continue;
			}
			if (!display.equals(name) && !seen.add(display)) {
				continue;
			}
			String what = WHAT.get(display);
			String section = WHERE.get(display);
			if (section == null || bySection.get(section) == null) {
				section = "Other";
			}
			bySection.get(section).add(HelpColumn.formatRow("." + display,
					what == null ? "(no description yet)" : what, width));
		}

		StringBuilder out = new StringBuilder();
		out.append("\n");
		int shown = 0;
		for (String section : SECTIONS) {
			List<String> rows = bySection.get(section);
			if (rows == null || rows.isEmpty()) {
				continue;
			}
			out.append(Colorizer.getBrightCyanColor()).append(section)
				.append(Colorizer.getWhiteColor()).append("\n");
			for (String row : rows) {
				out.append(row).append("\n");
				shown++;
			}
		}
		if (shown == 0) {
			out.append("No command matches \"").append(filter).append("\".\n");
			String guess = HelpCommandGuess.didYouMean(HelpCommandGuess.pick(filter));
			if (guess != null) {
				out.append(guess);
			}
		} else if (filter.length() == 0) {
			out.append("\nMost take their own arguments — type the command on its"
					+ " own to see them. The manual has the long version.\n");
		}
		String sub = subcommandHelp(filter);
		if (sub != null) {
			out.append(sub);
		}
		if (c != null) {
			c.sendDataToWindow(out.toString());
		}
		return null;
	}

	/**
	 * A synonym with its own sentence stays. A bare alias ({@code .kb},
	 * {@code .commands}) does not get a second line.
	 */
	static boolean includeInIndex(final String name, final String canonical) {
		if (WHAT.containsKey(name)) {
			return true;
		}
		return canonical == null || canonical.equals(name);
	}

	/** Name to print, or null. {@code .help kb} prints {@code .keyboard}. */
	static String rowName(final String name, final String canonical, final String filter) {
		if (filter != null && filter.length() > 0 && (name == null || !name.contains(filter))) {
			return null;
		}
		if (includeInIndex(name, canonical)) {
			return name;
		}
		if (filter != null && filter.length() > 0 && canonical != null
				&& !canonical.contains(filter) && WHAT.containsKey(canonical)) {
			return canonical;
		}
		return null;
	}

	/** When the filter names one command family, list its subcommands. */
	static String subcommandHelp(final String filter) {
		if (filter == null || filter.length() == 0) {
			return null;
		}
		if (filter.equals("help") || filter.equals("commands")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .help:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .help              — every command, one line each\n"
					+ "  .help <word>       — only names containing that word\n"
					+ "  .commands          — same command (alias)\n";
		}
		if (filter.equals("echo")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .echo:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .echo              — say whether the input bar is masked\n"
					+ "  .echo on|off       — show or hide what you type (telnet ECHO)\n";
		}
		if (filter.equals("run")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .run:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .run <directions>  — speedwalk, e.g. 3n2e or 3ds,open door,3w\n"
					+ "  (ordinals: ⋮ → Speedwalk Directions; each letter has Reverse for .rev)\n";
		}
		if (filter.equals("rev")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .rev:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .rev <directions>  — same letters as .run, walked backwards\n"
					+ "  .rev 3n2e sends w;w;s;s;s. Comma text stays: not close door.\n"
					+ "  Compass n↔s / in↔out if Reverse is blank; door/cave: fill Reverse.\n";
		}
		if (filter.equals("disconnect")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .disconnect:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .disconnect        — close this connection (no arguments)\n";
		}
		if (filter.equals("reconnect")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .reconnect:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .reconnect         — close and open again (no arguments)\n";
		}
		if (filter.equals("switch")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .switch:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .switch            — list open sessions\n"
					+ "  .switch <name>     — foreground that already-open connection\n";
		}
		if (filter.equals("note")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .note:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .note <text>       — print locally; never sent to the world\n";
		}
		if (filter.equals("width")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .width:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .width             — say the current percent\n"
					+ "  .width <N> | +N | -N\n"
					+ "  .width toggle | off\n";
		}
		if (filter.equals("avoidbuttons")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .avoidbuttons:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .avoidbuttons           — say whether text wraps, and letters vs words\n"
					+ "  .avoidbuttons on|off|toggle\n"
					+ "  .avoidbuttons letters|words\n"
					+ "Off by default. ASCII maps may break. Grid pad and floating copies; live while dragging.\n"
					+ "letters (default) moves one character at a time; words keeps whole words off the hole.\n"
					+ "Also: Options → Window → Text → Text avoids on-screen buttons?\n";
		}
		if (filter.equals("jumpsend")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .jumpsend:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .jumpsend               — say whether a send jumps to the live edge\n"
					+ "  .jumpsend on|off|toggle\n"
					+ "On by default. Incoming text near the live edge still snaps there.\n"
					+ "Also: Options → Window → Layout → Jump to the live edge when you send?\n";
		}
		if (filter.equals("dimrepeat")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .dimrepeat:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .dimrepeat              — on/off, lines remembered, strength\n"
					+ "  .dimrepeat on|off|toggle\n"
					+ "  .dimrepeat lines N      — remember last N long lines (1-80)\n"
					+ "  .dimrepeat strength N   — how hard to dim (10-90; higher is darker)\n";
		}
		if (filter.equals("light")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .light:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .light              — on/off and shade 1–5\n"
					+ "  .light on|off|toggle\n"
					+ "  .light 1–5 | shade N  — 1 grey … 5 near-white; 2 is the original\n"
					+ "Also: Options → Window → Text → Light theme? / Light paper shade (1–5)\n"
					+ "Game canvas only. Launcher, Options, mapper, chat and ⋮ stay dark.\n"
					+ "Ink darkens as the paper lightens. Extra-text follows.\n";
		}
		if (filter.equals("when")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .when:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .when              — day/time to the left of ⋮ while in history\n"
					+ "  .when on|off|toggle\n"
					+ "  .when opacity N    — how solid that date is (15–100)\n"
					+ "  .search 14:32 | 18 Aug  — jump to that moment (while on)\n";
		}
		if (filter.equals("timestamp")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .timestamp:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .timestamp                 — on/off, log, which parts\n"
					+ "  .timestamp on|off|toggle | show|hide\n"
					+ "  .timestamp log on|off|toggle\n"
					+ "  .timestamp hour|minute|second|month|year [on|off]\n"
					+ "On the right of each line. Does not change wrapping, triggers or copy.\n"
					+ "Log prefixes the same stamp on the left of each session-log line.\n"
					+ "Not the same as .when (that is history chrome next to ⋮).\n";
		}
		if (filter.equals("split")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .split:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .split                 — status\n"
					+ "  .split on|40|horizontal|h|vertical|v|left|right|top|bottom\n"
					+ "  .split off             — one pane again\n"
					+ "horizontal/h is left/right; vertical/v is top/bottom.\n"
					+ "Primary (left or top) gets the percent (default 50). Buttons,\n"
					+ "mapper, chat and NAWS stay on the primary. Not a second buffer.\n";
		}
		if (filter.equals("ping")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .ping:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .ping  — one ICMP echo to this world's host\n"
					+ "Not sent to the world. A world that drops ICMP can still\n"
					+ "answer on the game port.\n";
		}
		if (filter.equals("wrap")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .wrap:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .wrap              — say whether the input bar may grow\n"
					+ "  .wrap on|off\n";
		}
		if (filter.equals("gesture")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .gesture:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .gesture                 — what is on now\n"
					+ "  .gesture mode classic|1|2|both\n"
					+ "  .gesture scroll two|hold|off   Scrolling. Refused in Classic and Two fingers\n"
					+ "  .gesture edit            — direction commands\n"
					+ "  .gesture show on|off     — mode, and scrolling in 1 finger / both\n"
					+ "  .gesture preview on|off  — arrow and command\n"
					+ "Classic: one finger scrolls, two fingers copy.\n"
					+ "One finger: a one-finger swipe sends a command.\n"
					+ "Two fingers: a two-finger swipe sends a command; a short tap can copy.\n"
					+ "Both: one-finger and two-finger swipes send commands.\n"
					+ "Options → Gestures\n";
		}
		if (filter.equals("editbuttons")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .editbuttons:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .editbuttons       — open Edit buttons\n"
					+ "Same as ⋮ → Edit buttons.\n";
		}
		if (filter.equals("unaccent")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .unaccent:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .unaccent              — say whether accents are stripped on send\n"
					+ "  .unaccent on|off\n"
					+ "Local echo shows the folded form (usiądź → usiadz); the input bar does not.\n";
		}
		if (filter.equals("hyphen")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .hyphen:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .hyphen                    — status\n"
					+ "  .hyphen on|off\n"
					+ "  .hyphen lang en|phone   — where the hyphen goes\n"
					+ "  .hyphen en|phone        — same as lang\n"
					+ "  .hyphen full on|off        — a shorter piece may stay on the line\n"
					+ "The hyphen is drawn. Send, copy and suggestions keep the whole word.\n"
					+ "A word that fits on the next line still breaks if a piece fits here.\n"
					+ "Needs .wrap on. A password line does not break. Off by default.\n"
					+ "Options → Typing → Hyphenate long words?\n";
		}
		if (filter.equals("togglefullscreen")) {
			return "\n"
					+ Colorizer.getBrightCyanColor()
					+ "Children of .togglefullscreen:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .togglefullscreen  — flip fullscreen (every call, including off)\n";
		}
		if (filter.equals("fullscreen")) {
			return "\n"
					+ Colorizer.getBrightCyanColor()
					+ "Children of .fullscreen:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .fullscreen        — say whether the status bar is hidden\n"
					+ "  .fullscreen on|off|toggle\n"
					+ "  .togglefullscreen  — flip it every time it is typed\n";
		}
		if (filter.equals("closewindow")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .closewindow:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .closewindow       — leave the game window (no arguments)\n";
		}
		if (filter.equals("editpanel")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .editpanel:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .editpanel         — say whether the Edit tools strip is open\n"
					+ "  .editpanel on|off|toggle\n"
					+ "Two rows in landscape: .editrows on|off (off keeps one row).\n";
		}
		if (filter.equals("editrows")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .editrows:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .editrows          — say whether landscape uses two rows\n"
					+ "  .editrows on|off|toggle\n"
					+ "Off by default. Portrait stays one full-width row.\n"
					+ "Also: Options → Window → Input bar → Edit strip: two rows in landscape?\n";
		}
		if (filter.equals("editbutton")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .editbutton:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .editbutton        — say whether the Edit button is shown\n"
					+ "  .editbutton on|off\n";
		}
		if (filter.equals("sendbutton")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .sendbutton:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .sendbutton        — say whether the Send button is shown\n"
					+ "  .sendbutton on|off\n";
		}
		if (filter.equals("dobell")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .dobell:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .dobell            — reactions currently on in Options → Sound\n"
					+ "  .dobell vibrate [short|long|strong|burst]\n"
					+ "  .dobell alert      — on-screen bell icon now\n";
		}
		if (filter.equals("colordebug")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .colordebug:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .colordebug 0      — normal colour processing\n"
					+ "  .colordebug 1      — colour on, codes shown\n"
					+ "  .colordebug 2      — colour off, codes shown\n"
					+ "  .colordebug 3      — colour off, codes hidden\n";
		}
		if (filter.equals("debug")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .debug:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .debug                       — whether the label is on\n"
					+ "  .debug renderer              — same\n"
					+ "  .debug renderer show on|off|toggle\n"
					+ "Top-left of the text. typeset, tiles, and bake stay up, with a line count.\n"
					+ "tiles — stored line bitmap (glyphs not redrawn this frame)\n"
					+ "bake — glyphs drawn into a new line bitmap this frame\n"
					+ "typeset — glyphs drawn on the window canvas\n"
					+ "hw / sw — that window canvas\n"
					+ "Off until you turn it on. Not saved.\n";
		}
		if (filter.equals("clearbuttons")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .clearbuttons:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .clearbuttons      — clear all buttons (no arguments)\n";
		}
		if (filter.equals("heatmap")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .heatmap:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .heatmap           — show the heatmap and list the counts\n"
					+ "  .heatmap off       — hide it; counting continues\n"
					+ "  .heatmap reset     — forget this world's counts\n"
					+ "Tap, hold, each swipe direction, accordion open, accordion close,\n"
					+ "and a child tap are counted apart. Tiles are one white; a brighter\n"
					+ "tile has been used more. Counts are kept for this world.\n";
		}
		if (filter.equals("buttonopacity") || filter.equals("buttonsopacity")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .buttonopacity:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .buttonopacity 100      — force every tile fully opaque\n"
					+ "  .buttonopacity restore  — each button's own alpha again\n"
					+ "  .buttonopacity          — show whether an override is on\n"
					+ "Lasts until restore, including across .loadset (not saved).\n"
					+ ".buttonopacity 100 then .loadset tutorial keeps 100% until restore.\n"
					+ ".buttonsopacity is the same command.\n";
		}
		if (filter.equals("suggest") || filter.equals("suggestions")
				|| filter.equals("complete") || filter.equals("suggestion")) {
			return "\n"
					+ Colorizer.getBrightCyanColor()
					+ "Children of .suggest (.suggestions, .complete):"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .suggest on|off\n"
					+ "  .suggest lines N\n"
					+ "  .suggest show N\n"
					+ "  .suggest where floating|bar|list|off|next\n"
					+ "  .suggest order left|right — first chip on the left, or on the right\n"
					+ "                         (the bar and the floating window; .suggest order prints it)\n"
					+ "  .suggest ghost on|off\n"
					+ "  .suggest split on|off    (a space between dimmed suggestions)\n"
					+ "  .suggest caret on|off     (prefix chips in the middle of a line)\n"
					+ "  .suggest ghostlines N   (rows in the field, not how many offered)\n"
					+ "  .suggest opacity N\n"
					+ "  .suggest persist on|off\n"
					+ "  .suggest next on|off      (the word that followed a finished one)\n"
					+ "  .suggest phrases|plain|short|loose|typos on|off\n"
					+ "  .suggest skiphead|firstletter on|off\n"
					+ "  .suggest rank|pairs on|off\n"
					+ "  .suggest learned | clear\n"
					+ "  .suggest forget <word>     — drop that word from the bag\n"
					+ "  .suggest unpair <verb> <target> — drop that pairing only\n"
					+ "  .suggest weight <verb> <target> N — set that pairing's count\n"
					+ "  .suggest 1.." + CompleteCommand.MAX_PICK + "\n"
					+ "  .suggest status\n";
		}
		if (filter.equals("alias")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() 					+ "Children of .alias:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .alias             — how many are on (same as status)\n"
					+ "  .alias help        — the usage wall\n"
					+ "  .alias list\n"
					+ "  .alias status|state [name]\n"
					+ "  .alias on|off|toggle <name|plugin:name>\n"
					+ "  .alias all on|off\n";
		}
		if (filter.equals("last")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .last:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .last              — send the newest command again\n"
					+ "  .2last / .3last    — 2nd / 3rd newest\n"
					+ "  .Nlast             — Nth newest, N is 1 through history size (10–100)\n"
					+ "  .last input        — put the newest command in the bar; do not send it\n"
					+ "  .2last input       — the one before it, still only in the bar\n"
					+ "  You sent north, then look, then inventory.\n"
					+ "  .last sends inventory, .2last sends look, .3last sends north.\n"
					+ "  .last is not kept, so the next .last sends inventory again.\n"
					+ "  One space before input. .lastinput and last input are game lines.\n"
					+ "  2last with no dot is a game line.\n"
					+ "  .lastlist          — this world's list, in its own window\n"
					+ "  .lastlist float    — those commands as chips over the game\n"
					+ "  .lastfloat         — same as .lastlist float\n"
					+ "  .lastfloat off     — hide those chips\n"
					+ "  .lastfloat 5       — show the 5 newest (1–100)\n"
					+ "  .lastfloat length 12 — 12 characters of each (3–40)\n"
					+ "  .lastlist bar      — turn on the recent-command bar\n"
					+ "  .lastbar           — chips of those commands above the input row\n"
					+ "  .lastbar 5         — show the 5 newest and turn the bar on (1–100)\n"
					+ "  .lastbar length 12 — characters of each command (3–40)\n"
					+ "  .lastbar on|off    — show or hide that bar\n";
		}
		if (filter.equals("lastlist") || filter.equals("lastfloat")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .lastlist:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .lastlist          — recent commands, newest first\n"
					+ "  .lastlist list     — that window\n"
					+ "  .lastlist float    — chips over the game; a tap sends\n"
					+ "  .lastlist float off — hide those chips\n"
					+ "  .lastlist float on  — show them\n"
					+ "  .lastlist float toggle — flip them\n"
					+ "  .lastfloat         — same as .lastlist float\n"
					+ "  .lastfloat off     — hide those chips\n"
					+ "  .lastfloat 5       — show the 5 newest and turn the chips on\n"
					+ "  .lastlist float 5  — same. Count is 1 through 100\n"
					+ "  .lastfloat length 12 — characters of each command (3–40)\n"
					+ "  .lastlist float length 12 — same\n"
					+ "  Until you set them: 8 chips, 20 characters.\n"
					+ "  Each world keeps its own count and length.\n"
					+ "  Drag the grip to move the chips. A tap on the grip hides them.\n"
					+ "  Dropped near where they started, they follow the input bar again.\n"
					+ "  The backing is as see-through as floating suggestion chips.\n"
					+ "  .lastlist bar      — turn on the recent-command bar\n"
					+ "  .lastbar 5         — how many chips on that bar (1–100); turns it on\n"
					+ "  .lastbar length 12 — characters of each command on that bar (3–40)\n"
					+ "  .lastlist frame     — toggle the title, gear, and close\n"
					+ "  .lastlist frame off — hide that chrome\n"
					+ "  .lastlist frame on  — bring that chrome back\n"
					+ "  1 is what .last sends, 2 is what .2last sends.\n"
					+ "  Close hides it. The gear sets opacity, font size, lines between\n"
					+ "  commands, one line per command, tappable rows, a minimal frame,\n"
					+ "  keeping the window fill, and how many are kept.\n"
					+ "  Tappable: a tap sends that line. The row lights up, then lifting sends it.\n"
					+ "  Dragging up or down scrolls the list, the same as with Tappable off,\n"
					+ "  and that drag does not send.\n"
					+ "  A second finger outside the list cancels the tap.\n"
					+ "  Minimal frame hides the title, gear, and close.\n"
					+ "  Keep opacity leaves the fill and only hides that chrome.\n"
					+ "  Hold a row and tap the list with a second finger to show the frame.\n"
					+ "  The window stays where you left it for this world, including after\n"
					+ "  the app is killed. Back does not close it. Close does.\n"
					+ "  Options → Panes → Extra text windows → Recent commands…\n"
					+ "  Not an extra-text slot: game lines are not written here.\n"
					+ "  Each world has its own list.\n";
		}
		if (filter.equals("lastbar")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .lastbar:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .lastbar 5         — show the 5 newest and turn the bar on\n"
					+ "  .lastbar 10        — show 10. Count is 1 through 100\n"
					+ "  .lastbar on|off    — show or hide it. On uses the saved count\n"
					+ "  .lastbar           — count, length, order, and whether it is on\n"
					+ "  .lastbar length 12 — characters of each command (3–40)\n"
					+ "  .lastbar order left  — First on the left (the default)\n"
					+ "  .lastbar order right — First on the right, older numbers to its left\n"
					+ "  You sent north, then look, then inventory.\n"
					+ "  Left: 1 inventory, 2 look, 3 north.\n"
					+ "  Right: 3 north, 2 look, 1 inventory.\n"
					+ "  The number is the same as .Nlast. A tap sends that command.\n"
					+ "  Fewer stored than the count: you see what is there.\n"
					+ "  Each world keeps its own on/off, count, length, and order.\n"
					+ "  Options → Typing → Recent command bar…\n"
					+ "  Not sent to the world.\n";
		}
		if (filter.equals("kb") || filter.equals("keyboard")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() 					+ "Children of .kb:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .kb                — this usage; a word that is not an op does not change the bar\n"
					+ "  .kb insert <text>      — at caret, spaced like a tap ($word)\n"
					+ "  .kb insertliteral <text> — at caret, exactly as typed\n"
					+ "  .kb insertword <text>  — same as insert\n"
					+ "  .kb add|popup|flush|clear|close\n"
					+ "  .kb sel|copy|cut|paste\n"
					+ "  .kb start|end|stepf|stepb\n"
					+ "  .kb stepu|stepd        — older / newer command in history\n"
					+ "  .kb lineu|lined        — caret one line up / down, no history\n";
		}
		if (filter.equals("trigger")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() 					+ "Children of .trigger:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .trigger           — how many are on (same as status)\n"
					+ "  .trigger help      — the usage wall\n"
					+ "  .trigger on|off|toggle <name|plugin:name>\n"
					+ "  .trigger status [name]\n"
					+ "  .trigger group on|off|toggle <group>\n"
					+ "  .trigger all on|off\n"
					+ "  .trigger plugin <plugin> all on|off\n";
		}
		if (filter.equals("timer")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .timer:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .timer play|pause|reset|stop <name> [silent]\n"
					+ "  .timer info <name>          status in the game window\n"
					+ "  .timer dump <name>          same as info\n"
					+ "  .timer duration <name>      same as info\n"
					+ "  .timer duration <name> <seconds> [silent]   set length and restart from full\n"
					+ "  .timer duration <name> 50s|2m|1h [silent]   add to remaining (keep running)\n"
					+ "  .timer duration <name> -50s|-2m [silent]    subtract from remaining (floor 0)\n"
					+ "  .timer dump / .timer list / .timer info   every timer, in the window\n";
		}
		if (filter.equals("wait")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .wait:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .wait 5s | #wait 5m10s | .wait 500ms\n"
					+ "  Units h, m, s, ms in any order (5s5m is the same as 5m5s).\n"
					+ "  A bare number is seconds. Max 1h. .wait stop / #wait 0 cancels.\n"
					+ "  .wait show / .wait info lists queued waits and when they fire.\n"
					+ "  .wait change 1 60s retargets that row from now (max 1h).\n"
					+ "  Only the rest of this line waits: north;.wait 2s;south\n"
					+ "  #5/1s north — north five times, one second apart\n"
					+ "  The gap is one word (1s, 500ms, 5, 5m10s). Max 1h.\n"
					+ "  #5 north is still all five at once. .wait stop cancels the rest.\n";
		}
		if (filter.equals("map")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() 					+ "Children of .map:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .map               — this help; .map open shows the map\n"
					+ "  .map open|close|toggle | record|rec — record says on or off\n"
					+ "  .map follow on|off|toggle | level list|prev|next|set …\n"
					+ "  .map find|search|path|goto|go <query>\n"
					+ "  .map title|note|locktitle|lockposition|relayout|tidy …\n"
					+ "  (type .map alone for the full list)\n";
		}
		if (filter.equals("gmcp")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() 					+ "Children of .gmcp:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .gmcp              — status (same as .gmcp status)\n"
					+ "  .gmcp help         — the usage wall\n"
					+ "  .gmcp ask|handshake | modules | enable|disable\n"
					+ "  .gmcp renegotiate | status | sniff [on|off|tail N]\n"
					+ "  .gmcp feed [on|off] | version | supports | dump | send\n";
		}
		if (filter.equals("mcp")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() 					+ "Children of .mcp:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .mcp               — status (same as .mcp status)\n"
					+ "  .mcp help          — the usage wall\n"
					+ "  .mcp ask|status|packages|vitals|cords\n"
					+ "  .mcp enable|disable <pkg…> | renegotiate\n"
					+ "  .mcp sniff|feed|dump|send|ping|client\n"
					+ "  .mcp cord open|close|send …\n";
		}
		if (filter.equals("window")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() 					+ "Children of .window:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .window            — this help; .window show opens a slot\n"
					+ "  .window list\n"
					+ "  .window show|hide|clear <slot>\n"
					+ "  .window create <slot> [title…]\n"
					+ "  .window destroy <slot>\n"
					+ "  .window opacity <slot> [40-100]\n"
					+ "  .window font <slot> [6-96|+1|-1|default]\n"
					+ "  .window <slot> font [6-96|+1|-1|default]\n";
		}
		if (filter.equals("widget") || filter.equals("gauge")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .widget:"
					+ Colorizer.getWhiteColor() + "\n"
					+ WidgetCommandParser.usage();
		}
		if (filter.equals("frame")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .frame:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .frame list\n"
					+ "  .frame close <id>|all\n"
					+ "  .frame reopen|open <id>\n";
		}
		if (filter.equals("probe")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .probe:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .probe — this list of probes\n"
					+ "  .probe connection on|off | report | reset\n"
					+ "  .probe bleed on|off | report | reset\n"
					+ "  .probe truecolor | color — 24-bit sample in this window\n"
					+ "  .probe osc8 — OSC 8 sample (tap the marked words)\n"
					+ "  .probe mxp — MXP SEND/colour sample (tap the marked words)\n"
					+ "  .probe protocols — same as .protocols\n"
					+ "  .probe sensors | sensors state\n"
					+ "  .probe sensors shake|light [seconds]\n"
					+ "  .probe lines on|off | report | reset — optional chunk measurement\n";
		}
		if (filter.equals("sensor")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .sensor:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .sensor | .sensor list — what is set up\n"
					+ "  .sensor caps — which hardware provides each reading\n"
					+ "  .sensor <gesture> <command> — wire a reading to a command\n"
					+ "  .sensor <gesture> on|off | fire <gesture>\n"
					+ "  .sensor all on|off — every reading in this world\n"
					+ "  .sensor pat:lr look — left then right; the trigger stores !pat:lr\n"
					+ "  .sensor slash look — a shake you recorded, after My shakes\n"
					+ "  .sensor watch on|off — keep device.* up to date\n"
					+ "  .sensor examples | help\n"
					+ "  .sensor threshold shake|light|battery …\n"
					+ "A name already used by a saved shake or a built-in reading is refused.\n"
					+ "landscape/portrait: the orientation already showing when you bind is not a fire.\n";
		}
		if (filter.equals("sound")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .sound:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .sound stream media|notification|alarm\n"
					+ "  .sound warn on|off\n"
					+ "  .sound status\n";
		}
		if (filter.equals("prompt")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .prompt:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .prompt on|off     — pin the prompt above the input bar\n"
					+ "  .prompt | status   — say which it is, and prompts seen\n";
		}
		if (filter.equals("tapmenu")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .tapmenu:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .tapmenu opacity N — how solid (20-100)\n"
					+ "  .tapmenu | status  — what it is set to now\n";
		}
		if (filter.equals("font")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .font:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .font              — say the current size\n"
					+ "  .font +N | -N | <size> | default\n";
		}
		if (filter.equals("options")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .options:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .options           — none; it opens the Options screen\n"
					+ "  The settings file itself is .settings\n";
		}
		if (filter.equals("settings")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .settings:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .settings | status — what is on disk, and the kept copy\n"
					+ "  .settings backup   — save now and refresh the kept copy\n"
					+ "  .settings restore  — put the kept copy back and reload\n";
		}
		if (filter.equals("msdp")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .msdp:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .msdp              — dump the MSDP variable cache\n"
					+ "  .msdp list [COMMANDS]\n"
					+ "  .msdp send|report|unreport <var>\n"
					+ "  .msdp reset <group>\n";
		}
		if (filter.equals("mssp")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .mssp:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .mssp              — dump the MSSP server status cache\n"
					+ "  (MSSP is one-way; no send/report subcommands)\n";
		}
		if (filter.equals("loadset")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .loadset:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .loadset           — usage; name a set to load it\n"
					+ "  .loadset <name>    — load that button set\n";
		}
		if (filter.equals("osc8")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .osc8:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .osc8              — on or off\n"
					+ "  .osc8 on|off       — words the game marks (OSC 8)\n"
					+ "  send: / prompt:    — tap types a command / fills the input bar\n"
					+ "  .probe osc8        — dump a tappable sample here\n";
		}
		if (filter.equals("protocols") || filter.equals("protocol")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .protocols:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .protocols         — what this world offered vs what is on\n"
					+ "  .protocols enable  — turn on offered-but-off switches\n"
					+ "  .probe protocols   — same report\n";
		}
		if (filter.equals("mxp")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .mxp:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .mxp               — status\n"
					+ "  .mxp on|off        — MUD eXtension Protocol (option 91)\n"
					+ "  .probe mxp         — dump a tappable sample here\n";
		}
		if (filter.equals("tutorial")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .tutorial:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .tutorial              — help (works in any world)\n"
					+ "  .tutorial start|next|prev|topics | <name>\n"
					+ "  .tips on|always|off    — reminders while you play\n";
		}
		if (filter.equals("tips")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .tips:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .tips              — on, always, or off\n"
					+ "  .tips on           — once per command this session\n"
					+ "  .tips always       — every time\n"
					+ "  .tips off          — stop\n"
					+ "  Then type .help or .osc8 — not .alias\n";
		}
		if (filter.equals("pick")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .pick:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .pick / .pick once   bar prefix, one word, then off\n"
					+ "  .pick hold / .pick on  until .pick off (second hold turns it off)\n"
					+ "  .pick tap            same as once\n"
					+ "  .pick button         swipe a tile, keep holding, slide onto a word\n"
					+ "  .pick button-double  hold a tile, tap a word with the other finger\n"
					+ "  .pick insert         once, but the line goes in the bar\n"
					+ "  .pick hold insert    each next word is added in the bar\n"
					+ "  .pick button insert\n"
					+ "  .pick button-double insert\n"
					+ "  insert sends nothing. You send the line yourself.\n"
					+ "  .pick loupe          print size and zoom; also size N / zoom N / default\n"
					+ "  .pick loupe size N   magnifier size 50–200 (118 default)\n"
					+ "  .pick loupe zoom N   magnifier zoom 150–350 (200 = 2×)\n"
					+ "  .pick off\n"
					+ "Bar: type .pick, then put fix  in the bar, then tap a word.\n"
					+ "The game receives fix helmet and the bar still holds fix .\n"
					+ "Or put fix $1 helmet in the bar and pick iron → fix iron helmet.\n"
					+ "$1, $0 and $word are the picked word. No slot: the word is appended.\n"
					+ "Empty / a . command in the bar is not a prefix; pick stays on.\n"
					+ "Pad stays on screen. .pick off, or the same sticky command again.\n"
					+ "During hold, one finger picks; a second finger cancels that pick\n"
					+ "so you can drag to scroll. Two fingers with pick off still copy.\n"
					+ "Loupe: Options → Window → Text → Pick loupe size / zoom, or .pick loupe.\n";
		}
		if (filter.equals("copy")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .copy:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .copy / .copy loupe  print size and zoom; also size N / zoom N / default\n"
					+ "  .copy loupe size N   magnifier size 50–200 (118 default)\n"
					+ "  .copy loupe zoom N   magnifier zoom 150–350 (200 = 2×)\n"
					+ "  .copy loupe default\n"
					+ "Two-finger copy widget: copy, swap ends, close, and new trigger.\n"
					+ "New trigger opens the editor with the selection as a literal pattern.\n"
					+ "Also Options → Window → Text → Copy loupe size / zoom.\n";
		}
		if (filter.equals("grabber")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .grabber:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .grabber / .grabber once   until the first copy, then off\n"
					+ "  .grabber hold / .grabber on  until .grabber off\n"
					+ "  .grabber tap            one tap; list is tappable, then off\n"
					+ "  .grabber off\n"
					+ "Tap a glyph: layer list. Drag still scrolls the feed.\n"
					+ "The list is titled Grabber. Copy puts the ticked values on the clipboard.\n"
					+ "New trigger opens the editor with those layers already Required. Tick\n"
					+ "Exact recipe vs Looks the same, ALL vs ANY, extra attributes OK vs none,\n"
					+ "on the list itself.\n"
					+ "A Color action you painted is not the world's style and is skipped.\n"
					+ "Blank pattern: $0 and $1 are the styled run, so Ack $1 sends that phrase.\n"
					+ "Keep .colordebug to dump CSI in the draw path; grabber does not replace it.\n";
		}
		if (filter.equals("search")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .search:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .search <text> | 'multi word' | \"…\"\n"
					+ "  .search next|n | prev|previous|p\n"
					+ "  .search close|hide|clear\n"
					+ "  .search logs              — browse this world's session log files\n"
					+ "  .search logs 7 goblin     — window, then last 7 days of files\n"
					+ "  .search logs 7 'multi word'\n"
					+ "  .search logs 0 goblin     — window plus every saved file for this world\n"
					+ "  .search 'logs'            — find the word logs in the window\n"
					+ "  .search 14:32 | 18 Aug  — when Scroll dates is on\n"
					+ "Logs live in the folder Options → Connection → Session Log Directory\n"
					+ "(blank = /BlowTorch/session_logs/) as {world}_{yyyy-MM-dd}.txt\n"
					+ "(one file per world per local day; older _date_time files still list).\n"
					+ "⋮ → Session logs: pick dates and tap Load (large folders can take a while).\n"
					+ "The box filters names; Search finds text in the files still listed and\n"
					+ "stays on that list (hit counts). Tap a file for ‹ › in that file only.\n"
					+ "✕ from a file returns to the list; ✕ on the list clears the box.\n"
					+ "N is last N days including today; 0 = all files. Enable Log Session\n"
					+ "to File? or there is nothing to search.\n";
		}
		if (filter.equals("chat")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .chat:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .chat                  toggle the drawer\n"
					+ "  .chat open             open; already open stays open\n"
					+ "  .chat close | hide     close; already closed stays closed\n"
					+ "  .chat <name>           open that conversation (id or title)\n"
					+ "  .chat help\n"
					+ "  Also: overflow ⋮ → Chat\n"
					+ "Example: .chat ooc if the list says ooc (case-insensitive).\n"
					+ "⚙: tap My lines or Reply for the submenu (several My lines forms; ? in that dialog).\n"
					+ "Reply ($text is the reply box). Notify: Tells / Channels / Auction / Other\n"
					+ "(four Android channels, not one per name; tune sound in Android Settings).\n"
					+ "After this update, re-tune: chat left the alerts channel (bell stays on alerts).\n"
					+ "From/To/7d/All live behind ⚙.\n"
					+ "Find in thread stays visible.\n"
					+ "Save writes My lines + reply (and a matching Send to thread trigger).\n"
					+ "Delete conversation (confirm) removes messages; it does not delete the trigger.\n"
					+ "A lone $1 in Reply is treated as $text (C $1). tell $1 $text is the trigger form;\n"
					+ "Send wants tell Bob $text. Send refuses leftover $1/$text.\n"
					+ "Options → Chat: unread disc on ⋮, game-window line, Android notify (off by default),\n"
					+ "keep at most N messages (default 4000; 0 still caps at 50000).\n";
		}
		if (filter.equals("layoutwizard")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .layoutwizard:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .layoutwizard       — open the button layout wizard\n"
					+ "Also: Options → Button → Load button set from wizard.\n"
					+ "The offline Starter Tutorial keeps its own pad and refuses this.\n";
		}
		return null;
	}

	/** NAWS columns when they leave room for a description; otherwise 48. Missing connection does not throw. */
	static int wrapWidth(final Connection c) {
		int reported = 0;
		if (c != null) {
			Processor p = c.getProcessor();
			if (p != null) {
				OptionNegotiator n = p.getOptionHandler();
				if (n != null) {
					reported = n.getColumns();
				}
			}
		}
		return HelpColumn.wrapWidth(reported);
	}

	/** Names passed to {@code cmd}, as stored. */
	static List<String> registeredNames() {
		return new ArrayList<String>(WHAT.keySet());
	}
}
