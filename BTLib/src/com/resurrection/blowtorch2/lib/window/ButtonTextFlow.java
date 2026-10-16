package com.resurrection.blowtorch2.lib.window;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Viewport holes for on-screen buttons. Android-free so the merge / column /
 * wrap rules can be JVM-tested. Holes are viewport pixels; wrapping at
 * {@code mWrapColumns} stays in {@link TextTree}.
 */
public final class ButtonTextFlow {

	private ButtonTextFlow() {
	}

	public static final class RectPx {
		public final int left;
		public final int top;
		public final int right;
		public final int bottom;

		public RectPx(final int left, final int top, final int right, final int bottom) {
			this.left = left;
			this.top = top;
			this.right = right;
			this.bottom = bottom;
		}
	}

	/** One packed run: glyphs {@code [srcStart, srcEnd)} at {@code destCol} on {@code visualRow}. */
	public static final class Run {
		public final int visualRow;
		public final int destCol;
		public final int srcStart;
		public final int srcEnd;

		public Run(final int visualRow, final int destCol, final int srcStart, final int srcEnd) {
			this.visualRow = visualRow;
			this.destCol = destCol;
			this.srcStart = srcStart;
			this.srcEnd = srcEnd;
		}
	}

	/**
	 * All buttons overlapping {@code [rowTop, rowBottom)} become one
	 * {@code [left, right)} blob from the leftmost edge to the rightmost.
	 * Gaps between two buttons on this row are not free.
	 */
	public static RectPx mergedBlobPx(final List<RectPx> buttons, final int rowTop,
			final int rowBottom) {
		if (buttons == null || buttons.isEmpty() || rowBottom <= rowTop) {
			return null;
		}
		int minL = Integer.MAX_VALUE;
		int maxR = Integer.MIN_VALUE;
		int minT = Integer.MAX_VALUE;
		int maxB = Integer.MIN_VALUE;
		boolean any = false;
		for (int i = 0; i < buttons.size(); i++) {
			RectPx b = buttons.get(i);
			if (b == null) {
				continue;
			}
			if (b.bottom <= rowTop || b.top >= rowBottom) {
				continue;
			}
			if (b.right <= b.left) {
				continue;
			}
			any = true;
			if (b.left < minL) {
				minL = b.left;
			}
			if (b.right > maxR) {
				maxR = b.right;
			}
			if (b.top < minT) {
				minT = b.top;
			}
			if (b.bottom > maxB) {
				maxB = b.bottom;
			}
		}
		if (!any) {
			return null;
		}
		return new RectPx(minL, minT, maxR, maxB);
	}

	/** Grow a hole by {@code px} on every side. Zero and negative leave it. */
	public static RectPx outset(final RectPx rect, final int px) {
		if (rect == null || px <= 0) {
			return rect;
		}
		return new RectPx(rect.left - px, rect.top - px, rect.right + px, rect.bottom + px);
	}

	/**
	 * Blob in viewport px → inclusive-exclusive column span in the text grid.
	 * {@code scrollX} is canvas pixels already panned ({@code Window} mScrollX).
	 * Columns are viewport columns ({@code widthPx/cellW}), not canvas columns.
	 */
	public static int[] blobColumns(final RectPx blobPx, final int cellW, final int scrollX,
			final int viewportCols) {
		if (blobPx == null || cellW <= 0 || viewportCols <= 0) {
			return null;
		}
		int left = floorDiv(blobPx.left + scrollX, cellW);
		int right = ceilDiv(blobPx.right + scrollX, cellW);
		if (left < 0) {
			left = 0;
		}
		if (right > viewportCols) {
			right = viewportCols;
		}
		if (left >= right) {
			return null;
		}
		return new int[] { left, right };
	}

	/** Free column ranges left-to-right: {@code [0,L)} and {@code [R, viewportCols)} if those have width&gt;0. */
	public static int[][] freeColumns(final int[] blobCols, final int viewportCols) {
		if (viewportCols <= 0) {
			return new int[0][];
		}
		if (blobCols == null || blobCols.length < 2 || blobCols[0] >= blobCols[1]) {
			return new int[][] { { 0, viewportCols } };
		}
		int L = blobCols[0];
		int R = blobCols[1];
		if (L < 0) {
			L = 0;
		}
		if (R > viewportCols) {
			R = viewportCols;
		}
		if (L <= 0 && R >= viewportCols) {
			return new int[0][];
		}
		if (L <= 0) {
			return new int[][] { { R, viewportCols } };
		}
		if (R >= viewportCols) {
			return new int[][] { { 0, L } };
		}
		return new int[][] { { 0, L }, { R, viewportCols } };
	}

