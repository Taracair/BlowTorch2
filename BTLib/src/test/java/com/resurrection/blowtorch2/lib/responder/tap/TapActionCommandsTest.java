package com.resurrection.blowtorch2.lib.responder.tap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;

import org.junit.Test;

/**
 * A tap action carries a list of commands so one word can offer a menu, but the
 * ordinary case is still one command and has to behave exactly as it did when
 * the field was a single String: tap sends it, no menu.
 */
public class TapActionCommandsTest {

	@Test
	public void aFreshActionHasExactlyOneCommandAndNoMenu() {
		TapAction a = new TapAction();
		assertEquals(1, a.getCommands().size());
		assertEquals(TapAction.DEFAULT_COMMAND, a.getCommand());
		assertFalse(a.hasMenu());
	}

	@Test
	public void setCommandReplacesTheWholeList() {
		TapAction a = new TapAction();
		a.addCommand("kill $word");
		a.setCommand("look $word");
		assertEquals(Arrays.asList("look $word"), a.getCommands());
		assertFalse(a.hasMenu());
	}

	@Test
	public void theFirstCommandIsWhatAPlainTapSends() {
		TapAction a = new TapAction();
		a.setCommands(Arrays.asList("kill $word", "skin $word", "get $word"));
		assertEquals("kill $word", a.getCommand());
		assertTrue(a.hasMenu());
	}

	@Test
	public void blankRowsFromTheEditorAreDropped() {
		TapAction a = new TapAction();
		a.setCommands(Arrays.asList("kill $word", "   ", "", null, " skin $word "));
		assertEquals(Arrays.asList("kill $word", "skin $word"), a.getCommands());
	}

	/** A word that lights up and then does nothing is worse than a default. */
	@Test
	public void anEmptyListFallsBackToTheDefaultCommand() {
		TapAction a = new TapAction();
		a.setCommands(new ArrayList<String>());
		assertEquals(Arrays.asList(TapAction.DEFAULT_COMMAND), a.getCommands());

		a.setCommands(null);
		assertEquals(Arrays.asList(TapAction.DEFAULT_COMMAND), a.getCommands());

		a.setCommand("");
		assertEquals(TapAction.DEFAULT_COMMAND, a.getCommand());
	}

	/** Triggers are copied on every edit; the copy must not share the list. */
	@Test
	public void copyDoesNotShareTheCommandList() {
		TapAction a = new TapAction();
		a.setCommands(Arrays.asList("kill $word", "skin $word"));
		TapAction b = (TapAction) a.copy();
		assertEquals(a.getCommands(), b.getCommands());

		b.addCommand("get $word");
		assertEquals(2, a.getCommands().size());
		assertEquals(3, b.getCommands().size());
	}

	@Test
	public void equalityFollowsEveryCommand() {
		TapAction a = new TapAction();
		a.setCommands(Arrays.asList("kill $word", "skin $word"));
		TapAction b = (TapAction) a.copy();
		assertTrue(a.equals(b));

		b.addCommand("get $word");
		assertFalse(a.equals(b));
	}
}
