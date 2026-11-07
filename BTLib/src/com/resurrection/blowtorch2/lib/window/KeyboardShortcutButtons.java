package com.resurrection.blowtorch2.lib.window;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Button commands copied into a shortcut. Swipes and flip are not in this list. */
public final class KeyboardShortcutButtons {

	private KeyboardShortcutButtons() {
	}

	public static final class Button {
		public final String label;
		public final String name;
		public final String id;
		public final String command;
		public final String hold;
		/** Set this button was listed under. Empty for a flat list. */
		public final String set;

		private Button(final String label, final String name, final String id, final String command,
				final String hold, final String set) {
			this.label = label;
			this.name = name;
			this.id = id;
			this.command = command;
			this.hold = hold;
			this.set = set == null ? "" : set;
		}

		public String listLabel() {
			if (label.length() > 0) {
				return label;
			}
			if (command.length() > 0) {
				return command;
			}
			return hold;
		}
	}

	public static final class Group {
		public final String name;
		public final List<Button> buttons;

		private Group(final String name, final List<Button> buttons) {
			this.name = name == null ? "" : name;
			this.buttons = Collections.unmodifiableList(new ArrayList<Button>(buttons));
		}
	}

	public static List<Button> parse(final String raw) {
		if (raw == null || raw.trim().length() == 0) {
			return Collections.emptyList();
		}
		JSONArray arr;
		try {
			arr = new JSONArray(raw);
		} catch (JSONException e) {
			return Collections.emptyList();
		}
		return parseButtons(arr, "");
	}

	/**
	 * One group per button set, in the order the text lists them.
	 * A flat list is one group with no name.
	 */
	public static List<Group> groups(final String raw) {
		if (raw == null || raw.trim().length() == 0) {
			return Collections.emptyList();
		}
		JSONArray arr;
		try {
			arr = new JSONArray(raw);
		} catch (JSONException e) {
			return Collections.emptyList();
		}
		boolean grouped = false;
		for (int i = 0; i < arr.length(); i++) {
			JSONObject o = arr.optJSONObject(i);
			if (o != null && o.has("buttons")) {
				grouped = true;
				break;
			}
		}
		if (!grouped) {
			List<Button> flat = parseButtons(arr, "");
			if (flat.isEmpty()) {
				return Collections.emptyList();
			}
			return Collections.singletonList(new Group("", flat));
		}
		List<Group> out = new ArrayList<Group>();
		for (int i = 0; i < arr.length(); i++) {
			JSONObject o = arr.optJSONObject(i);
			if (o == null || !o.has("buttons")) {
				continue;
			}
			String name = o.optString("set", "").trim();
			JSONArray buttons = o.optJSONArray("buttons");
			List<Button> rows = buttons == null
					? Collections.<Button>emptyList() : parseButtons(buttons, name);
			if (rows.isEmpty()) {
				continue;
			}
			out.add(new Group(name, rows));
		}
		return out;
	}

	/** Empty text keeps every button. Otherwise the words, name, id, command, hold, or set. */
	public static List<Button> matching(final List<Button> buttons, final String query) {
		if (buttons == null || buttons.isEmpty()) {
			return Collections.emptyList();
		}
		if (query == null || query.trim().length() == 0) {
			return buttons;
		}
		String needle = query.trim().toLowerCase(Locale.ROOT);
		List<Button> out = new ArrayList<Button>();
		for (int i = 0; i < buttons.size(); i++) {
			Button button = buttons.get(i);
			if (matches(button, needle) || has(button.set, needle)) {
				out.add(button);
			}
		}
		return out;
	}

	/**
	 * Empty text keeps every group. A set name keeps that set's buttons.
	 * A button field keeps that button, under its set.
	 */
	public static List<Group> matchingGroups(final List<Group> groups, final String query) {
		if (groups == null || groups.isEmpty()) {
			return Collections.emptyList();
		}
		if (query == null || query.trim().length() == 0) {
			return groups;
		}
		String needle = query.trim().toLowerCase(Locale.ROOT);
		List<Group> out = new ArrayList<Group>();
		for (int i = 0; i < groups.size(); i++) {
			Group group = groups.get(i);
			boolean setHit = has(group.name, needle);
			List<Button> rows = new ArrayList<Button>();
			for (int j = 0; j < group.buttons.size(); j++) {
				Button button = group.buttons.get(j);
				if (setHit || matches(button, needle) || has(button.set, needle)) {
					rows.add(button);
				}
			}
			if (!rows.isEmpty()) {
				out.add(new Group(group.name, rows));
			}
		}
		return out;
	}

	private static List<Button> parseButtons(final JSONArray arr, final String set) {
		List<Button> out = new ArrayList<Button>();
		for (int i = 0; i < arr.length(); i++) {
			Button button = readButton(arr.optJSONObject(i), set);
			if (button != null) {
				out.add(button);
			}
		}
		return out;
	}

	private static Button readButton(final JSONObject o, final String set) {
		if (o == null) {
			return null;
		}
		String label = o.optString("label", "").trim();
		String name = o.optString("name", "").trim();
		String id = o.optString("id", "").trim();
		String command = o.optString("command", "").trim();
		String hold = o.optString("hold", "").trim();
		if (label.length() == 0 && name.length() == 0 && id.length() == 0
				&& command.length() == 0 && hold.length() == 0) {
			return null;
		}
		return new Button(label, name, id, command, hold, set);
	}

	private static boolean matches(final Button button, final String needle) {
		return has(button.label, needle) || has(button.name, needle) || has(button.id, needle)
				|| has(button.command, needle) || has(button.hold, needle);
	}

	private static boolean has(final String value, final String needle) {
		return value != null && value.toLowerCase(Locale.ROOT).indexOf(needle) >= 0;
	}
}
