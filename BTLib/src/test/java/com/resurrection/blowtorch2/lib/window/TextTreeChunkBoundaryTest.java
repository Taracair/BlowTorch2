package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.junit.Test;

/**
 * Where a TCP packet happens to break must not change what the buffer ends up
 * holding. These feed the same text in one chunk and in several, and compare.
 */
public class TextTreeChunkBoundaryTest {

	private static final String ESC = "";

	/** Lines oldest first, with colour dropped and the closing newline marked. */
	private static List<String> render(TextTree tree) {
		LinkedList<TextTree.Line> lines = tree.getLines();
		List<String> out = new ArrayList<String>();
		// addLine inserts at the head, so the buffer runs newest first.
		for (int i = lines.size() - 1; i >= 0; i--) {
			StringBuilder sb = new StringBuilder();
			for (TextTree.Unit u : lines.get(i).getData()) {
				switch (u.type) {
				case TEXT:
				case WHITESPACE:
					sb.append(((TextTree.Text) u).data);
					break;
				case NEWLINE:
					sb.append("\\n");
					break;
				default:
					break;
				}
			}
			out.add(sb.toString());
		}
		return out;
	}

	private static void feed(TextTree tree, String... chunks)
			throws UnsupportedEncodingException {
		for (String chunk : chunks) {
			tree.addBytesImpl(chunk.getBytes("UTF-8"));
		}
	}

	@Test
	public void splitsOnNewlinesInOneChunk() throws Exception {
		TextTree tree = new TextTree();
		feed(tree, "hello\nworld\n");
		assertEquals(list("hello\\n", "world\\n"), render(tree));
	}

	@Test
	public void continuesAnUnfinishedLineAcrossChunks() throws Exception {
		TextTree tree = new TextTree();
		feed(tree, "hel", "lo\n");
		assertEquals(list("hello\\n"), render(tree));
	}

	/**
	 * The regression: a chunk ending with a colour reset just after a newline
	 * leaves a line carrying nothing but that colour. It is the start of the next
	 * line, not a blank one, and the text that follows belongs to it.
	 */
	@Test
	public void doesNotOrphanAColourOnlyLine() throws Exception {
		TextTree tree = new TextTree();
		feed(tree, "hello\n" + ESC + "[0m", "world\n");
		assertEquals(list("hello\\n", "world\\n"), render(tree));
	}

	/** Same text, same result, whichever way the packets fall. */
	@Test
	public void colourAfterNewlineMatchesTheUnsplitCase() throws Exception {
		String text = "hello\n" + ESC + "[0mworld\n";
		TextTree whole = new TextTree();
		feed(whole, text);

		for (int cut = 1; cut < text.length(); cut++) {
			TextTree split = new TextTree();
			feed(split, text.substring(0, cut), text.substring(cut));
			assertEquals("split after " + cut + " char(s)",
					render(whole), render(split));
		}
	}

	/**
	 * An escape arriving as the last byte of a chunk is held over. The line it
	 * would have gone on is empty when the newline came immediately before, and
	 * an empty line must not reach the buffer.
	 */
	@Test
	public void escapeAtChunkEndAfterNewlineAddsNoBlankLine() throws Exception {
		TextTree tree = new TextTree();
		feed(tree, "hello\n" + ESC, "[0mworld\n");
		assertEquals(list("hello\\n", "world\\n"), render(tree));
	}

	/** A blank line the server really sent still survives. */
	@Test
	public void keepsBlankLinesTheServerSent() throws Exception {
		TextTree tree = new TextTree();
		feed(tree, "hello\n\nworld\n");
		assertEquals(list("hello\\n", "\\n", "world\\n"), render(tree));
	}

	private static List<String> list(String... items) {
		List<String> out = new ArrayList<String>();
		for (String s : items) {
			out.add(s);
		}
		return out;
	}
}
