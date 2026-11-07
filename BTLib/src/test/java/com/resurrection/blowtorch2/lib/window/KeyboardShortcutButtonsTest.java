package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import com.resurrection.blowtorch2.lib.window.KeyboardShortcutButtons.Button;
import com.resurrection.blowtorch2.lib.window.KeyboardShortcutButtons.Group;

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
		assertTrue(KeyboardShortcutButtons.groups("not json").isEmpty());
	}

	@Test
	public void groupsKeepEachSetAndSearchMatchesTheSetName() {
		String raw = "[{\"set\":\"fight\",\"buttons\":["
				+ "{\"label\":\"STOP\",\"name\":\"halt\",\"id\":\"b1\",\"command\":\"stop\",\"hold\":\"\"}]},"
				+ "{\"set\":\"move\",\"buttons\":["
				+ "{\"label\":\"N\",\"name\":\"step\",\"id\":\"b2\",\"command\":\"north\",\"hold\":\"bash\"},"
				+ "{\"label\":\"\",\"command\":\"\",\"hold\":\"\",\"swipe\":\"up\",\"flip\":\"look\"}]}]";
		List<Group> groups = KeyboardShortcutButtons.groups(raw);
		assertEquals(2, groups.size());
		assertEquals("fight", groups.get(0).name);
		assertEquals("stop", groups.get(0).buttons.get(0).command);
		assertEquals("fight", groups.get(0).buttons.get(0).set);
		assertEquals(1, groups.get(1).buttons.size());
		assertEquals("bash", groups.get(1).buttons.get(0).hold);
		assertEquals(2, KeyboardShortcutButtons.matchingGroups(groups, "").size());
		assertEquals(2, KeyboardShortcutButtons.matchingGroups(groups, "   ").size());
		List<Group> bySet = KeyboardShortcutButtons.matchingGroups(groups, "FIGHT");
		assertEquals(1, bySet.size());
		assertEquals("STOP", bySet.get(0).buttons.get(0).listLabel());
		assertEquals("b2", KeyboardShortcutButtons.matchingGroups(groups, "b2").get(0).buttons.get(0).id);
		assertEquals("bash", KeyboardShortcutButtons.matchingGroups(groups, "BASH").get(0).buttons.get(0).hold);
		assertEquals("step", KeyboardShortcutButtons.matchingGroups(groups, "step").get(0).buttons.get(0).name);
		assertTrue(KeyboardShortcutButtons.matchingGroups(groups, "smile").isEmpty());
		assertTrue(KeyboardShortcutButtons.groups("[{\"set\":\"empty\",\"buttons\":[]}]").isEmpty());
	}

	@Test
	public void aFlatListIsOneGroupWithNoName() {
		List<Group> groups = KeyboardShortcutButtons.groups(
				"[{\"label\":\"Look\",\"command\":\"look\",\"hold\":\"\"}]");
		assertEquals(1, groups.size());
		assertEquals("", groups.get(0).name);
		assertEquals("look", groups.get(0).buttons.get(0).command);
	}
}
