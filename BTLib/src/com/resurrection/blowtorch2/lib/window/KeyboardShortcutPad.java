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
 * Numpad 0 is key 144 and 9 is 153. Add is 157, subtract 156.
 * Multiply is 155, divide is 154, enter is 160.
 * An empty cell is not a shortcut. Enter sends the current line.
 */
public final class KeyboardShortcutPad {

	public static final int CELLS = 15;
	/** Compass, 0, +, and −. Presets fill these and leave *, /, and Enter alone. */
	public static final int ROSE_CELLS = 12;
	private static final int LOOK_CELL = 4;
	private static final int ENTER_CELL = 14;

	public static final String SEND_CURRENT_LINE = "Send the current line";

	private static final int[] KEYS = {
			151, 152, 153,
			148, 149, 150,
			145, 146, 147,
			144, 157, 156,
			155, 154, KeyboardShortcuts.KEYCODE_NUMPAD_ENTER
	};
	private static final String[] FACES = {
			"7", "8", "9",
			"4", "5", "6",
			"1", "2", "3",
			"0", "+", "−",
			"*", "/", "Enter"
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
		/** Numpad Enter sends the current line. The label is not the command. */
		public boolean sendInput;

		private Cell(final int keyCode, final String face) {
			this.keyCode = keyCode;
			this.face = face;
		}
	}

	public static final class Draft {
		public final Cell[] cells = new Cell[CELLS];
		public int modifier;
		public boolean look;
		public int latchKey = -1;
		/** The player cleared Enter. A preset leaves this false, so Done keeps that row. */
		public boolean enterCleared;

