package com.resurrection.blowtorch2.lib.window;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** The current button set, copied into a shortcut. Swipes and flip are not in this list. */
public final class KeyboardShortcutButtons {

	private KeyboardShortcutButtons() {
	}

	public static final class Button {
		public final String label;
		public final String name;
		public final String id;
		public final String command;
		public final String hold;

		private Button(final String label, final String name, final String id, final String command,
				final String hold) {
			this.label = label;
			this.name = name;
			this.id = id;
			this.command = command;
			this.hold = hold;
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
		List<Button> out = new ArrayList<Button>();
		for (int i = 0; i < arr.length(); i++) {
			JSONObject o = arr.optJSONObject(i);
			if (o == null) {
				continue;
			}
			String label = o.optString("label", "").trim();
			String name = o.optString("name", "").trim();
			String id = o.optString("id", "").trim();
			String command = o.optString("command", "").trim();
			String hold = o.optString("hold", "").trim();
			if (label.length() == 0 && name.length() == 0 && id.length() == 0
					&& command.length() == 0 && hold.length() == 0) {
				continue;
			}
			out.add(new Button(label, name, id, command, hold));
		}
		return out;
	}

	/** Empty text keeps every button. Otherwise the words, name, id, command, or hold line. */
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
			if (has(button.label, needle) || has(button.name, needle) || has(button.id, needle)
					|| has(button.command, needle) || has(button.hold, needle)) {
				out.add(button);
			}
		}
		return out;
	}

	private static boolean has(final String value, final String needle) {
		return value != null && value.toLowerCase(Locale.ROOT).indexOf(needle) >= 0;
	}
}
