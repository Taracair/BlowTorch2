package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/**
 * One command list. Switching worlds saves the one on screen, then loads
 * the other. An empty file is an empty list.
 */
public class HistoryWorldTest {

	@Test
	public void worldADoesNotRemainLoadedAfterWorldB() {
		CommandKeeper live = new CommandKeeper(10);
		live.addCommand("north");
		live.addCommand("look");
		HistoryWorld worlds = new HistoryWorld();
		worlds.pinShowing("world-a");

		String saveAs = worlds.leaveFor("world-b");
		assertEquals("world-a", saveAs);
		List<String> saved = live.copyCommands();
		live.replace(Arrays.asList("say hi"));

		assertEquals("world-b", worlds.showing());
		assertEquals("say hi", live.peek(1));
		assertNull(live.peek(2));
		assertEquals("look", saved.get(0));
		assertEquals("north", saved.get(1));
	}

	@Test
	public void anEmptyFileIsAnEmptyList() {
		CommandKeeper live = new CommandKeeper(10);
		live.addCommand("look");
		HistoryWorld worlds = new HistoryWorld();
		worlds.pinShowing("world-a");

		assertEquals("world-a", worlds.leaveFor("world-b"));
		live.replace(null);

		assertNull(live.peek(1));
		assertEquals("", live.peekNewest());
		assertEquals("world-b", worlds.showing());
	}

	@Test
	public void aLaterPinDoesNotMoveTheListOffTheWorldOnScreen() {
		HistoryWorld worlds = new HistoryWorld();
		worlds.pinShowing("world-a");
		worlds.pinShowing("world-b");

		assertEquals("world-a", worlds.leaveFor("world-b"));
		assertEquals("world-b", worlds.showing());
		assertNull(worlds.leaveFor("world-b"));
	}

	@Test
	public void theLoadedNameIsWhatGetsSavedWhenTheIntentAlreadySaysTheDestination() {
		assertEquals("world-a", HistoryWorld.parkedName("world-a", "world-b"));
		assertNull(HistoryWorld.parkedName("world-b", "world-b"));
		assertNull(HistoryWorld.parkedName(null, "world-b"));
		assertNull(HistoryWorld.parkedName("world-a", ""));
		assertNull(HistoryWorld.parkedName("world-a", null));
	}
}
