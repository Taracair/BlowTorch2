package com.resurrection.blowtorch2.lib.trigger.style;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.resurrection.blowtorch2.lib.window.TextTree;

public class StyleInspectTest {

	private static final String ESC = "\u001B";

	@Test
	public void newestLineIsBrokenZero() throws Exception {
		TextTree tree = twoLines();
		StyleLineModel[] models = StyleLineModel.buildTree(tree);
		StyleInspect.Hit hit = StyleInspect.at(tree.getLines(), models, 0, 0, 80);
		assertNotNull(hit);
		assertTrue(hit.glyph.contains("world"));
	}

	@Test
	public void pastEndClampsToOldestVisibleRow() throws Exception {
		TextTree tree = twoLines();
		StyleLineModel[] models = StyleLineModel.buildTree(tree);
		StyleInspect.Hit hit = StyleInspect.at(tree.getLines(), models, 9999, 0, 80);
		assertNotNull(hit);
		assertTrue(hit.glyph.contains("hello"));
	}

	@Test
	public void negativeBrokenClampsToNewest() throws Exception {
		TextTree tree = twoLines();
		StyleLineModel[] models = StyleLineModel.buildTree(tree);
		StyleInspect.Hit hit = StyleInspect.at(tree.getLines(), models, -4, 0, 80);
		assertNotNull(hit);
		assertTrue(hit.glyph.contains("world"));
	}

	@Test
	public void columnPastEndStillReturnsASnapshot() throws Exception {
		TextTree tree = twoLines();
		StyleLineModel[] models = StyleLineModel.buildTree(tree);
		StyleInspect.Hit hit = StyleInspect.at(tree.getLines(), models, 0, 400, 80);
		assertNotNull(hit);
		assertNotNull(hit.snap);
		assertEquals(StyleSnapshot.ColorSpace.ANSI16, hit.snap.fgSpace);
	}

	@Test
	public void wrapRowUsesWrapColumns() throws Exception {
		TextTree tree = new TextTree();
		tree.addBytesImpl((ESC + "[32mabcd" + ESC + "[31mefgh\n").getBytes("UTF-8"));
		tree.getLines().get(0).setBreaks(1);
		StyleLineModel[] models = StyleLineModel.buildTree(tree);
		StyleInspect.Hit first = StyleInspect.at(tree.getLines(), models, 0, 0, 4);
		StyleInspect.Hit second = StyleInspect.at(tree.getLines(), models, 1, 0, 4);
		assertNotNull(first);
		assertNotNull(second);
		assertEquals(32, first.snap.fgCode);
		assertEquals(31, second.snap.fgCode);
	}

	private static TextTree twoLines() throws Exception {
		TextTree tree = new TextTree();
		tree.addBytesImpl((ESC + "[32mhello\n").getBytes("UTF-8"));
		tree.addBytesImpl("world\n".getBytes("UTF-8"));
		return tree;
	}
}
