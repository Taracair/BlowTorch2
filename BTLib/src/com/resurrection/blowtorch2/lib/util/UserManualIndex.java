package com.resurrection.blowtorch2.lib.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Splits the packaged user manual on {@code ##} headings and maps them onto
 * Help-dialog categories. Android-free so the split can be JVM-tested.
 */
public final class UserManualIndex {

	public static final String CATEGORY_START = "Before you start";
	public static final String CATEGORY_PLAYING = "Playing";
	public static final String CATEGORY_INPUT = "Input and suggestions";
	public static final String CATEGORY_TRIGGERS = "Triggers and scripts";
	public static final String CATEGORY_RECIPES = "Recipes";
	public static final String CATEGORY_COMMANDS = "Built-in commands";
	public static final String CATEGORY_WINDOW = "The window";
	public static final String CATEGORY_BUTTONS = "Buttons";
	public static final String CATEGORY_MAP = "The map";
	public static final String CATEGORY_PROTOCOLS = "The world and its protocols";
	public static final String CATEGORY_OTHER = "Other";

	/** Category headers in the order the Help dialog paints them. */
	public static final String[] CATEGORY_ORDER = {
			CATEGORY_START,
			CATEGORY_PLAYING,
			CATEGORY_INPUT,
			CATEGORY_TRIGGERS,
			CATEGORY_RECIPES,
			CATEGORY_COMMANDS,
			CATEGORY_WINDOW,
			CATEGORY_BUTTONS,
			CATEGORY_MAP,
			CATEGORY_PROTOCOLS,
			CATEGORY_OTHER,
	};

	public static final class Section {
		public final String category;
		public final String title;
		public final String body;

		public Section(final String category, final String title, final String body) {
			this.category = category;
			this.title = title;
			this.body = body == null ? "" : body;
		}
	}

	public static final class Hit {
		public final int start;
		public final int end;

		public Hit(final int start, final int end) {
			this.start = start;
			this.end = end;
		}
	}

	private static final Map<String, String> TITLE_TO_CATEGORY;

	static {
		LinkedHashMap<String, String> m = new LinkedHashMap<String, String>();
		m.put("Before you start", CATEGORY_START);
		m.put("Encrypted connections (TLS)", CATEGORY_PLAYING);
		m.put("Dot commands", CATEGORY_PLAYING);
		m.put("Repeating a command (`#5 north`)", CATEGORY_PLAYING);
		m.put("Waiting between commands (`.wait` / `#wait`)", CATEGORY_PLAYING);
		m.put("Passwords are hidden while the MUD asks for them", CATEGORY_PLAYING);
		m.put("Plugin commands (when loaded)", CATEGORY_PLAYING);
		m.put("Session overflow menu", CATEGORY_PLAYING);
		m.put("Suggestions (`.suggest on`)", CATEGORY_INPUT);
		m.put("Prompt on its own bar (`.prompt on`)", CATEGORY_INPUT);
		m.put("Lowercase start of sent commands", CATEGORY_INPUT);
		m.put("Aliases and triggers (patterns / `$1`)", CATEGORY_TRIGGERS);
		m.put("Recipes", CATEGORY_RECIPES);
		m.put("Built-in commands", CATEGORY_COMMANDS);
		m.put("Chat drawer", CATEGORY_WINDOW);
		m.put("Copy text from the game window", CATEGORY_WINDOW);
		m.put("Font size", CATEGORY_WINDOW);
		m.put("Colours the world sends", CATEGORY_WINDOW);
		m.put("Dim repeated lines", CATEGORY_WINDOW);
		m.put("Light theme", CATEGORY_WINDOW);
		m.put("Scroll dates", CATEGORY_WINDOW);
		m.put("Newest text at top", CATEGORY_WINDOW);
		m.put("Extra text windows", CATEGORY_WINDOW);
		m.put("Overlay gauges (`.widget` / `.gauge`)", CATEGORY_WINDOW);
		m.put("On-screen buttons: swipe + accordion", CATEGORY_BUTTONS);
		m.put("Super-buttons (buttons on top of the keyboard)", CATEGORY_BUTTONS);
		m.put("Mapper", CATEGORY_MAP);
		m.put("OSC 8 hyperlinks", CATEGORY_PROTOCOLS);
		m.put("MXP (clickable SEND)", CATEGORY_PROTOCOLS);
		m.put("GMCP (short)", CATEGORY_PROTOCOLS);
		m.put("Frames a server opens (`mudstd.frame`)", CATEGORY_PROTOCOLS);
		m.put("MCP (short)", CATEGORY_PROTOCOLS);
		m.put("Related docs", CATEGORY_OTHER);
		TITLE_TO_CATEGORY = Collections.unmodifiableMap(m);
	}

	private UserManualIndex() {
	}

	public static List<Section> parse(final String raw) {
		ArrayList<Section> out = new ArrayList<Section>();
		if (raw == null || raw.length() == 0) {
			return out;
		}
		String[] lines = raw.split("\n", -1);
		String title = null;
		StringBuilder body = new StringBuilder();
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i];
			if (line.startsWith("## ") && !line.startsWith("### ")) {
				flush(out, title, body);
				title = line.substring(3).trim();
				body.setLength(0);
			} else if (title != null) {
				if (body.length() > 0) {
					body.append('\n');
				}
				body.append(line);
			}
		}
		flush(out, title, body);
		return out;
	}

	/**
	 * Query shorter than 2 characters returns every section. Otherwise a section
	 * stays if the query appears in its title or body, case-insensitive.
	 */
	public static List<Section> filter(final List<Section> sections, final String query) {
		if (sections == null || sections.isEmpty()) {
			return new ArrayList<Section>();
		}
		String q = query == null ? "" : query.trim();
		if (q.length() < 2) {
			return new ArrayList<Section>(sections);
		}
		String needle = q.toLowerCase(Locale.US);
		ArrayList<Section> out = new ArrayList<Section>();
		for (int i = 0; i < sections.size(); i++) {
			Section s = sections.get(i);
			if (s.title.toLowerCase(Locale.US).contains(needle)
					|| s.body.toLowerCase(Locale.US).contains(needle)) {
				out.add(s);
			}
		}
		return out;
	}

	public static List<Hit> highlightRanges(final String text, final String query) {
		ArrayList<Hit> hits = new ArrayList<Hit>();
		if (text == null || query == null) {
			return hits;
		}
		String q = query.trim();
		if (q.length() < 2) {
			return hits;
		}
		String hay = text.toLowerCase(Locale.US);
		String needle = q.toLowerCase(Locale.US);
		int from = 0;
		while (from <= hay.length() - needle.length()) {
			int at = hay.indexOf(needle, from);
			if (at < 0) {
				break;
			}
			hits.add(new Hit(at, at + needle.length()));
			from = at + needle.length();
		}
		return hits;
	}

	public static String categoryForTitle(final String title) {
		if (title == null) {
			return CATEGORY_OTHER;
		}
		String mapped = TITLE_TO_CATEGORY.get(title);
		return mapped == null ? CATEGORY_OTHER : mapped;
	}

	private static void flush(final List<Section> out, final String title,
			final StringBuilder body) {
		if (title == null || title.length() == 0) {
			return;
		}
		out.add(new Section(categoryForTitle(title), title, body.toString().trim()));
	}
}
