package com.resurrection.blowtorch2.lib.window;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.resurrection.blowtorch2.lib.mapper.MapDirections;
import com.resurrection.blowtorch2.lib.speedwalk.DirectionData;
import com.resurrection.blowtorch2.lib.window.KeyboardShortcuts.Row;

/**
 * The number-pad form. Nothing here is stored until {@link #apply}.
 * Numpad 0 is key 144 and 9 is 153. Add is 157, subtract 156, multiply 155, divide 154.
 */
public final class KeyboardShortcutPad {

	public static final int CELLS = 12;
	private static final int LOOK_CELL = 4;
	private static final int KEY_DIVIDE = 154;
	private static final int KEY_MULTIPLY = 155;

	private static final int[] KEYS = {
			151, 152, 153,
			148, 149, 150,
			145, 146, 147,
			144, 157, 156
	};
	private static final String[] FACES = {
			"7", "8", "9",
			"4", "5", "6",
			"1", "2", "3",
			"0", "+", "−"
	};
	private static final String[] COMPASS = {
			"northwest", "north", "northeast",
			"west", "look", "east",
			"southwest", "south", "southeast",
			"down", "up", "down"
	};
	private static final String[] SHORT = {
			"nw", "n", "ne",
			"w", "look", "e",
			"sw", "s", "se",
			"d", "u", "d"
	};

	private KeyboardShortcutPad() {
	}

	public static final class Cell {
		public final int keyCode;
		public final String face;
		public String command = "";
		public boolean repeat = true;

		private Cell(final int keyCode, final String face) {
			this.keyCode = keyCode;
			this.face = face;
		}
	}

	public static final class Draft {
		public final Cell[] cells = new Cell[CELLS];
		public int modifier;
		public boolean look;
		public boolean inOut;
		public int latchKey = -1;

		private Draft() {
			for (int i = 0; i < CELLS; i++) {
				cells[i] = new Cell(KEYS[i], FACES[i]);
				cells[i].repeat = i != LOOK_CELL;
			}
		}
	}

	public static Draft empty() {
		return new Draft();
	}

	public static Draft compass() {
		return filled(COMPASS);
	}

	public static Draft shortSteps() {
		return filled(SHORT);
	}

	/**
	 * Copies the command set for each compass meaning. The map key is not the
	 * command: a letter that only shortens a run stays a run letter.
	 * A missing up or down keeps the compass word.
	 */
	public static Draft speedwalk(final Map<String, DirectionData> directions) {
		Draft draft = empty();
		for (int i = 0; i < CELLS; i++) {
			draft.cells[i].command = commandFor(directions, COMPASS[i]);
			draft.cells[i].repeat = i != LOOK_CELL;
		}
		return draft;
	}

	public static void copyCommands(final Draft target, final Draft source) {
		if (target == null || source == null) {
			return;
		}
		for (int i = 0; i < CELLS; i++) {
			target.cells[i].command = source.cells[i].command;
			target.cells[i].repeat = source.cells[i].repeat;
		}
	}

	/** Alt when the walk modifier is none or Ctrl. Ctrl when the walk modifier is Alt. */
	public static int lookModifier(final int walkModifier) {
		if (walkModifier == KeyboardShortcuts.MOD_ALT) {
			return KeyboardShortcuts.MOD_CTRL;
		}
		return KeyboardShortcuts.MOD_ALT;
	}

