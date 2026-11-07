package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.resurrection.blowtorch2.lib.speedwalk.DirectionData;
import com.resurrection.blowtorch2.lib.window.KeyboardShortcutPad.Draft;
import com.resurrection.blowtorch2.lib.window.KeyboardShortcuts.Row;

public class KeyboardShortcutPadTest {

	@Test
	public void compassAndShortStepsFillTheRoseAndCenterFiresOnce() {
		Draft compass = KeyboardShortcutPad.compass();
		assertEquals("northwest", compass.cells[0].command);
		assertEquals("north", compass.cells[1].command);
		assertEquals("look", compass.cells[4].command);
		assertEquals("down", compass.cells[9].command);
		assertEquals("up", compass.cells[10].command);
		assertEquals("down", compass.cells[11].command);
		assertFalse(compass.cells[4].repeat);
		assertTrue(compass.cells[1].repeat);
		assertEquals(15, compass.cells.length);
		assertEquals(155, compass.cells[12].keyCode);
		assertEquals(154, compass.cells[13].keyCode);
		assertEquals(160, compass.cells[14].keyCode);
		assertEquals("", compass.cells[12].command);
		assertEquals("", compass.cells[13].command);
		assertEquals("", compass.cells[14].command);
		assertFalse(compass.cells[14].sendInput);

		Draft steps = KeyboardShortcutPad.shortSteps();
		assertEquals("nw", steps.cells[0].command);
		assertEquals("n", steps.cells[1].command);
		assertEquals("d", steps.cells[9].command);
		assertEquals("u", steps.cells[10].command);
		assertEquals("", steps.cells[12].command);
		assertEquals("", steps.cells[13].command);
		assertFalse(steps.cells[14].sendInput);
	}

	@Test
	public void speedwalkUsesTheCommandNotTheRunLetter() {
		Map<String, DirectionData> map = new HashMap<String, DirectionData>();
		map.put("h", direction("h", "nw", "se"));
		map.put("n", direction("n", "n", "s"));
		Draft draft = KeyboardShortcutPad.speedwalk(map);
		assertEquals("nw", draft.cells[0].command);
		assertEquals("n", draft.cells[1].command);
		assertEquals("up", draft.cells[10].command);
		assertEquals("look", draft.cells[4].command);
		assertFalse(draft.cells[0].command.equals("h"));
		assertEquals("", draft.cells[12].command);
		assertEquals("", draft.cells[13].command);
		assertFalse(draft.cells[14].sendInput);
	}

	@Test
	public void doneReplacesPadRowsAndLeavesAnotherChord() {
		Row other = Row.of(29, KeyboardShortcuts.MOD_CTRL, KeyboardShortcuts.ACTION_LINE,
				true, false, "select", "");
		KeyboardShortcuts stored = KeyboardShortcuts.enabled(other,
				Row.of(152, 0, KeyboardShortcuts.ACTION_LINE, true, true, "old", "").asPad(true));
		Draft draft = KeyboardShortcutPad.compass();
		draft.modifier = KeyboardShortcuts.MOD_ALT;
		draft.look = true;
		draft.cells[12].command = "in";
		draft.cells[13].command = "out";
		draft.latchKey = 57;
		KeyboardShortcuts next = KeyboardShortcutPad.apply(stored, draft);

		assertEquals("select", next.row(29, KeyboardShortcuts.MOD_CTRL).tap);
		assertNull(next.row(152, 0));
		Row north = next.row(152, KeyboardShortcuts.MOD_ALT);
		assertEquals("north", north.tap);
		assertTrue(north.pad);
		assertTrue(north.quiet);
		assertTrue(north.repeat);
		Row look = next.row(152, KeyboardShortcuts.MOD_CTRL);
		assertEquals("look north", look.tap);
		assertFalse(look.quiet);
		assertFalse(look.repeat);
		assertTrue(look.pad);
		Row center = next.row(149, KeyboardShortcuts.MOD_ALT);
		assertEquals("look", center.tap);
		assertFalse(center.repeat);
		assertTrue(center.quiet);
		assertNull(next.row(149, KeyboardShortcuts.MOD_CTRL));
		assertEquals("in", next.row(155, KeyboardShortcuts.MOD_ALT).tap);
		assertEquals("out", next.row(154, KeyboardShortcuts.MOD_ALT).tap);
		assertEquals(KeyboardShortcuts.MOD_ALT, next.roseModifier());
		assertEquals(57, next.latchKey());

		draft.look = false;
		draft.cells[1].command = "";
		draft.cells[12].command = "";
		draft.cells[13].command = "";
		draft.modifier = 0;
		draft.latchKey = 57;
		KeyboardShortcuts cleared = KeyboardShortcutPad.apply(next, draft);
		assertNull(cleared.row(152, KeyboardShortcuts.MOD_ALT));
		assertNull(cleared.row(152, KeyboardShortcuts.MOD_CTRL));
		assertNull(cleared.row(155, KeyboardShortcuts.MOD_ALT));
		assertEquals(0, cleared.roseModifier());
		assertEquals(-1, cleared.latchKey());
		assertEquals("select", cleared.row(29, KeyboardShortcuts.MOD_CTRL).tap);
	}

