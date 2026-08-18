package com.resurrection.blowtorch2.lib.service.plugin.settings;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The widget {@code ?} is the only place that explains Source vs a trigger.
 */
public class GaugeWidgetsHelpTest {

	@Test
	public void helpWalksProtocolRegexAndTrigger() {
		String h = GaugeWidgetsDialog.EDIT_HELP;
		assertTrue(h.contains("does not have a trigger of its own"));
		assertTrue(h.contains(".widget source hp mcp hp maxhp"));
		assertTrue(h.contains("#$#dns-org-hellmoo-status-update"));
		assertTrue(h.contains("HP:"));
		assertTrue(h.contains("Set Variable"));
		assertTrue(h.contains(".widget source hp var hp maxhp"));
		assertTrue(h.contains("upleft"));
		assertTrue(h.contains(".widget tap hp score"));
		assertTrue(h.contains(".widget swipe hp ne look n"));
	}
}
