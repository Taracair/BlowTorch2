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
		assertTrue(EditorHelp.TIMERS.contains("CONDITIONS"));
	}

	@Test
	public void triggerEditorConditionsEssayHasTheCanvasExample() {
		assertTrue(EditorHelp.TRIGGER_EDITOR_CONDITIONS.contains("CONDITIONS"));
		assertTrue(EditorHelp.TRIGGER_EDITOR_CONDITIONS.contains("_cerb"));
		assertTrue(EditorHelp.TRIGGER_EDITOR_CONDITIONS.contains("An extra gate"));
		assertTrue(EditorHelp.TRIGGER_EDITOR_CONDITIONS.contains("${name}"));
	}
}