	@Test
	public void aWalkWithASpaceHasNoLookLine() {
		Draft draft = KeyboardShortcutPad.empty();
		draft.cells[1].command = "go north";
		draft.look = true;
		draft.modifier = KeyboardShortcuts.MOD_CTRL;
		KeyboardShortcuts next = KeyboardShortcutPad.apply(KeyboardShortcuts.empty(), draft);
		assertEquals("go north", next.row(152, KeyboardShortcuts.MOD_CTRL).tap);
		assertNull(next.row(152, KeyboardShortcuts.MOD_ALT));
		assertEquals(KeyboardShortcuts.MOD_ALT, KeyboardShortcutPad.lookModifier(0));
		assertEquals(KeyboardShortcuts.MOD_CTRL, KeyboardShortcutPad.lookModifier(KeyboardShortcuts.MOD_ALT));
		assertEquals(KeyboardShortcuts.MOD_ALT, KeyboardShortcutPad.lookModifier(KeyboardShortcuts.MOD_CTRL));
	}

	@Test
	public void roseAndLatchRoundTripAndAPresetDoesNotTouchThem() {
		Draft draft = KeyboardShortcutPad.compass();
		draft.modifier = KeyboardShortcuts.MOD_CTRL;
		draft.latchKey = 113;
		KeyboardShortcuts stored = KeyboardShortcutPad.apply(KeyboardShortcuts.enabled(), draft);
		KeyboardShortcuts parsed = KeyboardShortcuts.parse(stored.format());
		assertEquals(KeyboardShortcuts.MOD_CTRL, parsed.roseModifier());
		assertEquals(113, parsed.latchKey());
		assertTrue(parsed.format().contains("rose=" + KeyboardShortcuts.MOD_CTRL));
		assertTrue(parsed.format().contains("latch=113"));
		assertTrue(parsed.row(152, KeyboardShortcuts.MOD_CTRL).pad);

		Draft again = KeyboardShortcutPad.fromRows(parsed);
		assertEquals("north", again.cells[1].command);
		assertEquals(KeyboardShortcuts.MOD_CTRL, again.modifier);
		assertEquals(113, again.latchKey);
		KeyboardShortcutPad.copyCommands(again, KeyboardShortcutPad.shortSteps());
		assertEquals("n", again.cells[1].command);
		assertEquals(KeyboardShortcuts.MOD_CTRL, again.modifier);
		assertEquals(113, again.latchKey);
	}

