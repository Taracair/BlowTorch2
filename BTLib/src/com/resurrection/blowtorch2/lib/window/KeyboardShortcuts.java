package com.resurrection.blowtorch2.lib.window;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * One connection string for hardware shortcuts. Empty means off.
 * The notice is printed once. Escape and the jump chords are offered once.
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
	/** Put a recalled draft back, or clear the bar, or hide the keyboard. */
	public static final String ACTION_ESCAPE = "escape";
	/** Jump the game text to the live end. */
	public static final String ACTION_LIVE = "live";

	/** Platform {@code KeyEvent.KEYCODE_ESCAPE}. */
	public static final int KEYCODE_ESCAPE = 111;
	/** Platform {@code KeyEvent.KEYCODE_ENTER}. */
	public static final int KEYCODE_ENTER = 66;
	/** Platform {@code KeyEvent.KEYCODE_MOVE_END}. */
	public static final int KEYCODE_MOVE_END = 123;

	private final boolean on;
	private final boolean introduced;
	/** Escape, Ctrl+Enter, and Ctrl+End have been offered. Absent on an old save. */
	private final boolean offered;
	private final int roseModifier;
	private final int latchKey;
	private final List<Row> rows;

	private KeyboardShortcuts(final boolean on, final boolean introduced, final List<Row> rows,
			final int roseModifier, final int latchKey, final boolean offered) {
		this.on = on;
		this.introduced = introduced;
		this.offered = offered;
		this.roseModifier = roseModifier == MOD_ALT || roseModifier == MOD_CTRL ? roseModifier : 0;
		this.latchKey = this.roseModifier == 0 || latchKey < 0 ? -1 : latchKey;
		this.rows = Collections.unmodifiableList(new ArrayList<Row>(rows));
	}

	/** Keeps the rose modifier, the latch key, and whether the bar chords were offered. */
	private KeyboardShortcuts copy(final boolean nextOn, final boolean nextIntroduced, final List<Row> nextRows) {
		return new KeyboardShortcuts(nextOn, nextIntroduced, nextRows, roseModifier, latchKey, offered);
	}

	public static KeyboardShortcuts empty() {
		return new KeyboardShortcuts(false, false, Collections.<Row>emptyList(), 0, -1, false);
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
		return new KeyboardShortcuts(true, true, list, 0, -1, true);
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
		return new KeyboardShortcuts(false, true, list, 0, -1, true);
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

	/** Alt, Ctrl, or 0 when the number pad is bare. */
	public int roseModifier() {
		return roseModifier;
	}

	/** Key that toggles the latch, or -1 when there is none. */
	public int latchKey() {
		return latchKey;
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
		boolean offered = false;
		int rose = 0;
		int latch = -1;
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
			if (part.startsWith("offered=")) {
				offered = "1".equals(part.substring("offered=".length()).trim());
				continue;
			}
			if (part.startsWith("rose=")) {
				rose = parseHeaderInt(part.substring("rose=".length()));
				continue;
			}
			if (part.startsWith("latch=")) {
				latch = parseHeaderInt(part.substring("latch=".length()));
				continue;
			}
			Row row = parseRow(part);
			if (row != null) {
				rows.add(row);
			}
		}
		// An old save has no offered mark, so these chords were never in it.
		// The mark stays after a delete, and a later load does not put the row back.
		if (!offered) {
			addSeed(rows, Row.barEscape());
			addSeed(rows, Row.jumpLive(KEYCODE_ENTER, MOD_CTRL));
			addSeed(rows, Row.jumpLive(KEYCODE_MOVE_END, MOD_CTRL));
			offered = true;
		}
		return new KeyboardShortcuts(on, introduced, rows, rose, latch, offered);
	}

	public String format() {
		if (!on && !introduced && rows.isEmpty() && roseModifier == 0 && latchKey < 0 && !offered) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		sb.append("on=").append(on ? "1" : "0");
		sb.append(";introduced=").append(introduced ? "1" : "0");
		if (offered) {
			sb.append(";offered=1");
		}
		if (roseModifier != 0) {
			sb.append(";rose=").append(roseModifier);
		}
		if (latchKey >= 0) {
			sb.append(";latch=").append(latchKey);
		}
		for (int i = 0; i < rows.size(); i++) {
			sb.append(';');
			rows.get(i).append(sb);
		}
		return sb.toString();
	}

	/**
	 * {@code on}, {@code off}, {@code toggle}, and {@code edit} are the whole argument.
	 * Empty prints status. Anything else, including a second word, changes nothing.
	 */
	public Change apply(final String arg) {
		String word = arg == null ? "" : arg.trim().toLowerCase(Locale.US);
		if (word.length() == 0) {
			return new Change(this, false, false, false, false);
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
		if ("edit".equals(word)) {
			return new Change(this, false, false, false, true);
		}
		return new Change(this, false, false, true, false);
	}

	private Change turnOn() {
		if (on) {
			return new Change(this, false, false, false, false);
		}
		boolean notice = !introduced;
		List<Row> nextRows = rows;
		if (!introduced) {
			nextRows = new ArrayList<Row>(rows);
			// Once, and only a chord that is still free.
			addSeed(nextRows, Row.numpadEnter());
			addSeed(nextRows, Row.barEscape());
			addSeed(nextRows, Row.jumpLive(KEYCODE_ENTER, MOD_CTRL));
			addSeed(nextRows, Row.jumpLive(KEYCODE_MOVE_END, MOD_CTRL));
			return new Change(new KeyboardShortcuts(true, true, nextRows, roseModifier, latchKey, true),
					true, notice, false, false);
		}
		return new Change(copy(true, true, nextRows), true, notice, false, false);
	}

	private Change turnOff() {
		if (!on) {
			return new Change(this, false, false, false, false);
		}
		return new Change(copy(false, introduced, rows), true, false, false, false);
	}

	/** Includes a disabled row. {@link #find} does not. */
	public Row row(final int keyCode, final int modifiers) {
		int mods = modifiers(modifiers);
		for (int i = 0; i < rows.size(); i++) {
			Row existing = rows.get(i);
			if (existing.keyCode == keyCode && existing.modifiers == mods) {
				return existing;
			}
		}
		return null;
	}

	/**
	 * One chord is one row. {@code previousKey} below 0 appends.
	 * Moving a row onto another chord replaces that chord and drops the old one.
	 */
	public KeyboardShortcuts withRow(final int previousKey, final int previousMods, final Row row) {
		if (row == null || row.keyCode < 0) {
			return this;
		}
		int oldMods = modifiers(previousMods);
		List<Row> next = new ArrayList<Row>();
		boolean placed = false;
		for (int i = 0; i < rows.size(); i++) {
			Row existing = rows.get(i);
			boolean wasEdited = previousKey >= 0 && existing.keyCode == previousKey
					&& existing.modifiers == oldMods;
			boolean sameChord = existing.keyCode == row.keyCode && existing.modifiers == row.modifiers;
			if (wasEdited || sameChord) {
				if (!placed) {
					next.add(row);
					placed = true;
				}
				continue;
			}
			next.add(existing);
		}
		if (!placed) {
			next.add(row);
		}
		return copy(on, introduced, next);
	}

	public KeyboardShortcuts withoutRow(final int keyCode, final int modifiers) {
		int mods = modifiers(modifiers);
		List<Row> next = new ArrayList<Row>();
		boolean removed = false;
		for (int i = 0; i < rows.size(); i++) {
			Row existing = rows.get(i);
			if (existing.keyCode == keyCode && existing.modifiers == mods) {
				removed = true;
				continue;
			}
			next.add(existing);
		}
		if (!removed) {
			return this;
		}
		return copy(on, introduced, next);
	}

	/** Rows go. The switch and the one-time notice stay, so a later on does not reseed. */
	public KeyboardShortcuts withoutRows() {
		if (rows.isEmpty()) {
			return this;
		}
		return copy(on, introduced, Collections.<Row>emptyList());
	}

	/**
	 * Replaces every number-pad row. A chord used by a new pad row drops the
	 * ordinary shortcut that was on it. Unrelated chords stay.
	 */
	public KeyboardShortcuts withPad(final List<Row> padRows, final int rose, final int latch) {
		List<Row> next = new ArrayList<Row>();
		for (int i = 0; i < rows.size(); i++) {
			Row existing = rows.get(i);
			if (!existing.pad) {
				next.add(existing);
			}
		}
		if (padRows != null) {
			for (int i = 0; i < padRows.size(); i++) {
				Row pad = padRows.get(i);
				if (pad == null || pad.keyCode < 0) {
					continue;
				}
				Row marked = pad.asPad(true);
				for (int j = next.size() - 1; j >= 0; j--) {
					Row old = next.get(j);
					if (old.keyCode == marked.keyCode && old.modifiers == marked.modifiers) {
						next.remove(j);
					}
				}
				next.add(marked);
			}
		}
		return new KeyboardShortcuts(on, introduced, next, rose, latch, offered);
	}

	public KeyboardShortcuts withEnabled(final int keyCode, final int modifiers, final boolean enabled) {
		Row existing = row(keyCode, modifiers);
		if (existing == null || existing.enabled == enabled) {
			return this;
		}
		return withRow(keyCode, modifiers, Row.of(existing.keyCode, existing.modifiers, existing.action,
				enabled, existing.repeat, existing.tap, existing.hold, existing.name, existing.quiet)
				.asPad(existing.pad));
	}

	public static String listKey(final int keyCode, final int modifiers) {
		return keyCode + "|" + modifiers(modifiers);
	}

	public static String chordLabel(final int keyCode, final int modifiers) {
		if (keyCode < 0) {
			return "nothing";
		}
		int mods = modifiers(modifiers);
		StringBuilder sb = new StringBuilder();
		if ((mods & MOD_CTRL) != 0) {
			sb.append("Ctrl+");
		}
		if ((mods & MOD_ALT) != 0) {
			sb.append("Alt+");
		}
		if ((mods & MOD_SHIFT) != 0) {
			sb.append("Shift+");
		}
		if ((mods & MOD_META) != 0) {
			sb.append("Meta+");
		}
		sb.append(keyName(keyCode));
		return sb.toString();
	}

	/** Title is the name, or the chord when the name is empty. */
	public static ListText listText(final Row row) {
		if (row == null) {
			return new ListText("", "", "");
		}
		String chord = chordLabel(row.keyCode, row.modifiers);
		String title = row.name.length() > 0 ? row.name : chord;
		StringBuilder detail = new StringBuilder();
		if (ACTION_STEP.equals(row.action)) {
			detail.append("step back");
		} else if (ACTION_INPUT.equals(row.action)) {
			detail.append("send the line");
		} else if (ACTION_ESCAPE.equals(row.action)) {
			detail.append("clear the bar");
		} else if (ACTION_LIVE.equals(row.action)) {
			detail.append("newest text");
		} else if (row.tap.length() > 0) {
			detail.append(row.tap);
		}
		if (row.hold.length() > 0) {
			if (detail.length() > 0) {
				detail.append('\n');
			}
			detail.append("hold · ").append(row.hold);
		}
		if (row.repeat) {
			if (detail.length() > 0) {
				detail.append('\n');
			}
			detail.append("hold to repeat");
		}
		String search = (row.name + "\n" + chord + "\n" + row.tap + "\n" + row.hold + "\n"
				+ detail).toLowerCase(Locale.US);
		return new ListText(title, detail.toString(), search);
	}

	private static String keyName(final int keyCode) {
		if (keyCode >= 29 && keyCode <= 54) {
			return String.valueOf((char) ('A' + (keyCode - 29)));
		}
		if (keyCode >= 7 && keyCode <= 16) {
			return String.valueOf(keyCode - 7);
		}
		if (keyCode >= 131 && keyCode <= 142) {
			return "F" + (keyCode - 130);
		}
		if (keyCode >= 144 && keyCode <= 153) {
			return "Num " + (keyCode - 144);
		}
		switch (keyCode) {
			case 19:
				return "Up";
			case 20:
				return "Down";
			case 21:
				return "Left";
			case 22:
				return "Right";
			case 23:
				return "Center";
			case 66:
				return "Enter";
			case 67:
				return "Backspace";
			case 111:
				return "Esc";
			case 112:
				return "Delete";
			case 61:
				return "Tab";
			case 62:
				return "Space";
			case 92:
				return "Page Up";
			case 93:
				return "Page Down";
			case 122:
				return "Home";
			case 123:
				return "End";
			case 154:
				return "Num /";
			case 155:
				return "Num *";
			case 156:
				return "Num -";
			case 157:
				return "Num +";
			case 158:
				return "Num .";
			case 160:
				return "Num Enter";
			case 57:
			case 58:
				return "Alt";
			case 113:
			case 114:
				return "Ctrl";
			case 59:
			case 60:
				return "Shift";
			case 117:
			case 118:
				return "Meta";
			default:
				return "Key " + keyCode;
		}
	}

	private static int parseHeaderInt(final String value) {
		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	private static void addSeed(final List<Row> rows, final Row row) {
		if (row == null || hasChord(rows, row.keyCode, row.modifiers)) {
			return;
		}
		rows.add(row);
	}

	private static boolean hasChord(final List<Row> rows, final int keyCode, final int modifiers) {
		int mods = modifiers(modifiers);
		for (int i = 0; i < rows.size(); i++) {
			Row row = rows.get(i);
			if (row.keyCode == keyCode && row.modifiers == mods) {
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
					&& !ACTION_STEP.equals(action) && !ACTION_ESCAPE.equals(action)
					&& !ACTION_LIVE.equals(action)) {
				return null;
			}
			boolean enabled = "1".equals(f[3].trim());
			boolean repeat = "1".equals(f[4].trim());
			String tap = f.length > 5 ? decode(f[5]) : "";
			String hold = f.length > 6 ? decode(f[6]) : "";
			String name = f.length > 7 ? decode(f[7]) : "";
			boolean quiet = f.length > 8 && "1".equals(f[8].trim());
			boolean pad = f.length > 9 && "1".equals(f[9].trim());
			return Row.of(key, mods, action, enabled, repeat, tap, hold, name, quiet).asPad(pad);
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
		public final String name;
		/** Send the line without printing it in the window. */
		public final boolean quiet;
		/** Written by the number-pad form. Done replaces these and leaves other rows. */
		public final boolean pad;

		private Row(final int keyCode, final int modifiers, final String action,
				final boolean enabled, final boolean repeat, final String tap, final String hold,
				final String name, final boolean quiet, final boolean pad) {
			this.keyCode = keyCode;
			this.modifiers = modifiers;
			this.action = action;
			this.enabled = enabled;
			this.repeat = repeat;
			this.tap = tap;
			this.hold = hold;
			this.name = name;
			this.quiet = quiet;
			this.pad = pad;
		}

		/** Repeat and a hold line are not both kept. A hold line wins. */
		public static Row of(final int keyCode, final int modifiers, final String action,
				final boolean enabled, final boolean repeat, final String tap, final String hold) {
			return of(keyCode, modifiers, action, enabled, repeat, tap, hold, "", false);
		}

		/**
		 * Repeat and a hold line are not both kept. A hold line wins.
		 * Step back, send-the-line, Escape, and jump-to-end store no lines and do not repeat.
		 * Send-the-line is not quiet.
		 */
		public static Row of(final int keyCode, final int modifiers, final String action,
				final boolean enabled, final boolean repeat, final String tap, final String hold,
				final String name, final boolean quiet) {
			String useTap = flatten(tap);
			String useHold = flatten(hold);
			boolean useRepeat = repeat;
			String useAction = action == null ? ACTION_LINE : action;
			boolean useQuiet = quiet;
			if (ACTION_STEP.equals(useAction) || ACTION_INPUT.equals(useAction)
					|| ACTION_ESCAPE.equals(useAction) || ACTION_LIVE.equals(useAction)) {
				useTap = "";
				useHold = "";
				useRepeat = false;
				if (!ACTION_STEP.equals(useAction)) {
					useQuiet = false;
				}
			} else if (useHold.length() > 0 && useRepeat) {
				useRepeat = false;
			}
			return new Row(keyCode, modifiers & MOD_MASK, useAction, enabled, useRepeat, useTap,
					useHold, flatten(name), useQuiet, false);
		}

		public Row asPad(final boolean pad) {
			return new Row(keyCode, modifiers, action, enabled, repeat, tap, hold, name, quiet, pad);
		}

		/** A copy of this button's tap and hold. Repeat is off. Later edits of the button are not followed. */
		public Row withButton(final String command, final String holdCommand) {
			return of(keyCode, modifiers, ACTION_LINE, enabled, false, command, holdCommand, name, quiet)
					.asPad(pad);
		}

		public static Row numpadEnter() {
			return of(KEYCODE_NUMPAD_ENTER, 0, ACTION_INPUT, true, false, "", "");
		}

		public static Row barEscape() {
			return of(KEYCODE_ESCAPE, 0, ACTION_ESCAPE, true, false, "", "");
		}

		public static Row jumpLive(final int keyCode, final int modifiers) {
			return of(keyCode, modifiers, ACTION_LIVE, true, false, "", "");
		}

		private void append(final StringBuilder sb) {
			sb.append(keyCode).append('|');
			sb.append(modifiers).append('|');
			sb.append(action).append('|');
			sb.append(enabled ? '1' : '0').append('|');
			sb.append(repeat ? '1' : '0').append('|');
			sb.append(encode(tap)).append('|');
			sb.append(encode(hold)).append('|');
			sb.append(encode(name)).append('|');
			sb.append(quiet ? '1' : '0');
			if (pad) {
				sb.append("|1");
			}
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

	public static final class ListText {
		public final String title;
		public final String detail;
		public final String search;

		private ListText(final String title, final String detail, final String search) {
			this.title = title;
			this.detail = detail;
			this.search = search;
		}
	}

	public static final class Change {
		public final KeyboardShortcuts next;
		public final boolean changed;
		public final boolean printNotice;
		public final boolean usage;
		/** {@code .shortcuts edit} opens the list and does not flip the switch. */
		public final boolean openList;

		private Change(final KeyboardShortcuts next, final boolean changed,
				final boolean printNotice, final boolean usage, final boolean openList) {
			this.next = next;
			this.changed = changed;
			this.printNotice = printNotice;
			this.usage = usage;
			this.openList = openList;
		}
	}
}
