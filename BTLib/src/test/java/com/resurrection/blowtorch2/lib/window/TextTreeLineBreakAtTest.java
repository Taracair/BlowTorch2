package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.UnsupportedEncodingException;

import org.junit.Test;

/**
 * {@link TextTree#setLineBreakAt} must not rebuild the tree when the wrap
 * width is unchanged — height-only drawer resize used to pay that walk on
 * every MOVE via {@code Window.calculateCharacterFeatures}.
 */
public class TextTreeLineBreakAtTest {

	private static void feed(TextTree tree, String text)
			throws UnsupportedEncodingException {
		tree.addBytesImpl(text.getBytes("UTF-8"));
	}

	private static TextTree.Break firstBreak(TextTree.Line line) {
		for (TextTree.Unit u : line.getData()) {
			if (u instanceof TextTree.Break) {
				return (TextTree.Break) u;
			}
		}
		return null;
	}

	@Test
	public void sameBreakAt_skipsUpdateTree() throws Exception {
		TextTree tree = new TextTree();
		tree.setLineBreakAt(20);
		feed(tree, "abcdefghijklmnopqrstuvwxyz\n");
		TextTree.Line line = tree.getLines().getFirst();
		TextTree.Break before = firstBreak(line);
		assertNotNull("expected a wrap break at column 20", before);
		int brokenBefore = tree.getBrokenLineCount();

		tree.setLineBreakAt(20);

		assertSame("updateTree must not re-run when breakAt is unchanged",
				before, firstBreak(line));
		assertEquals(brokenBefore, tree.getBrokenLineCount());
	}

	@Test
	public void differentBreakAt_rebuildsWraps() throws Exception {
		TextTree tree = new TextTree();
		tree.setLineBreakAt(40);
		feed(tree, "abcdefghijklmnopqrstuvwxyz\n");
		int wide = tree.getBrokenLineCount();
		tree.setLineBreakAt(10);
		int narrow = tree.getBrokenLineCount();
		assertEquals(1, wide);
		// 26 chars at wrap 10 → more than one broken line
		assertTrue(narrow > wide);
	}
}
