package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Phone 30 Sep 2026: after the first suggestion line, extras fell under the
 * keyboard. Growing bottom padding so rows "fit the view" was not enough under
 * {@code adjustNothing} — the field's lower edge already sits on the IME lift,
 * so rows stacked downward stay in the covered band. Wrapped extras must stack
 * upward into top padding (visible band above the typed line).
 */
public class GhostExtraLayoutTest {

	private static final float LINE = 12f;
	private static final float DESCENT = 3f;
	private static final float TYPED_BASELINE = 40f;
	private static final float CONTENT_TOP = 0f;
	private static final float CONTENT_BOTTOM = TYPED_BASELINE + DESCENT;

	@Test
	public void legacyDownwardWrapSitsBelowTypedBlockTowardKeyboard() {
		float top1 = GhostExtraLayout.legacyDownwardRowTop(1, TYPED_BASELINE,
				CONTENT_BOTTOM, LINE, DESCENT);
		assertTrue(
				"legacy downward row 1 must sit at/below the typed block (IME edge)",
				top1 >= CONTENT_BOTTOM);
	}

	@Test
	public void wrappedExtrasStackAboveGhostLineNotTowardKeyboard() {
		float top0 = GhostExtraLayout.rowTop(0, TYPED_BASELINE, CONTENT_TOP, LINE,
				DESCENT);
		float top1 = GhostExtraLayout.rowTop(1, TYPED_BASELINE, CONTENT_TOP, LINE,
				DESCENT);
		assertTrue(
				"row 1 top " + top1 + " must sit above row 0 ending at " + (top0 + LINE),
				top1 + LINE <= top0 + 1e-4f);
	}

	@Test
	public void firstWrappedRowSitsImmediatelyAboveGhostLine() {
		float ghostTop = GhostExtraLayout.rowTop(0, TYPED_BASELINE, CONTENT_TOP, LINE,
				DESCENT);
		float top1 = GhostExtraLayout.rowTop(1, TYPED_BASELINE, ghostTop, LINE,
				DESCENT);
		assertEquals(ghostTop - LINE, top1, 0f);
	}

	@Test
	public void wrappedRowsClearTypedBlockWhenGhostIsLower() {
		float ghostBaseline = TYPED_BASELINE + LINE;
		float top1 = GhostExtraLayout.rowTop(1, ghostBaseline, CONTENT_TOP, LINE,
				DESCENT);
		assertTrue(
				"row 1 top " + top1 + " must clear typed content starting at "
						+ CONTENT_TOP,
				top1 + LINE <= CONTENT_TOP + 1e-4f);
	}

	@Test
	public void topPaddingRowsCoverHighestWrappedExtra() {
		float[] widths = { 80f, 80f, 80f };
		GhostExtraLayout.Pack pack = GhostExtraLayout.pack(widths, 100f, 90f, 8f, 5);
		assertEquals(3, pack.rowsBelowGhost());
		float highest = GhostExtraLayout.rowTop(pack.maxRow, TYPED_BASELINE,
				CONTENT_TOP, LINE, DESCENT);
		int padRows = GhostExtraLayout.paddingRows(pack.maxRow, TYPED_BASELINE,
				CONTENT_TOP, LINE, DESCENT);
		assertTrue(
				"top padding must cover the highest drawn row",
				highest >= CONTENT_TOP - padRows * LINE - 1e-4f);
	}

	@Test
	public void paddingRowsZeroWhenEverythingFitsOnGhostLine() {
		float[] widths = { 30f, 30f };
		GhostExtraLayout.Pack pack = GhostExtraLayout.pack(widths, 100f, 0f, 8f, 5);
		assertEquals(0, pack.maxRow);
		assertEquals(0, GhostExtraLayout.paddingRows(pack.maxRow, TYPED_BASELINE,
				CONTENT_TOP, LINE, DESCENT));
	}

	@Test
	public void packStartingPastTheGhostStillWrapsOntoLaterRows() {
		float[] widths = { 30f, 90f, 90f };
		GhostExtraLayout.Pack pack = GhostExtraLayout.pack(widths, 100f, 60f, 8f, 5);
		assertEquals(0, pack.rows[0]);
		assertEquals(1, pack.rows[1]);
		assertEquals(2, pack.rows[2]);
		assertEquals(2, pack.rowsBelowGhost());
	}

