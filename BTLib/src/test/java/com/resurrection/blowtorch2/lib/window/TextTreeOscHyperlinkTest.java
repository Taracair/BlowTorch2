package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.junit.Test;

/**
 * Darkwind closes OSC 8 hyperlinks as {@code ESC ]8;;url ESC ]8;;)} — no BEL/ST.
 * The old scanner held from the open ESC forever, so the screen froze after the
 * first link while the session log (CSI-only strip) kept growing.
 */
public class TextTreeOscHyperlinkTest {

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

	@Test
	public void darkwindBrokenOscEightDoesNotSwallowTheRestOfTheBuffer() throws Exception {
		TextTree tree = new TextTree();
		String chunk =
				"before\n"
				+ "     (" + ESC + "]8;;https://play.darkwind.aihttps://play.darkwind.ai"
				+ ESC + "]8;;)\n"
				+ "[Newbie] after the link\n"
				+ "[ Paging (line 21): <enter> ]\n";
		tree.addBytesImpl(chunk.getBytes("UTF-8"));
		List<String> lines = render(tree);
		String joined = "";
		for (String l : lines) {
			joined = joined + l;
		}
		assertTrue("text before the link must remain", joined.contains("before"));
		assertTrue("text after a broken OSC 8 must not vanish",
				joined.contains("[Newbie] after the link"));
		assertTrue("pager prompt after the link must show",
				joined.contains("[ Paging (line 21): <enter> ]"));
		assertFalse("an open OSC must not leave a permanent holdover",
				joined.isEmpty());
	}

	@Test
	public void belTerminatedOscStillSkipsQuietly() throws Exception {
		TextTree tree = new TextTree();
		String chunk = "a" + ESC + "]8;;https://example.com\u0007" + "b\n";
		tree.addBytesImpl(chunk.getBytes("UTF-8"));
		assertEquals("ab\\n", render(tree).get(0));
	}
}
