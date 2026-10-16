package com.resurrection.blowtorch2.lib.responder.gag;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import com.resurrection.blowtorch2.lib.responder.IteratorModifiedException;
import com.resurrection.blowtorch2.lib.window.TextTree;

/**
 * A multi-line gag deletes the block, then has to resume on the next newer
 * line. The old cursor was the start index, which is past the end of the
 * list once the block is gone (Index: 15, Size: 8).
 */
public class GagBlockTest {

	private static final String ENC = "ISO-8859-1";

	@Test
	public void gaggingEveryLineOfTheChunkDoesNotWalkOffTheList() throws Exception {
		List<String> lines = Arrays.asList("top", "mid", "bot");
		TextTree tree = treeOf(lines);
		IteratorModifiedException resumed = gag(tree, lines.size() - 1, "top\nmid\nbot");
		assertEquals(Arrays.asList(), plainOldestFirst(tree));
		assertFalse(resumed.getIterator().hasPrevious());
	}

	@Test
	public void gaggingABlockLeavesTheLinesOnEitherSide() throws Exception {
		// Arrival order. The match is the three middle lines.
		List<String> lines = Arrays.asList("before", "a", "b", "c", "after");
		TextTree tree = treeOf(lines);
		int start = lines.size() - 1 - 1; // "a", the oldest line of the match
		IteratorModifiedException resumed = gag(tree, start, "a\nb\nc");
		assertEquals(Arrays.asList("before", "after"), plainOldestFirst(tree));
		assertEquals("after", plain(resumed.getIterator().previous()));
	}

	@Test
	public void aSingleLineGagStillContinuesOnTheNextNewerLine() throws Exception {
		List<String> lines = Arrays.asList("before", "a", "b", "c", "after");
		TextTree tree = treeOf(lines);
		int at = lines.size() - 1 - 2; // "b"
		IteratorModifiedException resumed = gag(tree, at, "b");
		assertEquals(Arrays.asList("before", "a", "c", "after"), plainOldestFirst(tree));
		assertEquals("c", plain(resumed.getIterator().previous()));
	}

	@Test
	public void theReportedIndexPastTheShrunkenListStaysInsideIt() throws Exception {
		// 18 lines, match starts at index 15 and covers 10. After the delete
		// the list has 8 lines, and the old resume index was 15.
		List<String> lines = new ArrayList<String>();
		for (int i = 0; i < 18; i++) {
			lines.add(String.format("L%02d", i));
		}
		TextTree tree = treeOf(lines);
		StringBuilder matched = new StringBuilder();
		for (int i = 2; i <= 11; i++) {
			if (i > 2) {
				matched.append('\n');
			}
			matched.append(lines.get(i));
		}
		IteratorModifiedException resumed = gag(tree, 15, matched.toString());
		List<String> left = new ArrayList<String>();
		left.add(lines.get(0));
		left.add(lines.get(1));
		for (int i = 12; i < 18; i++) {
			left.add(lines.get(i));
		}
		assertEquals(left, plainOldestFirst(tree));
		assertEquals("L12", plain(resumed.getIterator().previous()));
	}

	@Test
	public void aStartIndexPastTheEndRemovesNothing() throws Exception {
		TextTree tree = treeOf(Arrays.asList("only"));
		assertFalse(gagOrFalse(tree, 4, "only"));
		assertEquals(Arrays.asList("only"), plainOldestFirst(tree));
	}

	private static IteratorModifiedException gag(TextTree tree, int lineNumber, String matched)
			throws Exception {
		try {
			gagOrFalse(tree, lineNumber, matched);
		} catch (IteratorModifiedException resumed) {
			return resumed;
		}
		fail("a gag that removed lines has to hand back a fresh iterator");
		return null;
	}

	private static boolean gagOrFalse(TextTree tree, int lineNumber, String matched)
			throws IteratorModifiedException {
		TextTree.Line line = tree.getLines().isEmpty() ? null : tree.getLines().get(0);
		return new GagAction().doResponse(null, tree, lineNumber, null, line, 0, 0, matched,
				null, "test", "host", 0, 0, false, null, null, null, "minimap", ENC);
	}

	private static TextTree treeOf(List<String> oldestFirst) throws Exception {
		StringBuilder chunk = new StringBuilder();
		for (int i = 0; i < oldestFirst.size(); i++) {
			chunk.append(oldestFirst.get(i)).append('\n');
		}
		TextTree tree = new TextTree();
		tree.addBytesImpl(chunk.toString().getBytes(ENC));
		assertEquals(oldestFirst, plainOldestFirst(tree));
		return tree;
	}

	private static List<String> plainOldestFirst(TextTree tree) {
		List<String> out = new ArrayList<String>();
		for (int i = tree.getLines().size() - 1; i >= 0; i--) {
			out.add(plain(tree.getLines().get(i)));
		}
		return out;
	}

	private static String plain(TextTree.Line line) {
		return TextTree.deColorLine(line).toString();
	}
}
