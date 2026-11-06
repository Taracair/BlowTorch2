package com.resurrection.blowtorch2.lib.window;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * One connection string for hardware shortcuts. Empty means off, and the
 * first turn-on is the only time the Numpad Enter row and the notice are added.
 */
public final class KeyboardShortcuts {

	public static final String SETTING_KEY = "keyboard_shortcuts";

	/** Shown once, the first time shortcuts are turned on for a world. */
	public static final String NOTICE =
			"A hardware key follows the keyboard app you selected, the same app that shows the on-screen keys. "
					+ "The same key can type with one app and do something else with another. "
					+ "A shortcut is read before that app changes the key.";

	public static final int KEYCODE_NUMPAD_ENTER = 160;

	/** Platform meta "on" bits. Left and right are the same modifier. */
	public static final int MOD_SHIFT = 0x1;
	public static final int MOD_ALT = 0x2;
	public static final int MOD_CTRL = 0x1000;
	public static final int MOD_META = 0x10000;
	public static final int MOD_MASK = MOD_SHIFT | MOD_ALT | MOD_CTRL | MOD_META;

	public static final String ACTION_LINE = "line";
	public static final String ACTION_INPUT = "input";
	public static final String ACTION_STEP = "step";

	private final boolean on;
	private final boolean introduced;
	private final List<Row> rows;

	private KeyboardShortcuts(final boolean on, final boolean introduced, final List<Row> rows) {
		this.on = on;
		this.introduced = introduced;
		this.rows = Collections.unmodifiableList(new ArrayList<Row>(rows));
	}

	public static KeyboardShortcuts empty() {
		return new KeyboardShortcuts(false, false, Collections.<Row>emptyList());
	}

	public static KeyboardShortcuts enabled(final Row... rows) {
		List<Row> list = new ArrayList<Row>();
		if (rows != null) {
			for (int i = 0; i < rows.length; i++) {
				if (rows[i] != null) {
					list.add(rows[i]);
				}
			}
		}
		return new KeyboardShortcuts(true, true, list);
	}

	public static KeyboardShortcuts disabled(final Row... rows) {
		List<Row> list = new ArrayList<Row>();
		if (rows != null) {
			for (int i = 0; i < rows.length; i++) {
				if (rows[i] != null) {
					list.add(rows[i]);
				}
			}
		}
		return new KeyboardShortcuts(false, true, list);
	}

	public static int modifiers(final int metaState) {
		return metaState & MOD_MASK;
	}

	public boolean isOn() {
		return on;
	}

	public boolean isIntroduced() {
		return introduced;
	}

	public List<Row> rows() {
		return rows;
	}

	public Row find(final int keyCode, final int metaState) {
		int mods = modifiers(metaState);
		for (int i = 0; i < rows.size(); i++) {
			Row row = rows.get(i);
			if (row.enabled && row.keyCode == keyCode && row.modifiers == mods) {
				return row;
			}
		}
		return null;
	}

	public static KeyboardShortcuts parse(final String stored) {
		if (stored == null || stored.trim().length() == 0) {
			return empty();
		}
		boolean on = false;
		boolean introduced = false;
		List<Row> rows = new ArrayList<Row>();
		String[] parts = stored.split(";", -1);
		for (int i = 0; i < parts.length; i++) {
			String part = parts[i].trim();
			if (part.length() == 0) {
				continue;
			}
			if (part.startsWith("on=")) {
				on = "1".equals(part.substring(3).trim());
				continue;
			}
			if (part.startsWith("introduced=")) {
				introduced = "1".equals(part.substring("introduced=".length()).trim());
				continue;
			}
			Row row = parseRow(part);
			if (row != null) {
				rows.add(row);
			}
		}
		return new KeyboardShortcuts(on, introduced, rows);
	}