	@Test
	public void packHonoursRowCapAndCountsHidden() {
		float[] widths = { 80f, 80f, 80f };
		GhostExtraLayout.Pack pack = GhostExtraLayout.pack(widths, 100f, 0f, 8f, 0);
		assertEquals(0, pack.rowsBelowGhost());
		assertEquals(0, pack.rows[0]);
		assertEquals(-1, pack.rows[1]);
		assertEquals(-1, pack.rows[2]);
		assertEquals(2, pack.hiddenCount);
	}

	@Test
	public void legacyWrappedRowOverlapsWhenGhostContinuedALine() {
		float ghostBaseline = TYPED_BASELINE + LINE;
		float top0 = GhostExtraLayout.legacyRowTop(0, ghostBaseline, CONTENT_BOTTOM,
				LINE, DESCENT);
		float top1 = GhostExtraLayout.legacyRowTop(1, ghostBaseline, CONTENT_BOTTOM,
				LINE, DESCENT);
		assertTrue(
				"legacy row 1 must still overlap row 0 (the phone bug)",
				top1 < top0 + LINE);
	}

	/**
	 * Phone after 179846fe: suggestions under a full second typed line still sat
	 * on the IME when multi-line always forced below-the-field at x=0. Beside the
	 * caret when the last line has room; wrap down (with chrome lift while the
	 * IME is up) only when that line is full. Extras pack from the caret, not
	 * dumped under the field because lineCount &gt; 1.
	 */
	@Test
	public void lastLineWithRoomKeepsSuggestionBesideCaretNoLift() {
		GhostExtraLayout.Fit fit = GhostExtraLayout.fitBesideOrWrapDown(40f, 30f,
				100f);
		assertEquals(40f, fit.x, 0f);
		assertEquals(false, fit.wrapDown);
		assertEquals(0, fit.chromeLiftRows);
		assertEquals(0, GhostExtraLayout.belowFieldChromeLiftPx(fit.chromeLiftRows,
				LINE));
	}

	@Test
	public void lastLineFullWrapsDownWithChromeLiftWhileImeUp() {
		GhostExtraLayout.Fit fit = GhostExtraLayout.fitBesideOrWrapDown(90f, 40f,
				100f);
		assertEquals(0f, fit.x, 0f);
		assertEquals(true, fit.wrapDown);
		assertEquals(1, fit.chromeLiftRows);
		assertEquals(Math.round(LINE),
				GhostExtraLayout.belowFieldChromeLiftPx(fit.chromeLiftRows, LINE));
		int ime = 400;
		assertEquals(-(ime + Math.round(LINE)),
				GhostExtraLayout.chromeTranslationY(ime, fit.chromeLiftRows, LINE),
				0f);
		assertEquals(
				"no IME: do not float the bar for a wrap-down row alone",
				0f,
				GhostExtraLayout.chromeTranslationY(0, fit.chromeLiftRows, LINE),
				0f);
	}

	@Test
	public void severalSuggestionsPackBesideThenWrapNotAllUnderField() {
		// Caret mid last line of a multi-line field — not forced to x=0 under it.
		float caretX = 50f;
		float gap = 8f;
		float[] widths = { 15f, 15f, 70f };
		GhostExtraLayout.Pack pack = GhostExtraLayout.pack(widths, 100f, caretX, gap,
				5);
		assertEquals(0, pack.rows[0]);
		assertEquals(caretX, pack.xs[0], 0f);
		assertEquals(caretX + 15f + gap, pack.xs[1], 0f);
		assertEquals(0, pack.rows[1]);
		assertEquals(1, pack.rows[2]);
		assertEquals(0f, pack.xs[2], 0f);
		assertEquals(1, pack.rowsBelowGhost());
	}

	@Test
	public void roomAfterTheTypedTextKeepsTheListOnThatLine() {
		float textEnd = 40f;
		float[] widths = { 20f, 20f };
		GhostExtraLayout.Pack pack = GhostExtraLayout.pack(widths, 100f, textEnd, 8f,
				1);
		assertEquals(0, pack.rows[0]);
		assertEquals(textEnd, pack.xs[0], 0f);
		assertEquals(textEnd + 20f + 8f, pack.xs[1], 0f);
		assertEquals(0, pack.rows[1]);
		assertEquals(0, pack.rowsBelowGhost());
	}

