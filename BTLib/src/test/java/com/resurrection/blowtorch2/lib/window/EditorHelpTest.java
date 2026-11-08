package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;


/**
 * The list {@code ?} texts and the trigger-editor CONDITIONS essay that moved
 * off the canvas. Chrome of {@link EditorHelp#show} is visual; these strings
 * are what the player is supposed to still be able to read.
 */
public class EditorHelpTest {

	@Test
	public void listTextsKeepTheThreeHeadings() {
		assertTrue(EditorHelp.ALIASES.contains("THE TWO FIELDS"));
		assertTrue(EditorHelp.TRIGGERS.contains("CONDITIONS"));
		assertTrue(EditorHelp.TRIGGERS.contains("KEEP GOING"));
		assertTrue(EditorHelp.TRIGGERS.contains("ORDER"));
		assertTrue(EditorHelp.TRIGGERS.contains("not a second pattern"));
		assertTrue(EditorHelp.TRIGGERS.contains("not \"on top\""));
		assertTrue(EditorHelp.TRIGGER_ORDER.contains("colour at 11"));
		assertTrue(EditorHelp.TRIGGERS.contains(EditorHelp.TRIGGER_ORDER));
		assertTrue(EditorHelp.TRIGGERS.contains("WHO ELSE FIRES"));
		assertTrue(EditorHelp.TRIGGERS.contains("TWO PATTERNS ON ONE LINE"));
		assertTrue(EditorHelp.TRIGGERS.contains("It tries one line"));
		assertTrue(EditorHelp.TRIGGERS.contains("shade title is the world name"));
		assertTrue(EditorHelp.TIMERS.contains("timer has no game line"));
		assertTrue(EditorHelp.SHORTCUTS.contains(KeyboardShortcuts.NOTICE));
		assertTrue(EditorHelp.SHORTCUTS.contains("450 ms"));
		assertTrue(EditorHelp.SHORTCUTS.contains("200 ms"));
		assertTrue(EditorHelp.SHORTCUTS.contains(".shortcuts edit"));
		assertTrue(EditorHelp.SHORTCUTS.contains("Tap that row again"));
		assertTrue(EditorHelp.SHORTCUTS.contains("sends nothing"));
		assertTrue(EditorHelp.NUMBER_PAD.contains("Speedwalk Directions"));
		assertTrue(EditorHelp.NUMBER_PAD.contains("Done"));
		assertTrue(EditorHelp.SHORTCUTS.contains(
				"**Partially experimental**\n"
						+ "Android does not read every keyboard the same way. What a key does also depends on "
						+ "the keyboard app installed on the phone."));
		assertTrue(!EditorHelp.SHORTCUTS.contains("SwiftKey"));
		assertTrue(!EditorHelp.SHORTCUTS.contains("FUTO"));
		assertTrue(!EditorHelp.SHORTCUTS.contains("HoneyBoard"));
	}

	@Test
	public void shortcutHelpUsesTheFormSentences() {
		assertEquals(
				"A hold command and Repeat cannot both be on. Typing a hold command turns Repeat off. "
						+ "Turning Repeat on clears the hold command.",
				EditorHelp.HOLD_AND_REPEAT);
		assertEquals(
				"Show this key's line, including when Local Echo? is off. Off, the game still receives it.",
				EditorHelp.QUIET_CAPTION);
		assertEquals("Follow a button", EditorHelp.PICKER_TITLE);
		assertEquals("Follow a button that already exists on your screen", EditorHelp.USE_BUTTON);
		assertTrue(EditorHelp.SHORTCUTS.contains(EditorHelp.HOLD_AND_REPEAT));
		assertTrue(EditorHelp.SHORTCUTS.contains(EditorHelp.QUIET_CAPTION));
		assertTrue(EditorHelp.SHORTCUTS.contains(EditorHelp.USE_BUTTON));
		assertTrue(EditorHelp.SHORTCUTS.contains(
				"The list shows the id, and the search box matches it."));
		assertTrue(EditorHelp.SHORTCUTS.contains("Every set starts closed. Tap the set to open it."));
		assertTrue(EditorHelp.SHORTCUTS.contains("Ctrl+K can send kill goblin."));
		assertTrue(EditorHelp.SHORTCUTS.contains(".kb insert say"));
		assertTrue(EditorHelp.SHORTCUTS.contains("F1 starts empty."));
		assertTrue(EditorHelp.SHORTCUTS.contains("The row under Keyboard."));
		assertTrue(!EditorHelp.SHORTCUTS.contains("The first row in this list."));
		assertTrue(EditorHelp.SHORTCUTS.contains(".scrollnew"));
		assertTrue(EditorHelp.SHORTCUTS.contains("Ctrl+Enter and Ctrl+End"));
		assertTrue(EditorHelp.SHORTCUTS.contains(".scrollv 10"));
		assertTrue(EditorHelp.SHORTCUTS.contains(".scrollh 10"));
		assertTrue(EditorHelp.SHORTCUTS.contains(".kb start"));
		assertTrue(EditorHelp.SHORTCUTS.contains("Right Alt+a still inserts ą."));
		assertTrue(!EditorHelp.SHORTCUTS.contains("Page Up"));
		assertTrue(!EditorHelp.SHORTCUTS.contains("Page Down"));
		assertEquals(EditorHelp.SHORTCUTS.replace("**", ""), EditorHelp.shortcutsPlain());
		assertTrue(!EditorHelp.SHORTCUTS.contains("Repeat and a hold line are not both on"));
		assertTrue(!EditorHelp.SHORTCUTS.contains("Filling a hold line turns repeat off"));

		assertTrue(EditorHelp.NUMBER_PAD.contains(
				"Alt sends look plus that walk. A walk with a space has no look line."));
		assertTrue(EditorHelp.NUMBER_PAD.contains(
				"Ctrl sends look plus that walk. A walk with a space has no look line."));
		assertTrue(EditorHelp.NUMBER_PAD.contains(
				"Pick Alt or Ctrl above. The numbers already walk, so there is nothing to keep on."));
		assertTrue(EditorHelp.NUMBER_PAD.contains(
				"This key, or Alt alone, turns walking on. The same key turns it off. The bar shows Pad walks."));
		assertTrue(EditorHelp.NUMBER_PAD.contains(
				"This key, or Ctrl alone, turns walking on. The same key turns it off. The bar shows Pad walks."));
		assertTrue(EditorHelp.NUMBER_PAD.contains("Send the current line"));
		assertTrue(EditorHelp.NUMBER_PAD.contains("An empty command is not a shortcut."));
		assertTrue(!EditorHelp.NUMBER_PAD.contains("In and out"));
		assertTrue(!EditorHelp.NUMBER_PAD.contains("the other modifier"));
		assertTrue(!EditorHelp.NUMBER_PAD.contains("the other choice"));
		assertTrue(!EditorHelp.NUMBER_PAD.contains("Needs Alt or Ctrl"));
		assertTrue(EditorHelp.SHORTCUTS.contains("**Name**\nWhat this list shows."));
		assertTrue(EditorHelp.SHORTCUTS.contains("**Repeat**\n"));
		assertTrue(EditorHelp.SHORTCUTS.contains(
				"**Local echo**\n" + EditorHelp.QUIET_CAPTION));
		assertTrue(EditorHelp.SHORTCUTS.contains("A password prompt still hides it."));
		assertTrue(!EditorHelp.SHORTCUTS.contains("The same words"));
		assertTrue(EditorHelp.KEYBOARD.contains(EditorHelp.QUIET_CAPTION));
		assertTrue(!EditorHelp.KEYBOARD.contains("for that one key"));
		assertTrue(EditorHelp.NUMBER_PAD.contains("**Walk lock**\n"));
		assertTrue(!EditorHelp.SHORTCUTS.contains("Latch"));
		assertTrue(!EditorHelp.NUMBER_PAD.contains("Latch"));
	}

