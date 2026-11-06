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
		assertEquals("look", buttons.get(0).command);
		assertEquals("look around", buttons.get(0).hold);
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
