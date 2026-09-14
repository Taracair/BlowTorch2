package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PrefixPickHitTest {

	private static final String SAMPLE = "a rusty iron-helmet (glowing)";

	@Test
	public void anUnwrappedColumnHitsTheWord() {
		assertEquals("rusty", word(0, SAMPLE.indexOf("rusty"), 0));
		assertEquals("iron", word(0, SAMPLE.indexOf("iron"), 0));
		assertEquals("helmet", word(0, SAMPLE.indexOf("helmet"), 0));
		assertEquals("glowing", word(0, SAMPLE.indexOf("glowing"), 0));
	}

	@Test
	public void aWrappedRowAddsTheColumnStride() {
		// wrapColumns 10: "a rusty ir" / "on-helmet " / "(glowing)"
		assertEquals("iron", word(1, 0, 10));
		assertEquals("helmet", word(1, 4, 10));
	}

	@Test
	public void aGapAfterAWordStillPicksIt() {
		assertEquals("rusty", word(0, SAMPLE.indexOf("rusty") + "rusty".length(),
				0));
	}

	@Test
	public void hyphenStillSplits() {
		assertEquals("iron", word(0, SAMPLE.indexOf('-'), 0));
		AlnumWordAt.Span helmet = PrefixPickHit.spanOnLine(SAMPLE, 0,
				SAMPLE.indexOf("helmet"), 0);
		assertNotNull(helmet);
		assertEquals("helmet", helmet.text);
		assertEquals(SAMPLE.indexOf("helmet"), helmet.start);
	}

	@Test
	public void aWrapRowStartDoesNotStealThePreviousRow() {
		assertNull(PrefixPickHit.wordOnLine("abcdefghij world", 1, 0, 10));
		assertEquals("world", PrefixPickHit.wordOnLine("abcdefghij world", 1, 1,
				10));
	}

	@Test
	public void aColumnPastThisWrapRowStaysOnIt() {
		assertEquals("abcdefghij",
				PrefixPickHit.wordOnLine("abcdefghij world", 0, 15, 10));
		assertNull(PrefixPickHit.wordOnLine("abcdefghij world", 1, -3, 10));
	}

	@Test
	public void digitsStay() {
		assertEquals("2h", PrefixPickHit.wordOnLine("wait 2h then", 0, 5, 0));
	}

	@Test
	public void wordWrapUsesBreakRowsNotColumnStride() throws Exception {
		TextTree tree = new TextTree();
		tree.setWordWrap(true);
		tree.setLineBreakAt(17);
		tree.addBytesImpl(("The quick brown fox jumps over the lazy dog today "
				+ "and then some more words here for wrapping\n").getBytes("UTF-8"));
		TextTree.Line line = tree.getLines().getFirst();
		java.util.List<String> rows = PrefixPickHit.visualRows(line);
		assertTrue("expected several wraps", rows.size() >= 3);
		assertTrue("word wrap leaves a short first row",
				rows.get(0).length() < 17);
		assertTrue("next row does not start at wrapColumns",
				PrefixPickHit.rowStart(rows, 1) != 17);
		String plain = TextTree.deColorLine(line).toString();
		for (int r = 0; r < rows.size(); r++) {
			String row = rows.get(r);
			int col = 0;
			while (col < row.length() && !AlnumWordAt.isWordChar(row.charAt(col))) {
				col++;
			}
			if (col >= row.length()) {
				continue;
			}
			assertEquals(AlnumWordAt.at(row, col),
					PrefixPickHit.wordOnRows(plain, rows, r, col));
		}
	}

	@Test
	public void outOfRangeOrEmptyMisses() {
		assertNull(PrefixPickHit.wordOnLine(null, 0, 0, 0));
		assertNull(PrefixPickHit.wordOnLine("", 0, 0, 0));
		assertNull(word(0, SAMPLE.indexOf('('), 0));
		assertNull(word(5, 0, 10));
		assertNull(word(-1, 0, 10));
	}

	private static String word(final int wrapRow, final int visualCol,
			final int wrapColumns) {
		return PrefixPickHit.wordOnLine(SAMPLE, wrapRow, visualCol, wrapColumns);
	}
}