	/**
	 * Pack {@code charCount} glyphs into free segments, producing visual runs.
	 * Word wrap at last space in the overflowing segment unless {@code hardBreak}.
	 * Extra visual rows reuse {@code freeCols}; for a hole that changes per row
	 * use {@link #layoutAround}.
	 */
	public static List<Run> layout(final int charCount, final int[][] freeCols,
			final boolean wordWrap, final boolean hardBreak) {
		return layout(charCount, freeCols, wordWrap, hardBreak, null);
	}

	public static List<Run> layout(final int charCount, final int[][] freeCols,
			final boolean wordWrap, final boolean hardBreak, final CharSequence text) {
		if (charCount <= 0) {
			return Collections.emptyList();
		}
		if (freeCols == null || freeCols.length == 0) {
			return Collections.emptyList();
		}
		ArrayList<Run> runs = new ArrayList<Run>();
		int src = 0;
		int visualRow = 0;
		int guard = 0;
		final int guardMax = charCount + 2;
		while (src < charCount && guard++ < guardMax) {
			int placed = packRow(runs, visualRow, freeCols, src, charCount, wordWrap,
					hardBreak, text);
			src += placed;
			if (src >= charCount) {
				break;
			}
			if (placed == 0) {
				break;
			}
			visualRow++;
		}
		return runs;
	}

	/**
	 * Same packing as {@link #layout}, but the hole is recomputed for each
	 * visual row from {@code buttons}. A full-width blob skips that row without
	 * consuming glyphs. Extra rows step by {@code rowHeight} down the view.
	 */
	public static List<Run> layoutAround(final int charCount, final List<RectPx> buttons,
			final int firstRowTop, final int rowHeight, final int cellW, final int viewportCols,
			final boolean wordWrap, final boolean hardBreak, final CharSequence text) {
		return layoutAround(charCount, buttons, firstRowTop, rowHeight, rowHeight, cellW,
				viewportCols, wordWrap, hardBreak, text);
	}

	/**
	 * @param rowStep signed view-Y delta to the next visual row. Negative when
	 *        newest-at-top, because extra rows paint toward smaller screen Y.
	 */
	public static List<Run> layoutAround(final int charCount, final List<RectPx> buttons,
			final int firstRowTop, final int rowHeight, final int rowStep, final int cellW,
			final int viewportCols, final boolean wordWrap, final boolean hardBreak,
			final CharSequence text) {
		if (charCount <= 0 || rowHeight <= 0 || cellW <= 0 || viewportCols <= 0) {
			return Collections.emptyList();
		}
		int step = rowStep == 0 ? rowHeight : rowStep;
		ArrayList<Run> runs = new ArrayList<Run>();
		int src = 0;
		int visualRow = 0;
		int guard = 0;
		int skipBudget = skipRowBudget(buttons, firstRowTop, rowHeight);
		final int guardMax = charCount + skipBudget + 2;
		while (src < charCount && guard++ < guardMax) {
			int rowTop = firstRowTop + visualRow * step;
			int rowBottom = rowTop + rowHeight;
			RectPx blob = mergedBlobPx(buttons, rowTop, rowBottom);
			int[] blobCols = blobColumns(blob, cellW, 0, viewportCols);
			int[][] free = freeColumns(blobCols, viewportCols);
			if (free.length == 0) {
				visualRow++;
				continue;
			}
			int placed = packRow(runs, visualRow, free, src, charCount, wordWrap, hardBreak,
					text);
			src += placed;
			if (src >= charCount) {
				break;
			}
			if (placed == 0) {
				break;
			}
			visualRow++;
		}
		return runs;
	}

	/**
	 * Pack this wrap-row into the free columns of {@code [rowTop, rowTop+rowHeight)}
	 * only. Leftover is not placed on later rows — that inserted enters and
	 * reflowed the rest of the canvas while scrolling (phone, 17 Sep 2026).
	 * A full-width blob yields an empty list (the row is swallowed).
	 */
	public static List<Run> layoutInRow(final int charCount, final List<RectPx> buttons,
			final int rowTop, final int rowHeight, final int cellW, final int viewportCols,
			final boolean wordWrap, final boolean hardBreak, final CharSequence text) {
		if (charCount <= 0 || rowHeight <= 0 || cellW <= 0 || viewportCols <= 0) {
			return Collections.emptyList();
		}
		RectPx blob = mergedBlobPx(buttons, rowTop, rowTop + rowHeight);
		if (blob == null) {
			return Collections.emptyList();
		}
		int[] blobCols = blobColumns(blob, cellW, 0, viewportCols);
		if (blobCols == null) {
			return Collections.emptyList();
		}
		int[][] free = freeColumns(blobCols, viewportCols);
		if (free.length == 0) {
			return Collections.emptyList();
		}
		ArrayList<Run> runs = new ArrayList<Run>();
		packRow(runs, 0, free, 0, charCount, wordWrap, hardBreak, text);
		return runs;
	}

