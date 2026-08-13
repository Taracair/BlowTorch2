package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;

import org.junit.Test;

import com.resurrection.blowtorch2.lib.window.GameplayMenuAdapter.Row;
import com.resurrection.blowtorch2.lib.window.GameplayMenuAdapter.Section;

/**
 * Grouping for the ⋮ menu. Ids, not Menu order: Lua Button Sets is 401
 * and sits between Options and Edit buttons in a flat list.
 */
public class GameplayMenuAdapterTest {

	/** Visible items in Menu iteration order (order category, not insert). */
	private static final int[] DEFAULT_IDS = {
			100, 200, 300, 400, 401, 450, 500, 520, 600, 700, 800, 900,
			1050, 1100, 1500, 1600, 1700
	};
	private static final String[] DEFAULT_TITLES = {
			"Aliases", "Triggers", "Timers", "Options", "Button Sets",
			"Edit buttons", "Speedwalk Directions", "Map", "Plugins",
			"Reconnect", "Disconnect", "Quit", "Search scrollback",
			"Reload Settings", "Crash report", "About", "Help"
	};

	@Test
	public void knownIdsMapToTheSignedOffSections() {
		assertEquals(Section.EDITORS, GameplayMenuAdapter.sectionFor(100, "Aliases"));
		assertEquals(Section.EDITORS, GameplayMenuAdapter.sectionFor(200, "Triggers"));
		assertEquals(Section.EDITORS, GameplayMenuAdapter.sectionFor(300, "Timers"));
		assertEquals(Section.EDITORS, GameplayMenuAdapter.sectionFor(401, "Button Sets"));
		assertEquals(Section.EDITORS, GameplayMenuAdapter.sectionFor(450, "Edit buttons"));
		assertEquals(Section.SESSION, GameplayMenuAdapter.sectionFor(400, "Options"));
		assertEquals(Section.SESSION, GameplayMenuAdapter.sectionFor(500, "Speedwalk Directions"));
		assertEquals(Section.SESSION, GameplayMenuAdapter.sectionFor(520, "Map"));
		assertEquals(Section.SESSION, GameplayMenuAdapter.sectionFor(600, "Plugins"));
		assertEquals(Section.CONNECTION, GameplayMenuAdapter.sectionFor(700, "Reconnect"));
		assertEquals(Section.CONNECTION, GameplayMenuAdapter.sectionFor(800, "Disconnect"));
		assertEquals(Section.CONNECTION, GameplayMenuAdapter.sectionFor(900, "Quit"));
		assertEquals(Section.TOOLS, GameplayMenuAdapter.sectionFor(1050, "Search scrollback"));
		assertEquals(Section.TOOLS, GameplayMenuAdapter.sectionFor(1100, "Reload Settings"));
		assertEquals(Section.ABOUT, GameplayMenuAdapter.sectionFor(1500, "Crash report"));
		assertEquals(Section.ABOUT, GameplayMenuAdapter.sectionFor(1600, "About"));
		assertEquals(Section.ABOUT, GameplayMenuAdapter.sectionFor(1700, "Help"));
	}

	@Test
	public void defaultMenuHasFiveHeadersAndNoMore() {
		ArrayList<Row> rows = GameplayMenuAdapter.buildRows(DEFAULT_IDS, DEFAULT_TITLES);
		assertEquals("EDITORS", headerSequence(rows)[0]);
		assertEquals("SESSION", headerSequence(rows)[1]);
		assertEquals("CONNECTION", headerSequence(rows)[2]);
		assertEquals("TOOLS", headerSequence(rows)[3]);
		assertEquals("ABOUT", headerSequence(rows)[4]);
		assertEquals(5, headerSequence(rows).length);
	}

	@Test
	public void optionsSitsInSessionNotBetweenTimersAndButtonSets() {
		ArrayList<Row> rows = GameplayMenuAdapter.buildRows(DEFAULT_IDS, DEFAULT_TITLES);
		assertEquals(Section.SESSION, GameplayMenuAdapter.sectionFor(400, "Options"));
		int options = findItem(rows, 400);
		int timers = findItem(rows, 300);
		int buttonSets = findItem(rows, 401);
		assertTrue(timers < buttonSets);
		assertTrue(buttonSets < options);
		assertEquals("SESSION", nearestHeader(rows, options));
		assertEquals("EDITORS", nearestHeader(rows, buttonSets));
	}

