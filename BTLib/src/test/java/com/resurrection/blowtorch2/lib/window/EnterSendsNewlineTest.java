package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Enter under Grow Input Bar must send, not leave CR/LF sitting in the bar for
 * a MUD pager that is waiting on a blank line.
 */
public class EnterSendsNewlineTest {

	@Test
	public void softEnterIsOnlyCarriageReturns() {
		assertTrue(BetterEditText.isSoftEnterNewline("\n"));
		assertTrue(BetterEditText.isSoftEnterNewline("\r\n"));
		assertTrue(BetterEditText.isSoftEnterNewline("\n\n"));
		assertFalse(BetterEditText.isSoftEnterNewline(" "));
		assertFalse(BetterEditText.isSoftEnterNewline("q"));
		assertFalse(BetterEditText.isSoftEnterNewline("look\n"));
		assertFalse(BetterEditText.isSoftEnterNewline(""));
		assertFalse(BetterEditText.isSoftEnterNewline(null));
	}

	@Test
	public void newlineOnlyCommandsBecomeBareCrlf() {
		assertTrue(MainWindow.isNewlineOnlyCommand(""));
		assertTrue(MainWindow.isNewlineOnlyCommand(null));
		assertTrue(MainWindow.isNewlineOnlyCommand("\n"));
		assertTrue(MainWindow.isNewlineOnlyCommand("\n\n"));
		assertFalse(MainWindow.isNewlineOnlyCommand(" "));
		assertFalse(MainWindow.isNewlineOnlyCommand("q"));
		assertFalse(MainWindow.isNewlineOnlyCommand("look"));
	}
}
