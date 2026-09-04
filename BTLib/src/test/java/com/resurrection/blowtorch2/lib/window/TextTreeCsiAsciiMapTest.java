package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.junit.Test;

/**
 * ASCII map tiles live in the CSI-final range ({@code [ ] | ` \\ ; :}). They
 * are characters, not commands. A colour code that never saw {@code m} used
 * to swallow the next tile and the newline after it.
 */
public class TextTreeCsiAsciiMapTest {

	private static final String ESC = "\u001B";

	private static List<String> render(TextTree tree) {
		LinkedList<TextTree.Line> lines = tree.getLines();
		List<String> out = new ArrayList<String>();
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

	private static String joined(TextTree tree) {
		StringBuilder sb = new StringBuilder();
		for (String row : render(tree)) {
			sb.append(row);
		}
		return sb.toString();
	}

	private static TextTree feed(String s) throws UnsupportedEncodingException {
		TextTree tree = new TextTree();
		tree.addBytesImpl(s.getBytes("UTF-8"));
		return tree;
	}

	@Test
	public void wellFormedSgrKeepsOliveStripAndBrackets() throws Exception {
		TextTree tree = feed(ESC + "[38;5;220m;:" + ESC + "[0m [||] <<[]\n");
		assertEquals(";: [||] <<[]\\n", joined(tree));
	}

	@Test
	public void wellFormedSgrKeepsBacktickTile() throws Exception {
		TextTree tree = feed(ESC + "[32m`'\\" + ESC + "[0m\n");
		assertEquals("`'\\\\n", joined(tree));
	}

	@Test
	public void newlineAbortsBrokenCsiSoTheNextMapRowStaysARow() throws Exception {
		TextTree tree = feed(ESC + "[38;5;220\n;: tiles\n");
		List<String> rows = render(tree);
		assertEquals("two rows, not one glued line", 2, rows.size());
		assertEquals(";: tiles\\n", rows.get(1));
		assertFalse("olive strip must not vanish into CSI params",
				joined(tree).contains("[38"));
	}

	@Test
	public void lmapBracketIsNotACsiFinal() throws Exception {
		TextTree tree = feed(ESC + "[38;5;46[||]\n");
		assertEquals("[||]\\n", joined(tree));
	}

	@Test
	public void pipeBacktickAndBraceAreMapTilesNotCsi() throws Exception {
		TextTree tree = feed(ESC + "[46| ` {} /\n");
		assertEquals("| ` {} /\\n", joined(tree));
	}

	@Test
	public void atSignIsNotACsiFinal() throws Exception {
		TextTree tree = feed(ESC + "[46@\n");
		assertEquals("@\\n", joined(tree));
	}

	@Test
	public void cursorCsiStillDoesNotPrint() throws Exception {
		TextTree tree = feed(ESC + "[2J" + ESC + "[Hhello\n");
		assertEquals("hello\\n", joined(tree));
	}

	@Test
	public void oTilesAreNotCsiFinals() throws Exception {
		TextTree tree = feed(ESC + "[46oOoO\n");
		assertEquals("oOoO\\n", joined(tree));
	}
}
