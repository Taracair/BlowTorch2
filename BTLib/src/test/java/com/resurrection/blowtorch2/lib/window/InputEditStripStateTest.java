package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

/**
 * The Edit tools strip is activity chrome. One SharedPreferences boolean for
 * every world is why opening it on Darkwind left it open on the next world.
 */
public class InputEditStripStateTest {

	@Test
	public void darkwindAndStickmudDoNotShareAKey() {
		assertEquals("expanded|Darkwind", MainWindow.editExpandedPrefKey("Darkwind"));
		assertEquals("expanded|StickMUD", MainWindow.editExpandedPrefKey("StickMUD"));
		assertFalse(MainWindow.editExpandedPrefKey("Darkwind")
				.equals(MainWindow.editExpandedPrefKey("StickMUD")));
	}

	@Test
	public void aMissingDisplayDoesNotReuseTheOldGlobalKey() {
		assertEquals("expanded|", MainWindow.editExpandedPrefKey(null));
		assertEquals("expanded|", MainWindow.editExpandedPrefKey(""));
	}
}
