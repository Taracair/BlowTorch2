package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import com.resurrection.blowtorch2.lib.window.KeyboardShortcutButtons.Button;

/** A copy of the current set. Expected labels come from the button fields, not a live set. */
public class KeyboardShortcutButtonsTest {

	@Test
	public void parseKeepsLabelCommandAndHold() {
		List<Button> buttons = KeyboardShortcutButtons.parse(
				"[{\"label\":\"Look\",\"command\":\"look\",\"hold\":\"look around\"}]");
		assertEquals(1, buttons.size());
		assertEquals("Look", buttons.get(0).listLabel());
		assertEquals("", buttons.get(0).name);
		assertEquals("", buttons.get(0).id);
		assertEquals("look", buttons.get(0).command);
		assertEquals("look around", buttons.get(0).hold);
	}

	@Test
	public void searchKeepsAButtonWhoseNameIdCommandOrHoldMatches() {
		List<Button> buttons = KeyboardShortcutButtons.parse(
				"[{\"label\":\"N\",\"name\":\"step north\",\"id\":\"b12\",\"command\":\"north\",\"hold\":\"\"},"
						+ "{\"label\":\"Look\",\"name\":\"\",\"id\":\"b4\",\"command\":\"look\",\"hold\":\"look around\"}]");
		assertEquals(2, KeyboardShortcutButtons.matching(buttons, "").size());
		assertEquals(2, KeyboardShortcutButtons.matching(buttons, "   ").size());
		List<Button> byCommand = KeyboardShortcutButtons.matching(buttons, "NORTH");
		assertEquals(1, byCommand.size());
		assertEquals("north", byCommand.get(0).command);
		assertEquals("b12", KeyboardShortcutButtons.matching(buttons, "b12").get(0).id);
		assertEquals("b12", KeyboardShortcutButtons.matching(buttons, "step").get(0).id);
		List<Button> byHold = KeyboardShortcutButtons.matching(buttons, "around");
		assertEquals(1, byHold.size());
		assertEquals("Look", byHold.get(0).listLabel());
		assertEquals("b4", byHold.get(0).id);
		assertTrue(KeyboardShortcutButtons.matching(buttons, "smile").isEmpty());
	}

	@Test
	public void aBlankButtonIsDroppedAndAMissingLabelUsesTheCommand() {
		List<Button> buttons = KeyboardShortcutButtons.parse(
				"[{\"label\":\"\",\"command\":\"\",\"hold\":\"\"},"
						+ "{\"label\":\"\",\"command\":\"north\",\"hold\":\"\"}]");
		assertEquals(1, buttons.size());
		assertEquals("north", buttons.get(0).listLabel());
	}

	@Test
	public void garbageIsAnEmptyList() {
		assertTrue(KeyboardShortcutButtons.parse(null).isEmpty());
		assertTrue(KeyboardShortcutButtons.parse("").isEmpty());
		assertTrue(KeyboardShortcutButtons.parse("not json").isEmpty());
	}
}