	private static int packRow(final List<Run> runs, final int visualRow, final int[][] freeCols,
			final int src0, final int charCount, final boolean wordWrap, final boolean hardBreak,
			final CharSequence text) {
		int src = src0;
		int placed = 0;
		for (int s = 0; s < freeCols.length; s++) {
			if (src >= charCount) {
				break;
			}
			int[] seg = freeCols[s];
			if (seg == null || seg.length < 2) {
				continue;
			}
			int start = seg[0];
			int end = seg[1];
			int width = end - start;
			if (width <= 0) {
				continue;
			}
			int remaining = charCount - src;
			int take = remaining < width ? remaining : width;
			if (remaining > width && !hardBreak && wordWrap) {
				int wrapped = lastSpaceTake(text, src, width);
				if (wrapped > 0) {
					take = wrapped;
				}
			}
			take = snapTake(text, src, take);
			if (take <= 0) {
				continue;
			}
			runs.add(new Run(visualRow, start, src, src + take));
			src += take;
			placed += take;
		}
		return placed;
	}

	/**
	 * Cell remainders beside the button. {@code [0]} is pixels from the left
	 * cell boundary to {@code blobLeft}; {@code [1]} is pixels from
	 * {@code blobRight} out to the next cell boundary.
	 */
	public static int[] sideSlack(final int blobLeft, final int blobRight, final int cellW) {
		if (cellW <= 0) {
			return new int[] { 0, 0 };
		}
		int left = Math.floorMod(blobLeft, cellW);
		int rightMod = Math.floorMod(blobRight, cellW);
		int right = rightMod == 0 ? 0 : cellW - rightMod;
		return new int[] { left, right };
	}

	/** Shared remainder before the half-cell bias. The wider remainder closes. */
	public static int equalSideGap(final int blobLeft, final int blobRight, final int cellW) {
		int[] slack = sideSlack(blobLeft, blobRight, cellW);
		return Math.min(slack[0], slack[1]);
	}

	/**
	 * Left-run view X stays on the unholed grid. A non-zero shift is one dx for
	 * the whole run, so column 0 moves with the hole (player, 15 Oct 2026).
	 */
	public static int leftPocketShift(final int blobLeft, final int blobRight, final int cellW) {
		return 0;
	}

	/**
	 * Added to {@code destCol * cellW}. The half cell keeps the right-hand letter
	 * clear of the button until the pan reaches the middle of the cell.
	 */
	public static int rightPocketShift(final int blobLeft, final int blobRight, final int cellW) {
		int[] slack = sideSlack(blobLeft, blobRight, cellW);
		int half = cellW <= 0 ? 0 : cellW / 2;
		return Math.min(slack[0], slack[1]) - slack[1] + half;
	}

	private static int skipRowBudget(final List<RectPx> buttons, final int firstRowTop,
			final int rowHeight) {
		if (buttons == null || buttons.isEmpty() || rowHeight <= 0) {
			return 0;
		}
		int maxAbs = 0;
		for (int i = 0; i < buttons.size(); i++) {
			RectPx b = buttons.get(i);
			if (b == null) {
				continue;
			}
			int dTop = b.top - firstRowTop;
			if (dTop < 0) {
				dTop = -dTop;
			}
			int dBot = b.bottom - firstRowTop;
			if (dBot < 0) {
				dBot = -dBot;
			}
			if (dTop > maxAbs) {
				maxAbs = dTop;
			}
			if (dBot > maxAbs) {
				maxAbs = dBot;
			}
		}
		return maxAbs / rowHeight + 2;
	}

	/** Surplus pixels from extra visual rows versus buffer wrap rows. Never negative. */
	public static int extraVisualPx(final int visualRowsDrawn, final int wrapRowsDrawn,
			final int lineSize) {
		if (lineSize <= 0) {
			return 0;
		}
		int extra = visualRowsDrawn - wrapRowsDrawn;
		if (extra <= 0) {
			return 0;
		}
		return extra * lineSize;
	}

	/**
	 * View-X for a viewport column on a punched row. {@code destCol} is a
	 * viewport column; subtract the pan fraction so the remnant tracks rows
	 * without a hole (phone, 17 Sep 2026).
	 */
	public static float panViewX(final int destCol, final int sliceFrom, final float cellW,
			final float scrollX) {
		if (cellW <= 0f) {
			return 0f;
		}
		int from = sliceFrom < 0 ? 0 : sliceFrom;
		return (destCol + from) * cellW - scrollX;
	}

