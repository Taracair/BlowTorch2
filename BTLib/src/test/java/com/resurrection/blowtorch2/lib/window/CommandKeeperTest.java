package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Stepping back through what you have sent. */
public class CommandKeeperTest {

	@Test
	public void theNewestCommandIsTheOneJustSent() {
		CommandKeeper k = new CommandKeeper(10);
		k.addCommand("look");
		k.addCommand("list jewelry");
		assertEquals("list jewelry", k.peekNewest());
	}

	@Test
	public void nothingSentYetPeeksAsEmpty() {
		assertEquals("", new CommandKeeper(10).peekNewest());
	}

	@Test
	public void peekingDoesNotCountAsAStepBack() {
		// The whole point of peekNewest: MainWindow asks it on every ↑ to decide
		// whether the bar is already showing the newest command. If asking moved
		// the cursor, the answer would change the thing it was asked about.
		CommandKeeper k = new CommandKeeper(10);
		k.addCommand("look");
		k.addCommand("list jewelry");
		k.peekNewest();
		k.peekNewest();
		assertEquals("list jewelry", k.getNext());
		k.peekNewest();
		assertEquals("look", k.getNext());
	}

	@Test
	public void steppingBackTwiceWalksBackTwoCommands() {
		CommandKeeper k = new CommandKeeper(10);
		k.addCommand("north");
		k.addCommand("look");
		k.addCommand("list jewelry");
		assertEquals("list jewelry", k.getNext());
		assertEquals("look", k.getNext());
		assertEquals("north", k.getNext());
	}

	@Test
	public void sendingTheSameCommandTwiceKeepsOneEntry() {
		CommandKeeper k = new CommandKeeper(10);
		k.addCommand("look");
		k.addCommand("look");
		assertEquals("look", k.getNext());
		// Not "look" again from a second copy: the list wraps to the newest.
		assertEquals("look", k.getNext());
		assertEquals("look", k.peekNewest());
	}
}
