package com.resurrection.blowtorch2.lib.window;

/**
 * Row geometry for suggestion extras on the input bar. Android-free so the
 * phone's IME-edge failures can be pinned in a JVM test: suggestions stay
 * beside the typed line when that line has room, wrap down only when it does
 * not, and a wrap-down row needs chrome translationY (bottom padding alone
 * leaves that row on the IME under adjustNothing). Wrapped extras that leave
 * the ghost line stack upward into top padding.
 */
public final class GhostExtraLayout {

	private GhostExtraLayout() {
	}

	/**
	 * Where the inline suggestion sits relative to the caret on the last typed
	 * line. Multi-line alone does not force a wrap — only lack of room does.
	 */
	public static final class Fit {
		/** Draw x on the chosen line ({@code caretX} when beside, 0 when wrapped). */
		public final float x;
		/** True when the suggestion starts on the line below the typed line. */
		public final boolean wrapDown;
		/**
		 * Chrome lift rows for that wrap-down strip (0 when beside). Clearance
		 * is translationY while the IME is up, not taller bottom padding alone.
		 */
		public final int chromeLiftRows;

		Fit(final float x, final boolean wrapDown, final int chromeLiftRows) {
			this.x = x;
			this.wrapDown = wrapDown;
			this.chromeLiftRows = chromeLiftRows;
		}
	}

	/**
	 * Place one suggestion beside the caret when {@code suggestionWidth} fits in
	 * the remaining room on the line; otherwise start it on the next line down
	 * at x=0 and ask for one chrome-lift row.
	 *
	 * <p>Does not look at typed line count. A two-line field with room on the
	 * last line stays beside the caret — the old rule {@code lineCount > 1}
	 * always parked the ghost under the whole field at x=0.
	 */
	public static Fit fitBesideOrWrapDown(final float caretX,
			final float suggestionWidth, final float lineWidth) {
		if (lineWidth <= 0f || suggestionWidth < 0f) {
			return new Fit(Math.max(0f, caretX), false, 0);
		}
		float x = Math.max(0f, caretX);
		if (x > lineWidth) {
			x = lineWidth;
		}
		float room = lineWidth - x;
		if (suggestionWidth <= room) {
			return new Fit(x, false, 0);
		}
		return new Fit(0f, true, 1);
	}

	/**
	 * Top of extras row {@code row}. Row 0 stays on the ghost line (inline after
	 * the caret). Later rows stack <em>up</em> from the higher of the ghost line
	 * top and {@code contentTop}, so wraps stay in the visible band above the
	 * typed line — under {@code adjustNothing} the bar's lower edge already sits
	 * on the IME lift, and downward growth keeps those rows covered.
	 */
	public static float rowTop(final int row, final float ghostBaseline,
			final float contentTop, final float lineHeight, final float descent) {
		float ghostTop = ghostBaseline - lineHeight + descent;
		if (row <= 0) {
			return ghostTop;
		}
		float clear = Math.min(ghostTop, contentTop);
		return clear - row * lineHeight;
	}

	/**
	 * Top of extras row {@code row} relative to the ghost line only (content
	 * top equals the ghost line top).
	 */
	public static float rowTop(final int row, final float ghostBaseline,
			final float lineHeight, final float descent) {
		return rowTop(row, ghostBaseline, ghostBaseline - lineHeight + descent,
				lineHeight, descent);
	}

	/** Baseline for extras row {@code row}, matching {@link #rowTop}. */
	public static float rowBaseline(final int row, final float ghostBaseline,
			final float contentTop, final float lineHeight, final float descent) {
		return rowTop(row, ghostBaseline, contentTop, lineHeight, descent)
				+ lineHeight - descent;
	}

	/** Baseline stepping purely from the ghost line (row 0 family). */
	public static float rowBaseline(final int row, final float ghostBaseline,
			final float lineHeight) {
		return ghostBaseline + row * lineHeight;
	}

