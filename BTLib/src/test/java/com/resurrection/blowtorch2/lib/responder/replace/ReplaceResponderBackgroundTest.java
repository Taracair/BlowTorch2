package com.resurrection.blowtorch2.lib.responder.replace;

import static org.junit.Assert.assertTrue;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

import com.resurrection.blowtorch2.lib.window.TextTree;

/**
 * Replace copies the colour units that sat in front of the match, so a MUD
 * background CSI that painted the prefix would otherwise sit under the
 * replacement text too.
 */
public class ReplaceResponderBackgroundTest {

	private static final String ESC = "\u001B";
	private static final String ENC = "ISO-8859-1";
	private static final int MUD_BG = 46;
	private static final Pattern VERMIN = Pattern.compile("\\[ VERMIN \\]");

	@Test
	public void replacingABackgroundPaintedPrefixDoesNotKeepThatBackground()
			throws Exception {
		TextTree working = new TextTree();
		working.setModCount(0);
		working.addBytesImpl((ESC + "[48;5;" + MUD_BG
				+ "m[ VERMIN ] : Taracair says, \"test\"\n").getBytes(ENC));
		TextTree.Line line = working.getLines().get(0);
		Matcher m = VERMIN.matcher(TextTree.deColorLine(line).toString());
		assertTrue(m.find());
		ReplaceResponder action = new ReplaceResponder();
		action.setWith("VERMIN:");
		action.doResponse(null, working, 0, null, line, m.start(), m.end() - 2,
				m.group(), null, "test", "host", 0, 0, false, null, null, null,
				"vermin", ENC);

		String plain = TextTree.deColorLine(working.getLines().get(0)).toString();
		assertTrue("replacement missing: " + plain, plain.startsWith("VERMIN:"));
		Integer bg = xtermBgAt(working, "VERMIN:");
		assertTrue("replacement kept the MUD background (xterm " + bg + "): "
				+ visible(new String(working.dumpToBytes(true), ENC)),
				bg == null || bg.intValue() != MUD_BG);
	}

	private static String visible(String s) {
		return s.replace(ESC, "<ESC>").replace("\n", "\\n");
	}

	private static Integer xtermBgAt(TextTree tree, String needle) {
		Integer bg = null;
		for (int i = tree.getLines().size() - 1; i >= 0; i--) {
			String plain = TextTree.deColorLine(tree.getLines().get(i)).toString();
			int at = plain.indexOf(needle);
			int col = 0;
			Integer bgHere = bg;
			for (TextTree.Unit u : tree.getLines().get(i).getData()) {
				if (u instanceof TextTree.Color) {
					bgHere = xtermBgFrom(((TextTree.Color) u).getOperations(), bgHere);
				} else if (u instanceof TextTree.Text) {
					String s = ((TextTree.Text) u).getString();
					if (s == null) {
						continue;
					}
					if (at >= 0 && col <= at && at < col + s.length()) {
						return bgHere;
					}
					col += s.length();
				}
			}
			bg = bgHere;
		}
		return null;
	}

	private static Integer xtermBgFrom(java.util.List<Integer> ops, Integer current) {
		if (ops == null) {
			return current;
		}
		Integer bg = current;
		for (int i = 0; i < ops.size(); i++) {
			int op = ops.get(i).intValue();
			if (op == 48 && i + 2 < ops.size() && ops.get(i + 1).intValue() == 5) {
				bg = Integer.valueOf(ops.get(i + 2).intValue());
				i += 2;
			} else if (op == 0 || op == 49) {
				bg = null;
			} else if (op >= 40 && op <= 47) {
				bg = Integer.valueOf(op);
			}
		}
		return bg;
	}
}