	public String format() {
		if (!on && !introduced && rows.isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		sb.append("on=").append(on ? "1" : "0");
		sb.append(";introduced=").append(introduced ? "1" : "0");
		for (int i = 0; i < rows.size(); i++) {
			sb.append(';');
			rows.get(i).append(sb);
		}
		return sb.toString();
	}

	/**
	 * {@code on}, {@code off}, and {@code toggle} are the whole argument.
	 * Empty prints status. Anything else, including a second word, changes nothing.
	 */
	public Change apply(final String arg) {
		String word = arg == null ? "" : arg.trim().toLowerCase(Locale.US);
		if (word.length() == 0) {
			return new Change(this, false, false, false);
		}
		if ("on".equals(word)) {
			return turnOn();
		}
		if ("off".equals(word)) {
			return turnOff();
		}
		if ("toggle".equals(word)) {
			return on ? turnOff() : turnOn();
		}
		return new Change(this, false, false, true);
	}

	private Change turnOn() {
		if (on) {
			return new Change(this, false, false, false);
		}
		boolean notice = !introduced;
		List<Row> nextRows = rows;
		if (!introduced) {
			nextRows = new ArrayList<Row>(rows);
			if (!hasNumpadEnter(nextRows)) {
				nextRows.add(Row.numpadEnter());
			}
		}
		return new Change(new KeyboardShortcuts(true, true, nextRows), true, notice, false);
	}

	private Change turnOff() {
		if (!on) {
			return new Change(this, false, false, false);
		}
		return new Change(new KeyboardShortcuts(false, introduced, rows), true, false, false);
	}

	private static boolean hasNumpadEnter(final List<Row> rows) {
		for (int i = 0; i < rows.size(); i++) {
			Row row = rows.get(i);
			if (row.keyCode == KEYCODE_NUMPAD_ENTER && row.modifiers == 0) {
				return true;
			}
		}
		return false;
	}

	private static Row parseRow(final String part) {
		String[] f = part.split("\\|", -1);
		if (f.length < 5) {
			return null;
		}
		try {
			int key = Integer.parseInt(f[0].trim());
			int mods = Integer.parseInt(f[1].trim());
			String action = f[2].trim();
			if (!ACTION_LINE.equals(action) && !ACTION_INPUT.equals(action)
					&& !ACTION_STEP.equals(action)) {
				return null;
			}
			boolean enabled = "1".equals(f[3].trim());
			boolean repeat = "1".equals(f[4].trim());
			String tap = f.length > 5 ? decode(f[5]) : "";
			String hold = f.length > 6 ? decode(f[6]) : "";
			return Row.of(key, mods, action, enabled, repeat, tap, hold);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	static String encode(final String value) {
		if (value == null || value.length() == 0) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (c == '%' || c == '|' || c == ';' || c <= 0x20) {
				sb.append('%');
				String hex = Integer.toHexString(c);
				if (hex.length() == 1) {
					sb.append('0');
				}
				sb.append(hex);
			} else {
				sb.append(c);
			}
		}
		return sb.toString();
	}

	static String decode(final String value) {
		if (value == null || value.length() == 0) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (c == '%' && i + 2 < value.length()) {
				int hi = hex(value.charAt(i + 1));
				int lo = hex(value.charAt(i + 2));
				if (hi >= 0 && lo >= 0) {
					sb.append((char) ((hi << 4) | lo));
					i += 2;
					continue;
				}
			}
			sb.append(c);
		}
		return sb.toString();
	}

	private static int hex(final char c) {
		if (c >= '0' && c <= '9') {
			return c - '0';
		}
		if (c >= 'a' && c <= 'f') {
			return c - 'a' + 10;
		}
		if (c >= 'A' && c <= 'F') {
			return c - 'A' + 10;
		}
		return -1;
	}

	public static final class Row {
		public final int keyCode;
		public final int modifiers;
		public final String action;
		public final boolean enabled;
		public final boolean repeat;
		public final String tap;
		public final String hold;

		private Row(final int keyCode, final int modifiers, final String action,
				final boolean enabled, final boolean repeat, final String tap, final String hold) {
			this.keyCode = keyCode;
			this.modifiers = modifiers;
			this.action = action;
			this.enabled = enabled;
			this.repeat = repeat;
			this.tap = tap;
			this.hold = hold;
		}

		/** Repeat and a hold line are not both kept. A hold line wins. */
		public static Row of(final int keyCode, final int modifiers, final String action,
				final boolean enabled, final boolean repeat, final String tap, final String hold) {
			String useTap = flatten(tap);
			String useHold = flatten(hold);
			boolean useRepeat = repeat;
			if (useHold.length() > 0 && useRepeat) {
				useRepeat = false;
			}
			String useAction = action == null ? ACTION_LINE : action;
			return new Row(keyCode, modifiers & MOD_MASK, useAction, enabled, useRepeat, useTap, useHold);
		}

		public static Row numpadEnter() {
			return of(KEYCODE_NUMPAD_ENTER, 0, ACTION_INPUT, true, false, "", "");
		}

		private void append(final StringBuilder sb) {
			sb.append(keyCode).append('|');
			sb.append(modifiers).append('|');
			sb.append(action).append('|');
			sb.append(enabled ? '1' : '0').append('|');
			sb.append(repeat ? '1' : '0').append('|');
			sb.append(encode(tap)).append('|');
			sb.append(encode(hold));
		}

		private static String flatten(final String value) {
			if (value == null || value.length() == 0) {
				return "";
			}
			String trimmed = value.trim();
			if (trimmed.length() == 0) {
				return "";
			}
			return trimmed.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
		}
	}

	public static final class Change {
		public final KeyboardShortcuts next;
		public final boolean changed;
		public final boolean printNotice;
		public final boolean usage;

		private Change(final KeyboardShortcuts next, final boolean changed,
				final boolean printNotice, final boolean usage) {
			this.next = next;
			this.changed = changed;
			this.printNotice = printNotice;
			this.usage = usage;
		}
	}
}