	public static Draft fromRows(final KeyboardShortcuts stored) {
		Draft draft = empty();
		if (stored == null) {
			return draft;
		}
		draft.modifier = stored.roseModifier();
		draft.latchKey = stored.latchKey();
		int lookMod = lookModifier(draft.modifier);
		boolean sawIn = false;
		boolean sawOut = false;
		boolean sawLook = false;
		List<Row> rows = stored.rows();
		for (int i = 0; i < rows.size(); i++) {
			Row row = rows.get(i);
			if (row == null || !row.pad) {
				continue;
			}
			if (row.keyCode == KEY_MULTIPLY && equalsWord(row.tap, "in")) {
				sawIn = true;
			}
			if (row.keyCode == KEY_DIVIDE && equalsWord(row.tap, "out")) {
				sawOut = true;
			}
			int cell = cellIndex(row.keyCode);
			if (cell < 0) {
				continue;
			}
			if (row.modifiers == lookMod && startsWithLook(row.tap)) {
				sawLook = true;
				continue;
			}
			if (draft.cells[cell].command.length() == 0 || row.modifiers == draft.modifier) {
				draft.cells[cell].command = row.tap == null ? "" : row.tap;
				draft.cells[cell].repeat = row.repeat;
			}
		}
		draft.look = sawLook;
		draft.inOut = sawIn || sawOut;
		return draft;
	}

	public static KeyboardShortcuts apply(final KeyboardShortcuts current, final Draft draft) {
		KeyboardShortcuts base = current == null ? KeyboardShortcuts.empty() : current;
		if (draft == null) {
			return base;
		}
		return base.withPad(rows(draft), draft.modifier, draft.latchKey);
	}

	public static String commandFor(final Map<String, DirectionData> directions, final String meaning) {
		if (meaning == null) {
			return "";
		}
		if ("look".equals(meaning)) {
			return "look";
		}
		String want = MapDirections.toLongForm(meaning);
		if (directions != null && want != null) {
			for (DirectionData data : directions.values()) {
				if (data == null || data.getCommand() == null) {
					continue;
				}
				String command = data.getCommand().trim();
				if (command.length() == 0) {
					continue;
				}
				String longForm = MapDirections.toLongForm(
						MapDirections.stripMovementPrefix(command.toLowerCase(Locale.US)));
				if (want.equals(longForm)) {
					return command;
				}
			}
		}
		return meaning;
	}

	private static Draft filled(final String[] commands) {
		Draft draft = empty();
		for (int i = 0; i < CELLS; i++) {
			draft.cells[i].command = commands[i];
			draft.cells[i].repeat = i != LOOK_CELL;
		}
		return draft;
	}

	private static List<Row> rows(final Draft draft) {
		List<Row> rows = new ArrayList<Row>();
		int walk = draft.modifier == KeyboardShortcuts.MOD_ALT
				|| draft.modifier == KeyboardShortcuts.MOD_CTRL ? draft.modifier : 0;
		int lookMod = lookModifier(walk);
		for (int i = 0; i < CELLS; i++) {
			Cell cell = draft.cells[i];
			String command = cell.command == null ? "" : cell.command.trim();
			if (command.length() == 0) {
				continue;
			}
			rows.add(line(cell.keyCode, walk, command, cell.repeat, true));
			if (draft.look && command.indexOf(' ') < 0 && !equalsWord(command, "look")) {
				rows.add(line(cell.keyCode, lookMod, "look " + command, false, false));
			}
		}
		if (draft.inOut) {
			rows.add(line(KEY_MULTIPLY, walk, "in", true, true));
			rows.add(line(KEY_DIVIDE, walk, "out", true, true));
		}
		return rows;
	}

	private static Row line(final int key, final int modifiers, final String command,
			final boolean repeat, final boolean quiet) {
		return Row.of(key, modifiers, KeyboardShortcuts.ACTION_LINE, true, repeat, command, "", "", quiet)
				.asPad(true);
	}

	private static int cellIndex(final int keyCode) {
		for (int i = 0; i < KEYS.length; i++) {
			if (KEYS[i] == keyCode) {
				return i;
			}
		}
		return -1;
	}

	private static boolean equalsWord(final String value, final String word) {
		return value != null && word.equalsIgnoreCase(value.trim());
	}

	private static boolean startsWithLook(final String value) {
		return value != null && value.trim().toLowerCase(Locale.US).startsWith("look ");
	}
}