	/**
	 * How many {@code lineHeight} strips of <em>top</em> padding the bar must add
	 * above {@code contentTop} so every wrapped extras row fits in the visible
	 * band above the typed block.
	 */
	public static int paddingRows(final int maxRow, final float ghostBaseline,
			final float contentTop, final float lineHeight, final float descent) {
		if (maxRow <= 0 || lineHeight <= 0f) {
			return 0;
		}
		float highestTop = rowTop(maxRow, ghostBaseline, contentTop, lineHeight,
				descent);
		float need = contentTop - highestTop;
		if (need <= 0f) {
			return 0;
		}
		return (int) Math.ceil(need / lineHeight - 1e-4f);
	}

	/**
	 * Top of a wrap-down extras row. Row 1 is {@code firstBelowTop};
	 * later rows step down. Caret-middle lists use this instead of
	 * {@link #rowTop}, which stacks above the typed block.
	 */
	public static float belowFieldRowTop(final int row, final float firstBelowTop,
			final float lineHeight) {
		int step = row <= 1 ? 0 : row - 1;
		return firstBelowTop + step * lineHeight;
	}

	/**
	 * Extra chrome {@code translationY} magnitude (px) so a wrap-down suggestion
	 * row clears the IME under {@code adjustNothing}.
	 *
	 * <p>Bottom padding alone is not enough: the input bar is
	 * {@code ALIGN_PARENT_BOTTOM}, so growing {@code paddingBottom} keeps the
	 * bar's bottom on the IME edge and the ghost drawn there stays covered
	 * (phone after 2686d3e3). Only rows that actually wrapped down ask for this
	 * lift — not every multi-line field.
	 */
	public static int belowFieldChromeLiftPx(final int belowFieldRows,
			final float lineHeight) {
		if (belowFieldRows <= 0 || lineHeight <= 0f) {
			return 0;
		}
		return Math.round(belowFieldRows * lineHeight);
	}

	/**
	 * Bottom padding for rows under the typed text, and the chrome lift that
	 * must match it. A button taller than those rows leaves the surplus under
	 * the list — between the sentence and the list it was a blank band
	 * (phone, 30 Sep 2026). No rows, no pad: the button then sits beside the
	 * field, not under it.
	 */
	public static int belowFieldPadPx(final int belowFieldRows,
			final float lineHeight, final int buttonHeightPx) {
		int rowsPx = belowFieldChromeLiftPx(belowFieldRows, lineHeight);
		if (rowsPx <= 0) {
			return 0;
		}
		if (buttonHeightPx > rowsPx) {
			return buttonHeightPx;
		}
		return rowsPx;
	}

	/**
	 * How many rows a caret-middle list takes under the typed text.
	 * {@code maxRow} is the packer's highest row index. Row 0 shares the last
	 * typed line when the packer placed it there (room after the text). Only
	 * later rows are below. Drawing row 0 at the caret, rather than after the
	 * text, sat in the background of the sentence (phone, 30 Sep 2026).
	 */
	public static int caretListRowsBelow(final int maxRow) {
		if (maxRow <= 0) {
			return 0;
		}
		return maxRow;
	}

	/**
	 * Right margin that keeps every line clear of Hide/Send. The corner above
	 * the buttons stays empty. Dropping it when the text wraps draws the last
	 * line under the buttons (phone, 1 Oct 2026).
	 */
	public static int inputEndMarginPx(final int stripWidthPx) {
		return Math.max(0, stripWidthPx);
	}

	/**
	 * {@code translationY} for chrome that rides the IME. When the keyboard is
	 * down ({@code imeLiftPx == 0}), do not float the bar for a wrap-down row
	 * alone.
	 */
	public static float chromeTranslationY(final int imeLiftPx,
			final int belowFieldRows, final float lineHeight) {
		return chromeTranslationY(imeLiftPx,
				belowFieldChromeLiftPx(belowFieldRows, lineHeight));
	}

	/**
	 * Same as {@link #chromeTranslationY(int, int, float)} when the below-field
	 * clearance is already measured in px (from the EditText's line height).
	 */
	public static float chromeTranslationY(final int imeLiftPx,
			final int belowFieldLiftPx) {
		if (imeLiftPx <= 0) {
			return 0f;
		}
		return -(imeLiftPx + Math.max(0, belowFieldLiftPx));
	}