	@Test
	public void caretWrapStepsDownUnderTheFieldNotAboveTheText() {
		float firstBelow = 80f;
		assertEquals(firstBelow, GhostExtraLayout.belowFieldRowTop(1, firstBelow, LINE),
				0f);
		assertEquals(firstBelow + LINE,
				GhostExtraLayout.belowFieldRowTop(2, firstBelow, LINE), 0f);
		float above = GhostExtraLayout.rowTop(1, TYPED_BASELINE, CONTENT_TOP, LINE,
				DESCENT);
		assertTrue(above < 0f);
		assertTrue(GhostExtraLayout.belowFieldRowTop(1, firstBelow, LINE) > above);
	}

	@Test
	public void wrapDownChromeLiftIsZeroWhenBeside() {
		assertEquals(0, GhostExtraLayout.belowFieldChromeLiftPx(0, LINE));
	}

	@Test
	public void buttonTallerThanTheListPadsUnderTheRowsAndLiftsWithThatPad() {
		assertEquals(0, GhostExtraLayout.belowFieldPadPx(0, LINE, 70));
		assertEquals(Math.round(LINE), GhostExtraLayout.belowFieldPadPx(1, LINE, 4));
		assertEquals(70, GhostExtraLayout.belowFieldPadPx(1, LINE, 70));
		assertEquals(-(400 + 70),
				GhostExtraLayout.chromeTranslationY(400, 70), 0f);
	}

	@Test
	public void caretListOnTheLineDoesNotLiftUntilItWraps() {
		assertEquals(0, GhostExtraLayout.caretListRowsBelow(-1));
		assertEquals(0, GhostExtraLayout.caretListRowsBelow(0));
		assertEquals(1, GhostExtraLayout.caretListRowsBelow(1));
		assertEquals(0, GhostExtraLayout.belowFieldChromeLiftPx(
				GhostExtraLayout.caretListRowsBelow(0), LINE));
		int rows = GhostExtraLayout.caretListRowsBelow(1);
		assertEquals(Math.round(LINE),
				GhostExtraLayout.belowFieldChromeLiftPx(rows, LINE));
		int ime = 400;
		assertEquals(-(ime + Math.round(LINE)),
				GhostExtraLayout.chromeTranslationY(ime, rows, LINE), 0f);
		float first = 80f;
		assertEquals(first, GhostExtraLayout.belowFieldRowTop(1, first, LINE), 0f);
	}

	@Test
	public void caretListThatFitsStaysOnTheLineAndAFullLineWrapsDown() {
		GhostExtraLayout.Pack fits = GhostExtraLayout.pack(
				new float[] { 20f, 20f }, 100f, 40f, 8f, 1);
		assertEquals(0, fits.maxRow);
		assertEquals(0, GhostExtraLayout.caretListRowsBelow(fits.maxRow));
		GhostExtraLayout.Pack full = GhostExtraLayout.pack(
				new float[] { 80f }, 100f, 40f, 8f, 1);
		assertEquals(1, full.rows[0]);
		assertEquals(0f, full.xs[0], 0f);
		assertEquals(1, GhostExtraLayout.caretListRowsBelow(full.maxRow));
	}

	@Test
	public void endMarginStaysBesideTheButtons() {
		assertEquals(48, GhostExtraLayout.inputEndMarginPx(48));
		assertEquals(0, GhostExtraLayout.inputEndMarginPx(0));
	}

	@Test
	public void chromeTranslationAddsWrapDownRowOnlyWhileImeIsUp() {
		int ime = 400;
		assertEquals(-ime, GhostExtraLayout.chromeTranslationY(ime, 0, LINE), 0f);
		assertEquals(-(ime + Math.round(LINE)),
				GhostExtraLayout.chromeTranslationY(ime, 1, LINE), 0f);
		assertEquals(
				"no IME: do not float the bar for a wrap-down row alone",
				0f, GhostExtraLayout.chromeTranslationY(0, 1, LINE), 0f);
	}
}
