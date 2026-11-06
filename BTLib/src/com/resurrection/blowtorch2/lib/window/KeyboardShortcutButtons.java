package com.resurrection.blowtorch2.lib.window;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** The current button set, copied into a shortcut. Swipes and flip are not in this list. */
public final class KeyboardShortcutButtons {

	private KeyboardShortcutButtons() {
	}

	public static final class Button {
		public final String label;
		public final String command;
		public final String hold;

		private Button(final String label, final String command, final String hold) {
			this.label = label;
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
			String command = o.optString("command", "").trim();
			String hold = o.optString("hold", "").trim();
			if (label.length() == 0 && command.length() == 0 && hold.length() == 0) {
				continue;
			}
			out.add(new Button(label, command, hold));
		}
		return out;
	}
}
