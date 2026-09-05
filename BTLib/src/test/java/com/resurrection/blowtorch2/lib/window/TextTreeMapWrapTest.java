package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/**
 * Word wrap at spaces shreds character-cell maps ({@code [ ]-[ ]}, {@code ##<<}).
 * Those lines must hard-break at the column, like Block Element maps.
 */
public class TextTreeMapWrapTest {

	private static void feed(TextTree tree, String text)
			throws UnsupportedEncodingException {
		tree.addBytesImpl(text.getBytes("UTF-8"));
	}

	private static List<String> visualRows(TextTree.Line line) {
		List<String> rows = new ArrayList<String>();
		StringBuilder sb = new StringBuilder();
		for (TextTree.Unit u : line.getData()) {
			if (u instanceof TextTree.Break || u instanceof TextTree.NewLine) {
				rows.add(sb.toString());
				sb.setLength(0);
			} else if (u instanceof TextTree.Text) {
				String s = ((TextTree.Text) u).getString();
				if (s != null) {
					sb.append(s);
				}
			}
		}
		if (sb.length() > 0) {
			rows.add(sb.toString());
		}
		return rows;
	}

	@Test
	public void asciiRoomMapIsACellMap() {
		assertTrue(TextTree.looksLikeCellMap("[ ]-[ ]-[ ]-[ ]-[ ]-[ ]"));
		assertTrue(TextTree.looksLikeCellMap("##<<[](){}+-"));
		assertTrue(TextTree.looksLikeCellMap("\u2588 \u2591 \u2588 \u2591"));
		assertTrue(TextTree.looksLikeCellMap("\u2500\u2502\u250c\u2510"));
	}

	@Test
	public void flyingOTilesAndLmapLegendAreCellMaps() {
		assertTrue(TextTree.looksLikeCellMap("oOoOoO      skies above the river (sky) 1:38pm"));
		assertTrue(TextTree.looksLikeCellMap("oOoOoOoOoOoOoO                "));
		assertTrue(TextTree.looksLikeCellMap(
				". .  . .  [||] . .  [AB]-[CD]+[||] . .  . .    AB: Example Offices       "));
	}

	@Test
	public void proseIsNotACellMap() {
		assertFalse(TextTree.looksLikeCellMap("The quick brown fox jumps over the lazy dog"));
		assertFalse(TextTree.looksLikeCellMap("You slash [the wolf] with <sword>."));
		assertFalse(TextTree.looksLikeCellMap(""));
		assertFalse(TextTree.looksLikeCellMap(null));
	}

	@Test
	public void taggedChatDoesNotPinPaintToTheCellGrid() {
		assertFalse(TextTree.paintPinsToCellGrid(
				"[OOC] [Say] Alice says, 'hello there, everyone in the room'"));
		assertFalse(TextTree.paintPinsToCellGrid(
				"[Auction] [Newbie] [Tell] someone laughs at the joke"));
		assertTrue(TextTree.looksLikeCellMap(
				"[Auction] [Newbie] [Tell] [OOC] someone laughs at the joke"));
		assertFalse(TextTree.paintPinsToCellGrid(
				"[Auction] [Newbie] [Tell] [OOC] someone laughs at the joke"));
		assertTrue("room map still pins",
				TextTree.paintPinsToCellGrid("[ ]-[ ]-[ ]-[ ]-[ ]-[ ]"));
		assertTrue("flying oO still pins",
				TextTree.paintPinsToCellGrid("oOoOoO      skies above the river (sky)"));
	}

	@Test
	public void lmapSpacesHardWrapAtColumn() throws Exception {
		TextTree tree = new TextTree();
		tree.setWordWrap(true);
		tree.setLineBreakAt(20);
		feed(tree, "[ ]-[ ]-[ ]-[ ]-[ ]-[ ]-[ ]-[ ]-[ ]-[ ]-[ ]-[ ]\n");
		List<String> rows = visualRows(tree.getLines().getFirst());
		assertTrue("expected a wrap of the 48-char map", rows.size() >= 2);
		assertEquals("hard wrap, not a space inside [ ]", 20, rows.get(0).length());
		assertEquals('[', rows.get(1).charAt(0));
	}

	@Test
	public void lmapLegendHardWrapsInsteadOfBreakingInsideTiles() throws Exception {
		TextTree tree = new TextTree();
		tree.setWordWrap(true);
		tree.setLineBreakAt(40);
		String line = ". .  . .  [||] . .  [AB]-[CD]+[||] . .  . .    AB: Example Offices       ";
		feed(tree, line + "\n");
		List<String> rows = visualRows(tree.getLines().getFirst());
		assertTrue("expected a wrap of the map+legend line", rows.size() >= 2);
		assertEquals("hard wrap at the column, not a space inside [ ]", 40, rows.get(0).length());
		assertFalse("soft wrap leaves a dangling [", rows.get(0).endsWith("[ "));
	}

	@Test
	public void flyingOTilesHardWrapAtColumn() throws Exception {
		TextTree tree = new TextTree();
		tree.setWordWrap(true);
		tree.setLineBreakAt(20);
		feed(tree, "oOoOoO      skies above the river (sky) 1:38pm\n");
		List<String> rows = visualRows(tree.getLines().getFirst());
		assertTrue("expected a wrap of the flying-tile line", rows.size() >= 2);
		assertEquals("hard wrap at the column, not at the space after skies",
				20, rows.get(0).length());
	}

	@Test
	public void proseStillWrapsAtSpaces() throws Exception {
		TextTree tree = new TextTree();
		tree.setWordWrap(true);
		tree.setLineBreakAt(20);
		feed(tree, "The quick brown fox jumps over the lazy dog today\n");
		List<String> rows = visualRows(tree.getLines().getFirst());
		assertTrue(rows.size() >= 2);
		assertTrue("wrapped at the space after fox, not mid-word",
				rows.get(0).endsWith("fox ") || rows.get(0).endsWith("fox"));
		assertTrue(rows.get(1).startsWith("jumps") || rows.get(1).startsWith(" over")
				|| rows.get(1).trim().startsWith("jumps"));
	}
}