	@Test
	public void helpIsGroupedUnderAbout() {
		ArrayList<Row> rows = GameplayMenuAdapter.buildRows(DEFAULT_IDS, DEFAULT_TITLES);
		int help = findItem(rows, 1700);
		assertEquals("ABOUT", nearestHeader(rows, help));
		assertEquals("Help", rows.get(help).text);
		assertEquals(16, rows.get(help).sourceIndex);
	}

	@Test
	public void reconnectIsNotMovedToTheTop() {
		ArrayList<Row> rows = GameplayMenuAdapter.buildRows(DEFAULT_IDS, DEFAULT_TITLES);
		assertFalse(rows.get(0).header && rows.get(0).text.equals("CONNECTION"));
		assertEquals("EDITORS", rows.get(0).text);
		assertEquals(100, rows.get(1).itemId);
		assertEquals("CONNECTION", nearestHeader(rows, findItem(rows, 700)));
	}

	@Test
	public void clickPositionMapsBackToTheOriginalMenuItem() {
		ArrayList<Row> rows = GameplayMenuAdapter.buildRows(DEFAULT_IDS, DEFAULT_TITLES);
		int aliases = findItem(rows, 100);
		assertEquals(0, rows.get(aliases).sourceIndex);
		int buttonSets = findItem(rows, 401);
		assertEquals(4, rows.get(buttonSets).sourceIndex);
		int help = findItem(rows, 1700);
		assertEquals(16, rows.get(help).sourceIndex);
		for (Row row : rows) {
			if (row.header) {
				assertEquals(-1, row.sourceIndex);
			}
		}
	}

	@Test
	public void unknownButtonTitleGoesToEditors() {
		assertEquals(Section.EDITORS,
				GameplayMenuAdapter.sectionFor(90210, "Extra Buttons"));
		assertTrue(GameplayMenuAdapter.titleLooksButtonRelated("Ex Button Sets"));
	}

	@Test
	public void unknownLuaItemGoesToMoreSoItDoesNotVanish() {
		int[] ids = {100, 4242};
		String[] titles = {"Aliases", "Wizard plugin"};
		ArrayList<Row> rows = GameplayMenuAdapter.buildRows(ids, titles);
		String[] headers = headerSequence(rows);
		assertEquals("EDITORS", headers[0]);
		assertEquals("MORE", headers[headers.length - 1]);
		assertEquals("MORE", nearestHeader(rows, findItem(rows, 4242)));
		assertEquals(1, rows.get(findItem(rows, 4242)).sourceIndex);
	}

	@Test
	public void emptySectionsAreOmitted() {
		int[] ids = {700, 800};
		String[] titles = {"Reconnect", "Disconnect"};
		ArrayList<Row> rows = GameplayMenuAdapter.buildRows(ids, titles);
		assertEquals(1, headerSequence(rows).length);
		assertEquals("CONNECTION", rows.get(0).text);
		assertEquals(700, rows.get(1).itemId);
		assertEquals(800, rows.get(2).itemId);
	}

	private static int findItem(ArrayList<Row> rows, int itemId) {
		for (int i = 0; i < rows.size(); i++) {
			if (!rows.get(i).header && rows.get(i).itemId == itemId) {
				return i;
			}
		}
		throw new AssertionError("missing item " + itemId);
	}

	private static String nearestHeader(ArrayList<Row> rows, int itemPosition) {
		for (int i = itemPosition; i >= 0; i--) {
			if (rows.get(i).header) {
				return rows.get(i).text;
			}
		}
		throw new AssertionError("no header above " + itemPosition);
	}

	private static String[] headerSequence(ArrayList<Row> rows) {
		ArrayList<String> headers = new ArrayList<String>();
		for (Row row : rows) {
			if (row.header) {
				headers.add(row.text);
			}
		}
		return headers.toArray(new String[0]);
	}
}
