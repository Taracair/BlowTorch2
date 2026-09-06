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
		cmd("help", "Other", "this list; .help word shows only matching commands");
		cmd("echo", "Playing", "show or hide what you type when the server has "
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
		cmd("dimrepeat", "The window", "dim a long line that comes back identical");
		cmd("light", "The window", "light paper and dark ink; .light on|off|1-5");
		cmd("when", "The window", "day/time to the left of ⋮ in history; .when opacity N");
		cmd("osc8", "The window", "words the game marks (OSC 8); send:/prompt:/http; .osc8 on|off");
		cmd("wrap", "The window", "let the input bar grow to more than one line");
		cmd("togglefullscreen", "The window", "hide or show the status bar");
		cmd("window", "The window", "extra text windows; .window show|hide|create|destroy");
		cmd("widget", "The window", "HP/mana/timer gauges over the game (.widget add|source|set|…)");
		cmd("gauge", "The window", "same as .widget");
		cmd("closewindow", "The window", "leave the game window (dirty exit)");
		cmd("search", "The window", "find text in the scrollback or old session logs");
		cmd("chat", "The window", "left-hand chat drawer; .chat open|close|<name>");
		cmd("tapmenu", "The window", "how solid the menu a tapped word opens is");
		cmd("frame", "The window", "frames a server opens; .frame list|close|reopen");

		cmd("keyboard", "Input and suggestions",
				"send a key, or step through command history (.kb for short)");
		cmd("complete", "Input and suggestions",
				"same as .suggest (older name); also .suggestions");
		cmd("suggest", "Input and suggestions",
				"words the game just used; also .suggestions and .complete");
		cmd("suggestions", "Input and suggestions",
				"same as .suggest (alias); also .complete");
		cmd("prompt", "Input and suggestions", "pin the world's prompt above the input bar");
		cmd("editpanel", "Input and suggestions", "show or hide the editing strip");
		cmd("editbutton", "Input and suggestions", "show or hide the Edit button");
		cmd("sendbutton", "Input and suggestions", "show or hide the Send button");

		cmd("trigger", "Triggers and scripts", "enable and disable triggers (.trigger status, not list)");
		cmd("alias", "Triggers and scripts", "list, enable and disable aliases");
		cmd("timer", "Triggers and scripts", "play, pause, info, dump, duration");
		cmd("wait", "Triggers and scripts",
				"pause the rest of this line (.wait 5s / #wait 5m10s); .wait stop cancels");
		cmd("sound", "Triggers and scripts",
				"which volume a trigger's sound uses, and warning when it is off");
		cmd("dobell", "Triggers and scripts",
				"fire the bell reaction now; .dobell vibrate / .dobell alert ignore Options");
		cmd("probe", "Triggers and scripts",
				"measure how the world splits its text across packets; "
				+ ".probe bleed records colour-trigger restores; "
				+ ".probe truecolor dumps a 24-bit sample; "
				+ ".probe osc8 dumps tappable OSC 8 samples; "
				+ ".probe mxp dumps MXP SEND/colour samples; "
				+ ".probe protocols is the same as .protocols; "
				+ ".probe sensors for what this phone can feel");
		cmd("sensor", "Triggers and scripts",
				"what this phone can measure, and what triggers do with it");
		cmd("colordebug", "Triggers and scripts", "show the colour codes in a line");
		cmd("grabber", "Triggers and scripts",
				"inspect colour/style under a finger; copy layers or open a trigger");

		cmd("gmcp", "The world and its protocols", "what the world is sending over GMCP");
		cmd("mcp", "The world and its protocols", "MCP packages and negotiation");
		cmd("msdp", "The world and its protocols", "MSDP variables");
		cmd("mssp", "The world and its protocols", "what the world says about itself");
		cmd("mxp", "The world and its protocols", "MXP SEND/colours/SOUND; .mxp on|off");
		cmd("protocols", "The world and its protocols",
				"what this world offered vs what is on; .protocols enable");

		cmd("map", "The map", "the mapper: recording, walking, rooms and exits");

		cmd("loadset", "Buttons", "load a button set");
		cmd("clearbuttons", "Buttons", "take the buttons away until the next set");
		cmd("buttonopacity", "Buttons",
				"force every tile's alpha (.buttonopacity 100) until .buttonopacity restore");
		cmd("buttonsopacity", "Buttons", "same as .buttonopacity");
	}

	@Override
	public Object execute(Object o, Connection c) {
		String filter = o == null ? "" : ((String) o).trim().toLowerCase(Locale.US);
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
			if (filter.length() > 0 && !name.contains(filter)) {
				continue;
			}
			String what = WHAT.get(name);
			String section = WHERE.get(name);
			if (section == null || bySection.get(section) == null) {
				section = "Other";
			}
			bySection.get(section).add(pad("." + name) + " "
					+ (what == null ? "(no description yet)" : what));
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
				out.append("  ").append(row).append("\n");
				shown++;
			}
		}
		if (shown == 0) {
			out.append("No command matches \"").append(filter).append("\".\n");
		} else if (filter.length() == 0) {
			out.append("\nMost take their own arguments — type the command on its"
					+ " own to see them. The manual has the long version.\n");
		}
		String sub = subcommandHelp(filter);
		if (sub != null) {
			out.append(sub);
		}
		c.sendDataToWindow(out.toString());
		return null;
	}

	/** When the filter names one command family, list its subcommands. */
	private static String subcommandHelp(final String filter) {
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
					+ "Also: Options → Window → Light theme? / Light paper shade (1–5)\n"
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
		if (filter.equals("wrap")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .wrap:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .wrap              — say whether the input bar may grow\n"
					+ "  .wrap on|off\n";
		}
		if (filter.equals("togglefullscreen")) {
			return "\n"
					+ Colorizer.getBrightCyanColor()
					+ "Children of .togglefullscreen:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .togglefullscreen  — flip fullscreen (no arguments)\n";
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
					+ "  .editpanel         — toggle the Edit tools strip\n"
					+ "  .editpanel on|off\n";
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
					+ "  .dobell            — reactions currently on in Options → Bell\n"
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
		if (filter.equals("clearbuttons")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .clearbuttons:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .clearbuttons      — clear all buttons (no arguments)\n";
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
					+ "  .suggest where floating|bar|off|next\n"
					+ "  .suggest ghost on|off\n"
					+ "  .suggest ghostlines N   (rows in the field, not how many offered)\n"
					+ "  .suggest opacity N\n"
					+ "  .suggest persist on|off\n"
					+ "  .suggest phrases|plain|short|loose on|off\n"
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
					+ Colorizer.getBrightCyanColor() + "Children of .alias:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .alias list\n"
					+ "  .alias status|state [name]\n"
					+ "  .alias on|off|toggle <name|plugin:name>\n"
					+ "  .alias all on|off\n";
		}
		if (filter.equals("kb") || filter.equals("keyboard")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .kb:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .kb insert <text>      — at caret, spaced like a tap ($word)\n"
					+ "  .kb insertliteral <text> — at caret, exactly as typed\n"
					+ "  .kb insertword <text>  — same as insert\n"
					+ "  .kb add|popup|flush|clear|close\n"
					+ "  .kb sel|copy|cut|paste\n"
					+ "  .kb start|end|stepf|stepb|stepu|stepd|lineu|lined\n";
		}
		if (filter.equals("trigger")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .trigger:"
					+ Colorizer.getWhiteColor() + "\n"
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
					+ "  .timer info <name> [window]      toast, or game window with window\n"
					+ "  .timer dump <name>               same as info … window\n"
					+ "  .timer duration <name>            status (same as info)\n"
					+ "  .timer duration <name> <seconds> [silent]\n"
					+ "  .timer dump / .timer list / .timer info   every timer, in the window\n";
		}
		if (filter.equals("wait")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .wait:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .wait 5s | #wait 5m10s | .wait 500ms\n"
					+ "  Units h, m, s, ms in any order (5s5m is the same as 5m5s).\n"
					+ "  A bare number is seconds. Max 1h. .wait stop / #wait 0 cancels.\n"
					+ "  Only the rest of this line waits: north;.wait 2s;south\n";
		}
		if (filter.equals("map")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .map:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .map open|close|toggle | record|rec on|off|toggle\n"
					+ "  .map follow on|off|toggle | level list|prev|next|set …\n"
					+ "  .map find|search|path|goto|go <query>\n"
					+ "  .map title|note|locktitle|lockposition|relayout|tidy …\n"
					+ "  (type .map alone for the full list)\n";
		}
		if (filter.equals("gmcp")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .gmcp:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .gmcp ask|handshake | modules | enable|disable\n"
					+ "  .gmcp renegotiate | status | sniff [on|off|tail N]\n"
					+ "  .gmcp feed [on|off] | version | supports | dump | send\n";
		}
		if (filter.equals("mcp")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .mcp:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .mcp ask|status|packages|vitals|cords\n"
					+ "  .mcp enable|disable <pkg…> | renegotiate\n"
					+ "  .mcp sniff|feed|dump|send|ping|client\n"
					+ "  .mcp cord open|close|send …\n";
		}
		if (filter.equals("window")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .window:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .window list\n"
					+ "  .window show|hide|clear <slot>\n"
					+ "  .window create <slot> [title…]\n"
					+ "  .window destroy <slot>\n"
					+ "  .window opacity <slot> [40-100]\n";
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
					+ "  .probe lines on|off | report | reset\n"
					+ "  .probe bleed on|off | report | reset\n"
					+ "  .probe truecolor | color — 24-bit sample in this window\n"
					+ "  .probe osc8 — OSC 8 sample (tap the marked words)\n"
					+ "  .probe mxp — MXP SEND/colour sample (tap the marked words)\n"
					+ "  .probe protocols — same as .protocols\n"
					+ "  .probe sensors | sensors state\n"
					+ "  .probe sensors shake|light [seconds]\n";
		}
		if (filter.equals("sensor")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .sensor:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .sensor | .sensor list — what is set up\n"
					+ "  .sensor caps — which hardware provides each reading\n"
					+ "  .sensor <gesture> <command> — wire a reading to a command\n"
					+ "  .sensor <gesture> on|off | fire <gesture>\n"
					+ "  .sensor threshold shake|light|battery … | help | examples\n"
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
					+ "  .loadset <name>    — argument is a button-set name\n";
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
		if (filter.equals("grabber")) {
			return "\n"
					+ Colorizer.getBrightCyanColor() + "Children of .grabber:"
					+ Colorizer.getWhiteColor() + "\n"
					+ "  .grabber / .grabber once   until the first copy, then off\n"
					+ "  .grabber hold / .grabber on  until .grabber off\n"
					+ "  .grabber tap            one drag; list is tappable on release, then off\n"
					+ "  .grabber off\n"
					+ "Drag a glyph: live layer list beside the finger. Release: tick layers.\n"
					+ "Copy puts the ticked values on the clipboard. New trigger opens the\n"
					+ "editor with those layers already Required. Tick Exact recipe vs Looks\n"
					+ "the same, ALL vs ANY, extra attributes OK vs none, on the list itself.\n"
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
					+ "Logs live in the folder Options → Service → Session Log Directory\n"
					+ "(blank = /BlowTorch/session_logs/) as {world}_{date}_{time}.txt.\n"
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
					+ "  .chat / .chat open     open the drawer (toggles if already open)\n"
					+ "  .chat close | hide     same toggle; or tap ✕ / the dim area\n"
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
		return null;
	}

	/** Line up the descriptions without needing a monospace assumption to hold. */
	private static String pad(final String name) {
		StringBuilder b = new StringBuilder(name);
		while (b.length() < 18) {
			b.append(' ');
		}
		return b.toString();
	}
}