		private Draft() {
			for (int i = 0; i < CELLS; i++) {
				cells[i] = new Cell(KEYS[i], FACES[i]);
				cells[i].repeat = i != LOOK_CELL && i != ENTER_CELL;
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
	 * A missing up or down keeps the compass word. * and / stay empty.
	 */
	public static Draft speedwalk(final Map<String, DirectionData> directions) {
		Draft draft = empty();
		for (int i = 0; i < ROSE_CELLS; i++) {
			draft.cells[i].command = commandFor(directions, COMPASS[i]);
			draft.cells[i].repeat = i != LOOK_CELL;
		}
		return draft;
	}

	/** Copies the twelve direction cells. *, /, and Enter stay as the form loaded them. */
	public static void copyCommands(final Draft target, final Draft source) {
		if (target == null || source == null) {
			return;
		}
		for (int i = 0; i < ROSE_CELLS; i++) {
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

	/** Names the key Look will use. None and Ctrl use Alt. Alt uses Ctrl. */
	public static String lookCaption(final int walkModifier) {
		if (walkModifier == KeyboardShortcuts.MOD_ALT) {
			return "Ctrl sends look plus that walk. A walk with a space has no look line.";
		}
		return "Alt sends look plus that walk. A walk with a space has no look line.";
	}

	/** None tells the player to pick Alt or Ctrl. Alt and Ctrl name that key. */
	public static String latchCaption(final int walkModifier) {
		if (walkModifier == KeyboardShortcuts.MOD_ALT) {
			return "This key, or Alt alone, turns walking on. The same key turns it off. The bar shows Pad walks.";
		}
		if (walkModifier == KeyboardShortcuts.MOD_CTRL) {
			return "This key, or Ctrl alone, turns walking on. The same key turns it off. The bar shows Pad walks.";
		}
		return "Pick Alt or Ctrl above. The numbers already walk, so there is nothing to keep on.";
	}

	/** None does not capture. */
	public static String latchChip(final int walkModifier, final int latchKey, final boolean armed,
			final String armedLabel) {
		if (walkModifier != KeyboardShortcuts.MOD_ALT && walkModifier != KeyboardShortcuts.MOD_CTRL) {
			return "nothing";
		}
		if (armed) {
			return armedLabel == null ? "nothing" : armedLabel;
		}
		if (latchKey < 0) {
			return "nothing";
		}
		return KeyboardShortcuts.chordLabel(latchKey, 0);
	}

	public static String cellLabel(final Cell cell) {
		if (cell == null) {
			return "";
		}
		String command = displayedCommand(cell);
		if (command.length() == 0) {
			return cell.face;
		}
		return cell.face + "\n" + command;
	}

	public static String editorText(final Cell cell) {
		if (cell == null) {
			return "";
		}
		if (cell.sendInput) {
			return SEND_CURRENT_LINE;
		}
		return cell.command == null ? "" : cell.command;
	}

	/**
	 * Empty clears the cell. The Enter label stays the input action.
	 * Any other Enter text is that cell's command, and Done writes it back.
	 */
	public static void edit(final Draft draft, final int index, final String text, final boolean repeat) {
		if (draft == null || index < 0 || index >= CELLS) {
			return;
		}
		Cell cell = draft.cells[index];
		if (index == ENTER_CELL) {
			String command = text == null ? "" : text.trim();
			if (command.length() == 0) {
				cell.sendInput = false;
				cell.command = "";
				draft.enterCleared = true;
				return;
			}
			if (SEND_CURRENT_LINE.equals(command)) {
				cell.sendInput = true;
				cell.command = "";
				draft.enterCleared = false;
				return;
			}
			cell.sendInput = false;
			cell.command = command;
			draft.enterCleared = true;
			return;
		}
		cell.command = text == null ? "" : text;
		cell.repeat = repeat;
	}

	public static Draft fromRows(final KeyboardShortcuts stored) {
		Draft draft = empty();
		if (stored == null) {
			return draft;
		}
		draft.modifier = stored.roseModifier();
		draft.latchKey = stored.latchKey();
		int lookMod = lookModifier(draft.modifier);
		boolean sawLook = false;
		List<Row> rows = stored.rows();
		for (int i = 0; i < rows.size(); i++) {
			Row row = rows.get(i);
			if (row == null) {
				continue;
			}
			if (isEnterInput(row)) {
				draft.cells[ENTER_CELL].sendInput = true;
				continue;
			}
			if (!row.pad) {
				continue;
			}
			int cell = cellIndex(row.keyCode);
			if (cell < 0) {
				continue;
			}
			if (cell == ENTER_CELL) {
				String tap = row.tap == null ? "" : row.tap.trim();
				if (row.modifiers == 0 && tap.length() > 0 && !SEND_CURRENT_LINE.equals(tap)) {
					draft.cells[ENTER_CELL].command = row.tap;
				}
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
		return draft;
	}

	public static KeyboardShortcuts apply(final KeyboardShortcuts current, final Draft draft) {
		KeyboardShortcuts base = current == null ? KeyboardShortcuts.empty() : current;
		if (draft == null) {
			return base;
		}
		KeyboardShortcuts next = base.withPad(rows(draft, base), draft.modifier, draft.latchKey);
		if (!draft.enterCleared || draft.cells[ENTER_CELL].sendInput) {
			return next;
		}
		String command = draft.cells[ENTER_CELL].command == null
				? "" : draft.cells[ENTER_CELL].command.trim();
		if (command.length() > 0) {
			return next;
		}
		Row left = next.row(KeyboardShortcuts.KEYCODE_NUMPAD_ENTER, 0);
		if (left != null && KeyboardShortcuts.ACTION_INPUT.equals(left.action)) {
			return next.withoutRow(KeyboardShortcuts.KEYCODE_NUMPAD_ENTER, 0);
		}
		return next;
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

	private static String displayedCommand(final Cell cell) {
		if (cell.sendInput) {
			return SEND_CURRENT_LINE;
		}
		return cell.command == null ? "" : cell.command.trim();
	}

	private static Draft filled(final String[] commands) {
		Draft draft = empty();
		for (int i = 0; i < ROSE_CELLS; i++) {
			draft.cells[i].command = commands[i];
			draft.cells[i].repeat = i != LOOK_CELL;
		}
		return draft;
	}

	private static List<Row> rows(final Draft draft, final KeyboardShortcuts base) {
		List<Row> rows = new ArrayList<Row>();
		int walk = draft.modifier == KeyboardShortcuts.MOD_ALT
				|| draft.modifier == KeyboardShortcuts.MOD_CTRL ? draft.modifier : 0;
		int lookMod = lookModifier(walk);
		for (int i = 0; i < CELLS; i++) {
			if (i == ENTER_CELL) {
				continue;
			}
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
		appendEnter(rows, draft, base);
		return rows;
	}

	/** Enter is the send-the-line action. Done does not store the label as the command. */
	private static void appendEnter(final List<Row> rows, final Draft draft, final KeyboardShortcuts base) {
		Cell cell = draft.cells[ENTER_CELL];
		String command = cell.command == null ? "" : cell.command.trim();
		if (cell.sendInput || SEND_CURRENT_LINE.equals(command)) {
			rows.add(inputRow(base));
			return;
		}
		Row existing = base.row(KeyboardShortcuts.KEYCODE_NUMPAD_ENTER, 0);
		String stored = existing == null || existing.tap == null ? "" : existing.tap.trim();
		if (command.length() > 0) {
			if (existing != null && existing.pad && command.equals(stored)
					&& !KeyboardShortcuts.ACTION_INPUT.equals(existing.action)) {
				rows.add(existing);
				return;
			}
			rows.add(line(cell.keyCode, 0, command, false, false));
			return;
		}
		if (!draft.enterCleared && SEND_CURRENT_LINE.equals(stored)) {
			rows.add(inputRow(base));
			return;
		}
		if (draft.enterCleared) {
			return;
		}
		if (existing != null && KeyboardShortcuts.ACTION_INPUT.equals(existing.action)) {
			rows.add(existing.asPad(true));
		} else if (existing != null && existing.pad) {
			rows.add(existing);
		}
	}

	private static Row inputRow(final KeyboardShortcuts base) {
		Row existing = base == null ? null : base.row(KeyboardShortcuts.KEYCODE_NUMPAD_ENTER, 0);
		if (existing != null && KeyboardShortcuts.ACTION_INPUT.equals(existing.action)) {
			return existing.asPad(true);
		}
		return Row.of(KeyboardShortcuts.KEYCODE_NUMPAD_ENTER, 0, KeyboardShortcuts.ACTION_INPUT,
				true, false, "", "").asPad(true);
	}

	private static Row line(final int key, final int modifiers, final String command,
			final boolean repeat, final boolean quiet) {
		return Row.of(key, modifiers, KeyboardShortcuts.ACTION_LINE, true, repeat, command, "", "", quiet)
				.asPad(true);
	}

	private static boolean isEnterInput(final Row row) {
		return row.keyCode == KeyboardShortcuts.KEYCODE_NUMPAD_ENTER && row.modifiers == 0
				&& KeyboardShortcuts.ACTION_INPUT.equals(row.action);
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
