package com.resurrection.blowtorch2.lib.timer;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Timer editor {@code ?} swallowed the CONDITIONS canvas essay (variables as
 * session sticky notes) so the form can keep a one-liner status.
 */
public class TimerEditorHelpTest {

	@Test
	public void conditionsEssayMentionsVariables() {
		assertTrue(TimerEditorDialog.TIMER_HELP_TEXT.contains("CONDITIONS"));
		assertTrue(TimerEditorDialog.TIMER_HELP_TEXT.contains("sticky notes"));
		assertTrue(TimerEditorDialog.TIMER_HELP_TEXT.contains("${name}"));
	}
}