	/** Inverse of {@link #panViewX}: viewport column under view-X. */
	public static int panDestCol(final float viewX, final int sliceFrom, final float cellW,
			final float scrollX) {
		if (cellW <= 0f) {
			return 0;
		}
		int from = sliceFrom < 0 ? 0 : sliceFrom;
		int dest = (int) Math.floor((viewX + scrollX) / cellW) - from;
		return dest;
	}

	/**
	 * View-X for a left-pocket glyph. Same pan as {@link #panViewX}: a holed
	 * row starts on the same column as a row with no hole.
	 */
	public static float leftPocketViewX(final int destCol, final int sliceFrom,
			final float blobLeft, final float blobRight, final float cellW, final float scrollX) {
		float x = panViewX(destCol, sliceFrom, cellW, scrollX);
		if (cellW <= 0f) {
			return x;
		}
		return x + leftPocketShift(Math.round(blobLeft), Math.round(blobRight), Math.round(cellW));
	}

	/** Inverse of {@link #leftPocketViewX}. */
	public static int leftPocketDestCol(final float viewX, final int sliceFrom,
			final float blobLeft, final float blobRight, final float cellW, final float scrollX) {
		if (cellW <= 0f) {
			return panDestCol(viewX, sliceFrom, cellW, scrollX);
		}
		float shift = leftPocketShift(Math.round(blobLeft), Math.round(blobRight),
				Math.round(cellW));
		return panDestCol(viewX - shift, sliceFrom, cellW, scrollX);
	}

	/**
	 * View-X for a right-pocket glyph. 1px of scroll moves 1px; a screen-fixed
	 * {@code destCol} would jump ~cellW at the column boundary (phone, 17 Sep
	 * 2026). At scroll fraction 0 the glyph starts half a cell past
	 * {@link #equalSideGap}. The left run does not take that half.
	 */
	public static float rightPocketViewX(final int destCol, final int holeCol1,
			final float blobLeft, final float blobRight, final float cellW, final float scrollX) {
		if (cellW <= 0f) {
			return blobRight;
		}
		if (destCol < holeCol1) {
			return blobRight;
		}
		int shift = rightPocketShift(Math.round(blobLeft), Math.round(blobRight),
				Math.round(cellW));
		return destCol * cellW + shift - scrollFrac(scrollX, cellW);
	}

	/** Inverse of {@link #rightPocketViewX}: viewport column under view-X. */
	public static int rightPocketDestCol(final float viewX, final int holeCol1,
			final float blobLeft, final float blobRight, final float cellW, final float scrollX) {
		if (cellW <= 0f) {
			return holeCol1;
		}
		int shift = rightPocketShift(Math.round(blobLeft), Math.round(blobRight),
				Math.round(cellW));
		float frac = scrollFrac(scrollX, cellW);
		int col = (int) Math.floor((viewX - shift + frac) / cellW);
		if (col < holeCol1) {
			return holeCol1;
		}
		return col;
	}

	/** Pixel offset within the current cell; always in {@code [0, cellW)} for scrollX&gt;=0. */
	private static float scrollFrac(final float scrollX, final float cellW) {
		float frac = scrollX - (float) Math.floor(scrollX / cellW) * cellW;
		if (frac < 0f) {
			frac += cellW;
		}
		return frac;
	}

	private static int snapTake(final CharSequence text, final int src, final int take) {
		if (text == null || take <= 0 || src < 0) {
			return take;
		}
		int end = src + take;
		if (end > text.length()) {
			return Math.max(0, text.length() - src);
		}
		if (end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))
				&& Character.isLowSurrogate(text.charAt(end))) {
			return take + 1;
		}
		if (take >= 2 && Character.isHighSurrogate(text.charAt(end - 1))
				&& (end >= text.length() || !Character.isLowSurrogate(text.charAt(end)))) {
			return take - 1;
		}
		return take;
	}

	private static int lastSpaceTake(final CharSequence text, final int src, final int width) {
		if (text == null || src < 0 || width <= 0) {
			return -1;
		}
		int lim = src + width;
		if (lim > text.length()) {
			lim = text.length();
		}
		int lastSpace = -1;
		for (int i = src; i < lim; i++) {
			if (text.charAt(i) == ' ') {
				lastSpace = i;
			}
		}
		if (lastSpace > src) {
			return lastSpace - src + 1;
		}
		return -1;
	}

	private static int floorDiv(final int num, final int den) {
		return (int) Math.floor(num / (double) den);
	}

	private static int ceilDiv(final int num, final int den) {
		return (int) Math.ceil(num / (double) den);
	}
}
