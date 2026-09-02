package com.resurrection.blowtorch2.lib.window;

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
		assertTrue(EditorHelp.TRIGGERS.contains("WHO ELSE FIRES"));
		assertTrue(EditorHelp.TIMERS.contains("CONDITIONS"));
	}

	@Test
	public void triggerEditorConditionsEssayHasTheCanvasExample() {
		assertTrue(EditorHelp.TRIGGER_EDITOR_CONDITIONS.contains("CONDITIONS"));
		assertTrue(EditorHelp.TRIGGER_EDITOR_CONDITIONS.contains("combat_mode"));
		assertTrue(EditorHelp.TRIGGER_EDITOR_CONDITIONS.contains("An extra gate"));
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
