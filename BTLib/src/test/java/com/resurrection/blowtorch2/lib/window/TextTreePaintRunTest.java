package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.LinkedList;
import java.util.ListIterator;

import org.junit.Test;

/**
 * Fling paint joins adjacent TEXT/WHITESPACE of one colour. The iterator
 * protocol is the part that used to blank the screen when it was left at the
 * end of the line.
 */
public class TextTreePaintRunTest {

	@Test
	public void joinsWordsAndStopsBeforeColor() {
		TextTree tree = new TextTree();
		LinkedList<TextTree.Unit> units = new LinkedList<TextTree.Unit>();
		TextTree.Text hello = tree.new Text("hello");
		TextTree.WhiteSpace space = tree.new WhiteSpace(" ");
		TextTree.Text world = tree.new Text("world");
		TextTree.Color color = tree.new Color();
		TextTree.Text more = tree.new Text("more");
		units.add(hello);
		units.add(space);
		units.add(world);
		units.add(color);
		units.add(more);

		ListIterator<TextTree.Unit> it = units.listIterator();
		assertSame(hello, it.next());
		StringBuilder out = new StringBuilder();
		int[] io = new int[2];
		TextTree.drainSamePaintText(it, hello, out, io);

		assertEquals("hello world", out.toString());
		assertEquals(hello.charcount + space.charcount + world.charcount, io[0]);
		assertEquals("hello world".length(), io[1]);
		assertTrue(it.hasNext());
		assertSame(color, it.next());
		assertSame(more, it.next());
	}

	@Test
	public void stopsBeforeHrefAndLeavesIteratorThere() {
		TextTree tree = new TextTree();
		LinkedList<TextTree.Unit> units = new LinkedList<TextTree.Unit>();
		TextTree.Text before = tree.new Text("see ");
		TextTree.Text link = tree.new Text("here");
		link.setHref("http://example.org");
		units.add(before);
		units.add(link);

		ListIterator<TextTree.Unit> it = units.listIterator();
		assertSame(before, it.next());
		StringBuilder out = new StringBuilder();
		int[] io = new int[2];
		TextTree.drainSamePaintText(it, before, out, io);

		assertEquals("see ", out.toString());
		assertSame(link, it.next());
	}

	@Test
	public void singleUnitLeavesIteratorExhausted() {
		TextTree tree = new TextTree();
		LinkedList<TextTree.Unit> units = new LinkedList<TextTree.Unit>();
		TextTree.Text only = tree.new Text("[OOC]");
		units.add(only);

		ListIterator<TextTree.Unit> it = units.listIterator();
		it.next();
		StringBuilder out = new StringBuilder();
		int[] io = new int[2];
		TextTree.drainSamePaintText(it, only, out, io);

		assertEquals("[OOC]", out.toString());
		assertFalse(it.hasNext());
	}
}
