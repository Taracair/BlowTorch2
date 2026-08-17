package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
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

	@Test
	public void belOpenCloseStampsHrefOnDisplayText() throws Exception {
		TextTree tree = new TextTree();
		String chunk = ESC + "]8;;https://example.com/real\u0007"
				+ "click" + ESC + "]8;;\u0007" + " after\n";
		tree.addBytesImpl(chunk.getBytes("UTF-8"));
		String joined = join(render(tree));
		assertTrue(joined.contains("click"));
		assertTrue(joined.contains(" after"));
		assertFalse("payload must not print", joined.contains("]8;;"));
		assertEquals("https://example.com/real", hrefOn(tree, "click"));
		assertNull(hrefOn(tree, " after"));
	}

	@Test
	public void stTerminatorStampsHref() throws Exception {
		TextTree tree = new TextTree();
		String st = ESC + "\\";
		String chunk = ESC + "]8;;https://example.net/st" + st
				+ "marked" + ESC + "]8;;" + st + "\n";
		tree.addBytesImpl(chunk.getBytes("UTF-8"));
		assertEquals("https://example.net/st", hrefOn(tree, "marked"));
	}

	@Test
	public void displayNeedNotEqualUri() throws Exception {
		TextTree tree = new TextTree();
		String chunk = ESC + "]8;;https://example.com/real-path\u0007"
				+ "click here" + ESC + "]8;;\u0007\n";
		tree.addBytesImpl(chunk.getBytes("UTF-8"));
		assertEquals("https://example.com/real-path", hrefOn(tree, "click"));
	}

	@Test
	public void javascriptDoesNotStamp() throws Exception {
		TextTree tree = new TextTree();
		String chunk = ESC + "]8;;javascript:alert(1)\u0007"
				+ "plain" + ESC + "]8;;\u0007\n";
		tree.addBytesImpl(chunk.getBytes("UTF-8"));
		assertNull(hrefOn(tree, "plain"));
		assertTrue(join(render(tree)).contains("plain"));
	}

	@Test
	public void darkwindCloserDoesNotLeaveParenAsHref() throws Exception {
		TextTree tree = new TextTree();
		String chunk =
				"     (" + ESC + "]8;;https://play.darkwind.aihttps://play.darkwind.ai"
				+ ESC + "]8;;)\n"
				+ "after\n";
		tree.addBytesImpl(chunk.getBytes("UTF-8"));
		assertNull("closer ) is not a URI", hrefOn(tree, ")"));
		assertTrue(join(render(tree)).contains("after"));
	}

	@Test
	public void dumpToBytesRoundTripsHrefToAFreshTree() throws Exception {
		TextTree working = new TextTree();
		String chunk = ESC + "]8;;https://example.com/real\u0007"
				+ "click" + ESC + "]8;;\u0007" + " after\n";
		working.addBytesImpl(chunk.getBytes("UTF-8"));
		byte[] dumped = working.dumpToBytes(true);
		String dumpedStr = new String(dumped, "UTF-8");
		assertTrue("dump must re-emit OSC 8, got: " + dumpedStr,
				dumpedStr.contains("]8;;https://example.com/real"));
		TextTree ui = new TextTree();
		ui.addBytesImpl(dumped);
		assertEquals("https://example.com/real", hrefOn(ui, "click"));
		assertNull(hrefOn(ui, " after"));
	}

	@Test
	public void drainLinesKeepsAnOpenOsc8Span() throws Exception {
		TextTree tree = new TextTree();
		tree.addBytesImpl((ESC + "]8;;https://example.com/span\u0007click\n")
				.getBytes("UTF-8"));
		assertEquals("https://example.com/span", hrefOn(tree, "click"));
		tree.drainLines();
		tree.addBytesImpl("here\n".getBytes("UTF-8"));
		assertEquals("open span must survive drainLines() between chunks",
				"https://example.com/span", hrefOn(tree, "here"));
	}

	@Test
	public void emptyClosesAnOpenOsc8Span() throws Exception {
		TextTree tree = new TextTree();
		tree.addBytesImpl((ESC + "]8;;https://example.com/span\u0007click\n")
				.getBytes("UTF-8"));
		tree.empty();
		tree.addBytesImpl("here\n".getBytes("UTF-8"));
		assertNull("full wipe must not stamp replay with the old href",
				hrefOn(tree, "here"));
	}

	@Test
	public void wrapCopiesHrefOntoBothFragments() throws Exception {
		TextTree tree = new TextTree();
		tree.setWordWrap(true);
		tree.setLineBreakAt(4);
		String chunk = ESC + "]8;;https://example.com/w\u0007"
				+ "abcdefgh" + ESC + "]8;;\u0007\n";
		tree.addBytesImpl(chunk.getBytes("UTF-8"));
		int stamped = 0;
		for (TextTree.Line line : tree.getLines()) {
			for (TextTree.Unit u : line.getData()) {
				if (u instanceof TextTree.Text) {
					TextTree.Text t = (TextTree.Text) u;
					if ("https://example.com/w".equals(t.getHref())) {
						stamped++;
					}
				}
			}
		}
		assertTrue("both wrap fragments should keep the href, got " + stamped,
				stamped >= 2);
	}

	private static String join(java.util.List<String> lines) {
		StringBuilder sb = new StringBuilder();
		for (String l : lines) {
			sb.append(l);
		}
		return sb.toString();
	}

	private static String hrefOn(TextTree tree, String contains) {
		for (TextTree.Line line : tree.getLines()) {
			for (TextTree.Unit u : line.getData()) {
				if (u instanceof TextTree.Text) {
					TextTree.Text t = (TextTree.Text) u;
					if (t.getString() != null && t.getString().contains(contains)) {
						return t.getHref();
					}
				}
			}
		}
		return null;
	}
}
