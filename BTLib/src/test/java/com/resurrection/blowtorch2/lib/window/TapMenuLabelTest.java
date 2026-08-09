package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Labels for the menu a tappable word opens. The menu sits on top of the text
 * it is about, so a row must not stretch it: a long command is cut and ends in
 * (...), and only the label is cut — the command sent is the whole one.
 */
public class TapMenuLabelTest {

	@Test
	public void shortCommandsAreShownWhole() {
		assertEquals("look yeti", MainWindow.shortenForMenu("look yeti"));
		assertEquals("get crate", MainWindow.shortenForMenu("  get crate  "));
	}

	@Test
	public void aLongCommandIsCutAndMarked() {
		String long1 = "put all artifacts in 2.trail;buy energy;buy energy";
		String label = MainWindow.shortenForMenu(long1);
		assertTrue(label.endsWith("(...)"));
		assertTrue(label.length() <= MainWindow.TAP_MENU_MAX_CHARS + "(...)".length());
		assertTrue(long1.startsWith(label.substring(0, label.length() - "(...)".length())));
	}

	/** Cut on a word when one is near the limit, not in the middle of it. */
	@Test
	public void theCutPrefersAWordBoundary() {
		assertEquals("kill the enormous(...)",
				MainWindow.shortenForMenu("kill the enormous yeti of the north"));
	}

	@Test
	public void nullIsEmpty() {
		assertEquals("", MainWindow.shortenForMenu(null));
	}
}
