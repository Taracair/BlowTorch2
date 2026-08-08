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
		cmd("echo", "Playing", "print a line locally, without sending it");
		cmd("run", "Playing", "walk a speedwalk string, like 4n2e");
		cmd("disconnect", "Playing", "close the connection");
		cmd("reconnect", "Playing", "close it and open it again");
		cmd("switch", "Playing", "change to another world");
		cmd("settings", "Playing", "open the options screen");
		cmd("note", "Playing", "keep a note against this world");

		cmd("font", "The window", "game font size; +n and -n step from where you are");
		cmd("width", "The window", "text canvas width as a percent of the screen");
		cmd("wrap", "The window", "let the input bar grow to more than one line");
		cmd("togglefullscreen", "The window", "hide or show the status bar");
		cmd("window", "The window", "open, close and address extra text windows");
		cmd("closewindow", "The window", "close one of them");
		cmd("search", "The window", "find text in the scrollback");
		cmd("tapmenu", "The window", "how solid the menu a tapped word opens is");
		cmd("frame", "The window", "the drawn frame some worlds ask for");

		cmd("keyboard", "Input and suggestions",
				"send a key, or step through command history (.kb for short)");
		cmd("complete", "Input and suggestions",
				"the older name for .suggest; still works");
		cmd("suggest", "Input and suggestions",
				"suggest words the game just used, and everything about that bar");
		cmd("suggestions", "Input and suggestions", "the same command, spelled out");
		cmd("prompt", "Input and suggestions", "pin the world's prompt above the input bar");
		cmd("editpanel", "Input and suggestions", "show or hide the editing strip");
		cmd("editbutton", "Input and suggestions", "show or hide the Edit button");
		cmd("sendbutton", "Input and suggestions", "show or hide the Send button");

		cmd("trigger", "Triggers and scripts", "list, enable and disable triggers");
		cmd("alias", "Triggers and scripts", "list, enable and disable aliases");
		cmd("timer", "Triggers and scripts", "list, enable and disable timers");
		cmd("sound", "Triggers and scripts",
				"which volume a trigger's sound uses, and warning when it is off");
		cmd("dobell", "Triggers and scripts", "fire the bell reaction now");
		cmd("probe", "Triggers and scripts",
				"measure how the world splits its text across packets; "
				+ ".probe sensors for what this phone can feel");
		cmd("colordebug", "Triggers and scripts", "show the colour codes in a line");

		cmd("gmcp", "The world and its protocols", "what the world is sending over GMCP");
		cmd("mcp", "The world and its protocols", "MCP packages and negotiation");
		cmd("msdp", "The world and its protocols", "MSDP variables");
		cmd("mssp", "The world and its protocols", "what the world says about itself");

		cmd("map", "The map", "the mapper: recording, walking, rooms and exits");

		cmd("loadset", "Buttons", "load a button set");
		cmd("clearbuttons", "Buttons", "take the buttons away until the next set");
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
		c.sendDataToWindow(out.toString());
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
