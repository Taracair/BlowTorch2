package com.resurrection.blowtorch2.lib.responder.color;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

import com.resurrection.blowtorch2.lib.responder.TriggerResponder;
import com.resurrection.blowtorch2.lib.responder.replace.ReplaceResponder;
import com.resurrection.blowtorch2.lib.trigger.LineModCount;
import com.resurrection.blowtorch2.lib.window.TextTree;

/**
 * A replace inside a later colour match must not slide that match. The colour
 * paints the line as it is after the replace, including the inserted text.
 */
public class ReplaceInsideColorMatchTest {

	private static final String ESC = "\u001B";
	private static final String ENC = "ISO-8859-1";
	private static final int PARTY = 183;

	private static final String PARTY_LINE =
			"[party] Taracair says, \"note complete mastery hmm\"";

	@Test
	public void longerReplaceInsideAWholeLineColourStaysInTheLine()
			throws Exception {
		String expected = PARTY_LINE.replace("complete mastery",
				"complete mastery (31+/34+)");
		TextTree.Line line = dispatch(PARTY_LINE, "complete mastery",
				"complete mastery (31+/34+)", "\\[party\\](.+)", true);
		assertEquals(expected, TextTree.deColorLine(line).toString());
		assertEquals(expected, textWhereFgIs(line, PARTY));
	}

	@Test
	public void sameLengthReplaceInsideAColourIsNotReverted() throws Exception {
		String expected = PARTY_LINE.replace("mastery", "MASTERY");
		TextTree.Line line = dispatch(PARTY_LINE, "mastery", "MASTERY",
				"\\[party\\](.+)", true);
		assertEquals(expected, TextTree.deColorLine(line).toString());
	}

	@Test
	public void shorterReplaceInsideAColourDoesNotSplitTheLine()
			throws Exception {
		String expected = PARTY_LINE.replace("mastery", "x");
		TextTree.Line line = dispatch(PARTY_LINE, "mastery", "x",
				"\\[party\\](.+)", true);
		assertEquals(expected, TextTree.deColorLine(line).toString());
		assertEquals(expected, textWhereFgIs(line, PARTY));
	}

	@Test
	public void replaceBeforeALaterColourStillSlidesThatMatch()
			throws Exception {
		String original = "hello says goblin";
		TextTree.Line line = dispatch(original, "hello", "hello there",
				"says", false);
		assertEquals("hello there says goblin",
				TextTree.deColorLine(line).toString());
		assertEquals("says", textWhereFgIs(line, PARTY));
	}

	@Test
	public void replaceAfterAColourMatchDoesNotMoveTheColour()
			throws Exception {
		String expected = PARTY_LINE.replace("complete mastery",
				"complete mastery (31+/34+)");
		TextTree.Line line = dispatch(PARTY_LINE, "complete mastery",
				"complete mastery (31+/34+)", "Taracair", false);
		assertEquals(expected, TextTree.deColorLine(line).toString());
		assertEquals("Taracair", textWhereFgIs(line, PARTY));
	}

	@Test
	public void deletingTheColourMatchDoesNotPutTheTextBack() throws Exception {
		String original = "head mastery tail";
		TextTree working = new TextTree();
		working.clearMatchAdjustments();
		working.addBytesImpl((original + "\n").getBytes(ENC));
		TextTree.Line line = working.getLines().get(0);

		Matcher rep = Pattern.compile("mastery").matcher(original);
		assertTrue(rep.find());
		ReplaceResponder replace = new ReplaceResponder();
		replace.setWith("");
		fire(replace, working, line, rep.start(), rep.end() - 2, rep.group(),
				"craft");

		LineModCount saved = new LineModCount();
		saved.store(0, working.getMatchShift());
		working.clearMatchAdjustments();
		working.setMatchShift(saved.load(0));

		Matcher gone = Pattern.compile("mastery").matcher(original);
		assertTrue(gone.find());
		ColorAction color = new ColorAction();
		color.setColor(PARTY);
		color.setBackgroundColor(16);
		fire(color, working, line, gone.start(), gone.end() - 2, gone.group(),
				"party");
		assertEquals("head  tail", TextTree.deColorLine(line).toString());

		Matcher tail = Pattern.compile("tail").matcher(original);
		assertTrue(tail.find());
		ColorAction tailColor = new ColorAction();
		tailColor.setColor(45);
		tailColor.setBackgroundColor(16);
		fire(tailColor, working, line, tail.start(), tail.end() - 2,
				tail.group(), "tail");
		assertEquals("head  tail", TextTree.deColorLine(line).toString());
		assertEquals("tail", textWhereFgIs(line, 45));
	}