	@Test
	public void aMarkedOptionNameIsBoldAndTheNoteUnderItIsNot() {
		EditorHelp.Emphasis shown = EditorHelp.emphasis("**Name**\nWhat this list shows.");
		assertEquals("Name\nWhat this list shows.", shown.text);
		assertEquals(1, shown.starts.length);
		assertEquals(0, shown.starts[0]);
		assertEquals(4, shown.ends[0]);
		EditorHelp.Emphasis plain = EditorHelp.emphasis("Aliases stay plain.");
		assertEquals("Aliases stay plain.", plain.text);
		assertEquals(0, plain.starts.length);
	}

	@Test
	public void triggerEditorConditionsEssayHasTheCanvasExample() {
		assertTrue(EditorHelp.TRIGGER_EDITOR_CONDITIONS.contains("CONDITIONS"));
		assertTrue(EditorHelp.TRIGGER_EDITOR_CONDITIONS.contains("combat_mode"));
		assertTrue(EditorHelp.TRIGGER_EDITOR_CONDITIONS.contains("matches this line"));
		assertTrue(EditorHelp.TRIGGER_EDITOR_CONDITIONS.contains("${name}"));
		assertTrue(EditorHelp.TRIGGER_EDITOR_CONDITIONS.contains("OPEN AND CLOSED"));
		assertTrue("help must not name a profile-private trigger",
				!EditorHelp.TRIGGER_EDITOR_CONDITIONS.contains("_cerb"));
	}

	@Test
	public void chatMyLinesExplainsNameNotChannelAndNamesNoLiveWorld() {
		assertTrue(EditorHelp.CHAT_MY_LINES.contains("WHAT TO TYPE"));
		assertTrue(EditorHelp.CHAT_MY_LINES.contains("WORLDS PRINT CHAT DIFFERENTLY"));
		assertTrue(EditorHelp.CHAT_MY_LINES.contains("Ada"));
		assertTrue(EditorHelp.CHAT_MY_LINES.contains("MY LINES"));
		assertTrue(EditorHelp.CHAT_MY_LINES.contains("REPLY"));
		assertTrue(EditorHelp.CHAT_MY_LINES.contains("tell Bob $text"));
		assertTrue(EditorHelp.CHAT_MY_LINES.contains("ooc $text"));
		assertTrue(EditorHelp.CHAT_MY_LINES.contains("[ooc]"));
		assertTrue(EditorHelp.CHAT_MY_LINES.contains("    Reply:    $text"));
		assertTrue(EditorHelp.CHAT_MY_LINES.contains("C $1"));
		assertTrue(EditorHelp.CHAT_MY_LINES.contains("NOT A SEND TEMPLATE"));
		assertTrue(EditorHelp.CHAT_MY_LINES.contains("SEND TO THREAD"));
		assertTrue(EditorHelp.CHAT_MY_LINES.contains("SEVERAL FORMS"));
		assertTrue(EditorHelp.CHAT_MY_LINES.contains("Ada says; Ada asks"));
		assertTrue("My lines help must not name a live guild",
				!EditorHelp.CHAT_MY_LINES.contains("VERMIN"));
		assertTrue("My lines help must not name a live player",
				!EditorHelp.CHAT_MY_LINES.contains("Taracair"));
	}
}
