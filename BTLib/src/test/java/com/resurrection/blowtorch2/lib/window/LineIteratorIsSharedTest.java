package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ListIterator;

import org.junit.Test;

/**
 * {@code Line.getIterator()} is a getter, not a factory: it hands back the one
 * iterator the line owns. Anything that walks a line without meaning to draw it
 * must therefore walk {@link TextTree.Line#getData()} instead.
 *
 * <p>This is pinned because getting it wrong is invisible in review and total on
 * the device: whole-line tap matching walked getIterator() before drawing, the
 * drawing loop then asked for the same object and found it at the end, and a
 * connected session showed an empty game window with only the buttons on it.
 */
public class LineIteratorIsSharedTest {

	private static TextTree.Line oneLine(String text) throws Exception {
		TextTree tree = new TextTree();
		tree.addBytesImpl((text + "\n").getBytes("UTF-8"));
		return tree.getLines().getLast();
	}

	@Test
	public void twoCallsHandBackTheSameIterator() throws Exception {
		TextTree.Line line = oneLine("you see a rusty sword lying here");
		assertSame("getIterator() must not be treated as a factory",
				line.getIterator(), line.getIterator());
	}

	@Test
	public void walkingItLeavesItAtTheEndForTheNextCaller() throws Exception {
		TextTree.Line line = oneLine("you see a rusty sword lying here");
		ListIterator<TextTree.Unit> first = line.getIterator();
		while (first.hasNext()) {
			first.next();
		}
		assertFalse("the drawing loop would find nothing left to draw",
				line.getIterator().hasNext());
	}

	/** The way to read a line without disturbing anyone: its own list. */
	@Test
	public void theDataListCanBeWalkedFreely() throws Exception {
		TextTree.Line line = oneLine("you see a rusty sword lying here");
		StringBuilder plain = new StringBuilder();
		for (TextTree.Unit u : line.getData()) {
			if (u instanceof TextTree.Text) {
				plain.append(((TextTree.Text) u).getString());
			}
		}
		assertTrue(plain.toString().contains("rusty sword"));
		assertTrue("walking getData() must leave the shared iterator alone",
				line.getIterator().hasNext());
	}
}