	@Test
	public void starSlashAndEnterStayOutOfPresetsAndEnterSendsTheLine() {
		assertEquals("Alt sends look plus that walk. A walk with a space has no look line.",
				KeyboardShortcutPad.lookCaption(0));
		assertEquals("Alt sends look plus that walk. A walk with a space has no look line.",
				KeyboardShortcutPad.lookCaption(KeyboardShortcuts.MOD_CTRL));
		assertEquals("Ctrl sends look plus that walk. A walk with a space has no look line.",
				KeyboardShortcutPad.lookCaption(KeyboardShortcuts.MOD_ALT));
		assertEquals("Pick Alt or Ctrl above. The numbers already walk, so there is nothing to keep on.",
				KeyboardShortcutPad.latchCaption(0));
		assertEquals("This key, or Alt alone, turns walking on. The same key turns it off. The bar shows Walk.",
				KeyboardShortcutPad.latchCaption(KeyboardShortcuts.MOD_ALT));
		assertEquals("This key, or Ctrl alone, turns walking on. The same key turns it off. The bar shows Walk.",
				KeyboardShortcutPad.latchCaption(KeyboardShortcuts.MOD_CTRL));
		assertEquals("nothing", KeyboardShortcutPad.latchChip(0, 57, true, "Press a key"));
		assertFalse("Needs Alt or Ctrl".equals(
				KeyboardShortcutPad.latchChip(0, -1, false, "Press a key")));

		KeyboardShortcuts seeded = KeyboardShortcuts.enabled(Row.numpadEnter());
		KeyboardShortcuts preset = KeyboardShortcutPad.apply(seeded, KeyboardShortcutPad.compass());
		Row enter = preset.row(160, 0);
		assertEquals(KeyboardShortcuts.ACTION_INPUT, enter.action);
		assertEquals("", enter.tap);
		assertNull(preset.row(155, 0));
		assertNull(preset.row(154, 0));
		assertNoSendLabel(preset);

		Draft loaded = KeyboardShortcutPad.fromRows(seeded);
		assertTrue(loaded.cells[14].sendInput);
		assertEquals("Enter\nSend the current line", KeyboardShortcutPad.cellLabel(loaded.cells[14]));
		loaded.cells[12].command = "in";
		loaded.cells[13].command = "out";
		KeyboardShortcutPad.copyCommands(loaded, KeyboardShortcutPad.shortSteps());
		assertEquals("in", loaded.cells[12].command);
		assertEquals("out", loaded.cells[13].command);
		assertTrue(loaded.cells[14].sendInput);
		assertEquals("n", loaded.cells[1].command);
		KeyboardShortcuts kept = KeyboardShortcutPad.apply(seeded, loaded);
		assertEquals("in", kept.row(155, 0).tap);
		assertEquals("out", kept.row(154, 0).tap);
		assertEquals(KeyboardShortcuts.ACTION_INPUT, kept.row(160, 0).action);
		assertEquals("", kept.row(160, 0).tap);

		KeyboardShortcutPad.edit(loaded, 14, "Send the current line", true);
		KeyboardShortcuts labeled = KeyboardShortcutPad.apply(kept, loaded);
		assertEquals(KeyboardShortcuts.ACTION_INPUT, labeled.row(160, 0).action);
		assertEquals("", labeled.row(160, 0).tap);
		assertNoSendLabel(labeled);

		KeyboardShortcutPad.edit(loaded, 14, "smile", false);
		assertFalse(loaded.cells[14].sendInput);
		assertEquals("smile", loaded.cells[14].command);
		assertEquals("Enter\nsmile", KeyboardShortcutPad.cellLabel(loaded.cells[14]));
		KeyboardShortcuts once = KeyboardShortcutPad.apply(labeled, loaded);
		assertEquals(KeyboardShortcuts.ACTION_LINE, once.row(160, 0).action);
		assertEquals("smile", once.row(160, 0).tap);
		Draft reopened = KeyboardShortcutPad.fromRows(once);
		assertFalse(reopened.cells[14].sendInput);
		assertEquals("smile", reopened.cells[14].command);
		KeyboardShortcuts twice = KeyboardShortcutPad.apply(once, reopened);
		assertEquals("smile", twice.row(160, 0).tap);
		assertEquals(KeyboardShortcuts.ACTION_LINE, twice.row(160, 0).action);

		KeyboardShortcutPad.edit(loaded, 14, "", false);
		KeyboardShortcutPad.edit(loaded, 12, "", false);
		KeyboardShortcuts cleared = KeyboardShortcutPad.apply(labeled, loaded);
		assertNull(cleared.row(160, 0));
		assertNull(cleared.row(155, 0));
		assertEquals("out", cleared.row(154, 0).tap);
		assertEquals("n", cleared.row(152, 0).tap);
	}

	private static void assertNoSendLabel(final KeyboardShortcuts stored) {
		for (int i = 0; i < stored.rows().size(); i++) {
			assertFalse("Send the current line".equals(stored.rows().get(i).tap));
		}
	}

	private static DirectionData direction(final String letter, final String command, final String reverse) {
		DirectionData data = new DirectionData(letter, command);
		data.setReverse(reverse);
		return data;
	}
}