	@Test
	public void mudColourUnitsDoNotChangeThePlainResult() throws Exception {
		String raw = ESC + "[33m[party]" + ESC + "[0m Taracair "
				+ ESC + "[38;5;13msays," + ESC + "[0m \"note complete mastery hmm\"";
		String expected = PARTY_LINE.replace("complete mastery",
				"complete mastery (31+/34+)");
		TextTree.Line line = dispatch(raw, "complete mastery",
				"complete mastery (31+/34+)", "\\[party\\](.+)", true);
		assertEquals(expected, TextTree.deColorLine(line).toString());
	}

	/**
	 * Replace, then drop the tree's shift the way the cascade does when it
	 * steps to another line, then reload and colour. Matches are taken from
	 * {@code originalPlain}, which is what the cascade saw.
	 */
	private static TextTree.Line dispatch(String wire, String replacePattern,
			String with, String colorPattern, boolean colorToEndOfLine)
			throws Exception {
		TextTree working = new TextTree();
		working.clearMatchAdjustments();
		working.addBytesImpl((wire + "\n").getBytes(ENC));
		TextTree.Line line = working.getLines().get(0);
		String original = TextTree.deColorLine(line).toString();

		Matcher rep = Pattern.compile(replacePattern).matcher(original);
		assertTrue(rep.find());
		ReplaceResponder replace = new ReplaceResponder();
		replace.setWith(with);
		replace.doResponse(null, working, 0, null, line, rep.start(),
				rep.end() - 2, rep.group(), null, "test", "host", 0, 0, false,
				null, null, null, "craft", ENC);

		LineModCount saved = new LineModCount();
		saved.store(0, working.getMatchShift());
		working.clearMatchAdjustments();
		working.setMatchShift(saved.load(0));

		Matcher col = Pattern.compile(colorPattern).matcher(original);
		assertTrue(col.find());
		ColorAction color = new ColorAction();
		color.setColor(PARTY);
		color.setBackgroundColor(16);
		color.doResponse(null, working, 0, null, line, col.start(),
				col.end() - 2, col.group(), null, "test", "host", 0, 0, false,
				null, null, null, "party", ENC);
		if (colorToEndOfLine) {
			assertTrue("colour did not cover the line: " + visible(line),
					textWhereFgIs(line, PARTY).length()
							== TextTree.deColorLine(line).length());
		}
		return line;
	}

	private static void fire(TriggerResponder action, TextTree working,
			TextTree.Line line, int start, int end, String matched, String name)
			throws Exception {
		action.doResponse(null, working, 0, null, line, start, end, matched,
				null, "test", "host", 0, 0, false, null, null, null, name, ENC);
	}

	private static String textWhereFgIs(TextTree.Line line, int want) {
		StringBuilder b = new StringBuilder();
		int fg = -1;
		for (TextTree.Unit u : line.getData()) {
			if (u instanceof TextTree.Color) {
				fg = applyFg(fg, ((TextTree.Color) u).getOperations());
			} else if (u instanceof TextTree.Text && fg == want) {
				String s = ((TextTree.Text) u).getString();
				if (s != null) {
					b.append(s);
				}
			}
		}
		return b.toString();
	}

	private static int applyFg(int fg, List<Integer> ops) {
		if (ops == null) {
			return fg;
		}
		for (int i = 0; i < ops.size(); i++) {
			int op = ops.get(i).intValue();
			if (op == 0 || op == 39) {
				fg = -1;
			} else if (op == 38 && i + 2 < ops.size()
					&& ops.get(i + 1).intValue() == 5) {
				fg = ops.get(i + 2).intValue();
				i += 2;
			}
		}
		return fg;
	}

	private static String visible(TextTree.Line line) {
		return TextTree.deColorLine(line).toString();
	}
}
