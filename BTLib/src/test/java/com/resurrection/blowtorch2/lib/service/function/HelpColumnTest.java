package com.resurrection.blowtorch2.lib.service.function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HelpColumnTest {

	@Test
	public void padMatchesTheOldEighteenCharacterColumn() {
		assertEquals(18, HelpColumn.pad(".wait").length());
		assertEquals(".wait             ", HelpColumn.pad(".wait"));
		assertEquals(".echo             ", HelpColumn.pad(".echo"));
	}

	@Test
	public void shortRowDoesNotWrap() {
		String row = HelpColumn.formatRow(".echo", "show or hide", 80);
		assertEquals("  .echo              show or hide", row);
		assertFalse(row.contains("\n"));
	}

	@Test
	public void emptyDescriptionIsPaddedNameOnly() {
		String row = HelpColumn.formatRow(".echo", "", 40);
		assertEquals("  .echo             ", row);
		assertFalse(row.contains("\n"));
		assertEquals("  .echo             ", HelpColumn.formatRow(".echo", null, 40));
	}

	@Test
	public void longDescriptionAtPhoneWidthStaysWithinForty() {
		String desc = "pause the rest of this line (.wait 5s / #wait 5m10s); "
				+ ".wait stop cancels; .wait show lists the queue";
		assertLinesFit(HelpColumn.formatRow(".wait", desc, 40), 40);
	}

	@Test
	public void longDescriptionAtEightyStaysWithinEighty() {
		String desc = "pause the rest of this line (.wait 5s / #wait 5m10s); "
				+ ".wait stop cancels; .wait show lists the queue";
		assertLinesFit(HelpColumn.formatRow(".wait", desc, 80), 80);
	}

	@Test
	public void continuationIndentIsLeadingSpacesPlusPaddedNamePlusSeparator() {
		String name = ".wait";
		int indent = HelpColumn.continuationIndent(name);
		assertEquals(2 + HelpColumn.pad(name).length() + 1, indent);
		assertEquals(21, indent);

		String row = HelpColumn.formatRow(name,
				"one two three four five six seven eight nine ten", 40);
		String[] lines = row.split("\n", -1);
		assertTrue(lines.length > 1);
		assertTrue(lines[0].startsWith("  " + name));
		for (int i = 1; i < lines.length; i++) {
			assertEquals(spaces(indent), leading(lines[i], indent));
			assertFalse(lines[i].startsWith("."));
		}
	}

	@Test
	public void breaksOnSpacesNotMidWord() {
		String row = HelpColumn.formatRow(".x",
				"one two three four five six seven", 40);
		assertEquals(
				"  .x                 one two three four\n"
						+ "                     five six seven",
				row);
	}

	@Test
	public void overlongTokenIsHardBrokenSoTheLineStillFits() {
		String row = HelpColumn.formatRow(".x",
				"short abcdefghijklmnopqrstuvwxyz end", 40);
		assertLinesFit(row, 40);
		String[] lines = row.split("\n", -1);
		assertEquals("  .x                 short", lines[0]);
		assertEquals("                     abcdefghijklmnopqrs", lines[1]);
		assertEquals("                     tuvwxyz end", lines[2]);
	}

	@Test
	public void nameLongerThanPadWidthStillWrapsUnderTheDescription() {
		String name = ".thiscommandnameistoolong";
		assertTrue(name.length() > HelpColumn.NAME_WIDTH);
		assertEquals(name, HelpColumn.pad(name));

		String shortRow = HelpColumn.formatRow(name, "ok", 80);
		assertEquals("  " + name + " ok", shortRow);

		String row = HelpColumn.formatRow(name,
				"one two three four five six seven eight", 40);
		int indent = HelpColumn.continuationIndent(name);
		assertEquals(2 + name.length() + 1, indent);
		assertLinesFit(row, 40);
		String[] lines = row.split("\n", -1);
		assertTrue(lines[0].startsWith("  " + name + " "));
		assertTrue(lines.length > 1);
		for (int i = 1; i < lines.length; i++) {
			assertEquals(spaces(indent), leading(lines[i], indent));
		}
	}

	@Test
	public void wrapWidthUsesNawsWhenWiderThanTheNameColumn() {
		assertEquals(HelpColumn.DEFAULT_WIDTH, HelpColumn.wrapWidth(0));
		assertEquals(HelpColumn.DEFAULT_WIDTH, HelpColumn.wrapWidth(26));
		assertEquals(HelpColumn.DEFAULT_WIDTH, HelpColumn.wrapWidth(-1));
		assertEquals(48, HelpColumn.DEFAULT_WIDTH);
		assertEquals(40, HelpColumn.wrapWidth(40));
		assertEquals(80, HelpColumn.wrapWidth(80));
		assertEquals(27, HelpColumn.wrapWidth(27));
	}

	@Test
	public void missingConnectionUsesTheDefaultWidth() {
		assertEquals(HelpColumn.DEFAULT_WIDTH, HelpCommand.wrapWidth(null));
	}

	private static void assertLinesFit(final String row, final int width) {
		String[] lines = row.split("\n", -1);
		assertTrue(lines.length >= 1);
		for (String line : lines) {
			assertTrue(line + " len=" + visibleLength(line),
					visibleLength(line) <= width);
		}
	}

	/** Help rows are uncoloured; still ignore CSI if a sequence is present. */
	private static int visibleLength(final String line) {
		return line.replaceAll("\u001B\\[[0-9;]*m", "").length();
	}

	private static String spaces(final int n) {
		StringBuilder b = new StringBuilder(n);
		for (int i = 0; i < n; i++) {
			b.append(' ');
		}
		return b.toString();
	}

	private static String leading(final String line, final int n) {
		if (line.length() < n) {
			return line;
		}
		return line.substring(0, n);
	}
}
