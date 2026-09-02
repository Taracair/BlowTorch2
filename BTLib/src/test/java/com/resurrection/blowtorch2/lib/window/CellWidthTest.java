package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Display cells for paint, not wrap. ASCII maps stay 1; emoji in U+1F000–U+1FAFF
 * are 2; U+1FB00 sextants stay 1.
 */
public class CellWidthTest {

	@Test
	public void asciiIncludingSpaceIsOne() {
		assertEquals(1, CellWidth.cells(' '));
		assertEquals(1, CellWidth.cells('A'));
		assertEquals(1, CellWidth.cells(0x7E));
		assertEquals(3, CellWidth.cells("abc"));
	}

	@Test
	public void controlsAreZero() {
		assertEquals(0, CellWidth.cells(0));
		assertEquals(0, CellWidth.cells(0x09));
		assertEquals(0, CellWidth.cells(0x7F));
	}

	@Test
	public void combiningAndFormatAreZero() {
		assertEquals(0, CellWidth.cells(0x0301));
		assertEquals(0, CellWidth.cells(0x200D));
		assertEquals(0, CellWidth.cells(0xFE0F));
		assertEquals(1, CellWidth.cells("e\u0301"));
	}

	@Test
	public void ratEmojiIsTwoCells() {
		assertEquals(2, CellWidth.cells(0x1F400));
		assertEquals(2, CellWidth.cells(new String(Character.toChars(0x1F400))));
	}

	@Test
	public void sextantsAndBlockElementsStayOneForMaps() {
		assertEquals(1, CellWidth.cells(0x1FB00));
		assertEquals(1, CellWidth.cells(0x2588));
		assertEquals(1, CellWidth.cells(0x2591));
		assertEquals(1, CellWidth.cells(0x2800));
	}

	@Test
	public void cjkAndHangulAreTwo() {
		assertEquals(2, CellWidth.cells(0x4E00));
		assertEquals(2, CellWidth.cells(0xAC00));
		assertEquals(2, CellWidth.cells(0xFF21));
		assertEquals(1, CellWidth.cells(0x303F));
	}

	@Test
	public void cjkExtBIsTwo() {
		assertEquals(2, CellWidth.cells(0x20000));
	}

	@Test
	public void spanCountsDisplayCellsNotUtf16() {
		assertEquals(2, CellWidth.cells("\u4e00", 0, 1));
		final String rat = new String(Character.toChars(0x1F400));
		assertEquals(2, CellWidth.cells(rat, 0, rat.length()));
		assertEquals(3, CellWidth.cells("a" + rat, 0, 1 + rat.length()));
		assertEquals(0, CellWidth.cells("ab", 2, 2));
	}

	@Test
	public void nullAndEmptyAreZero() {
		assertEquals(0, CellWidth.cells((CharSequence) null));
		assertEquals(0, CellWidth.cells(""));
	}

	@Test
	public void ambiguousLatinStayOne() {
		assertEquals(1, CellWidth.cells(0x00A1));
		assertEquals(1, CellWidth.cells(0x03B1));
	}
}
