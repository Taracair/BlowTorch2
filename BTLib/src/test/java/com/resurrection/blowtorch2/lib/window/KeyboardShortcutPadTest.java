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

		Draft steps = KeyboardShortcutPad.shortSteps();
		assertEquals("nw", steps.cells[0].command);
		assertEquals("n", steps.cells[1].command);
		assertEquals("d", steps.cells[9].command);
		assertEquals("u", steps.cells[10].command);
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
		draft.inOut = true;
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
		draft.inOut = false;
		draft.cells[1].command = "";
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

	private static DirectionData direction(final String letter, final String command, final String reverse) {
		DirectionData data = new DirectionData(letter, command);
		data.setReverse(reverse);
		return data;
	}
}