	/**
	 * Y used through the padding-growth fix (0882a2f6): wrapped rows stepped
	 * <em>down</em> below {@code contentBottom}. Fits inside a taller view, but
	 * under adjustNothing those pixels sit on the IME edge.
	 */
	public static float legacyDownwardRowTop(final int row, final float ghostBaseline,
			final float contentBottom, final float lineHeight, final float descent) {
		if (row <= 0) {
			return ghostBaseline - lineHeight + descent;
		}
		float clear = Math.max(ghostBaseline + descent, contentBottom);
		return clear + (row - 1) * lineHeight;
	}

	/**
	 * Y used through 30 Sep 2026 morning: row 0 on the ghost baseline, later rows
	 * at {@code below + (row - 1) * lineHeight}. When the ghost had continued onto
	 * a next line, row 1 jumped back to {@code below} and overlapped row 0.
	 */
	public static float legacyRowTop(final int row, final float ghostBaseline,
			final float below, final float lineHeight, final float descent) {
		if (row == 0) {
			return ghostBaseline - lineHeight + descent;
		}
		return below + (row - 1) * lineHeight;
	}

	/** One pass of side-by-side packing. */
	public static final class Pack {
		/** Row for each width, or -1 when hidden past the ceiling. */
		public final int[] rows;
		/** Left edge for each width (valid when {@code rows[i] >= 0}). */
		public final float[] xs;
		/**
		 * Highest row index used (0 when only the ghost line was used). -1
		 * when nothing was placed. Same meaning as the old packer return.
		 */
		public final int maxRow;
		/** How many widths were left unplaced. */
		public final int hiddenCount;

		Pack(final int[] rows, final float[] xs, final int maxRow,
				final int hiddenCount) {
			this.rows = rows;
			this.xs = xs;
			this.maxRow = maxRow;
			this.hiddenCount = hiddenCount;
		}

		/** How many rows below the ghost line were used (0 when maxRow is 0). */
		public int rowsBelowGhost() {
			return maxRow < 0 ? 0 : maxRow;
		}
	}

	/**
	 * Pack suggestion widths side by side, wrapping when a width does not fit.
	 *
	 * @param widths each suggestion's drawn width (index mark included).
	 * @param avail width of one row.
	 * @param startX where the first item begins on row 0 (0 = left edge).
	 * @param gap space between two items on the same row.
	 * @param rowCap most row index allowed; 0 keeps everything on row 0 only.
	 */
	public static Pack pack(final float[] widths, final float avail,
			final float startX, final float gap, final int rowCap) {
		if (widths == null || widths.length == 0) {
			return new Pack(new int[0], new float[0], -1, 0);
		}
		int[] rows = new int[widths.length];
		float[] xs = new float[widths.length];
		for (int i = 0; i < rows.length; i++) {
			rows[i] = -1;
		}
		int row = 0;
		float x = startX;
		int hidden = 0;
		int maxRow = -1;
		boolean onRow = false;
		for (int i = 0; i < widths.length; i++) {
			float w = widths[i];
			if (w < 0f) {
				continue;
			}
			float lead = onRow ? gap : 0f;
			if (x + lead + w > avail) {
				if (row + 1 > rowCap) {
					hidden = countFrom(widths, i);
					break;
				}
				row++;
				x = 0f;
				onRow = false;
				lead = 0f;
				if (w > avail) {
					w = avail;
				}
			}
			rows[i] = row;
			xs[i] = x + lead;
			x += lead + w;
			onRow = true;
			if (row > maxRow) {
				maxRow = row;
			}
		}
		return new Pack(rows, xs, maxRow, hidden);
	}

	private static int countFrom(final float[] widths, final int from) {
		int n = 0;
		for (int i = from; i < widths.length; i++) {
			if (widths[i] >= 0f) {
				n++;
			}
		}
		return n;
	}
}
