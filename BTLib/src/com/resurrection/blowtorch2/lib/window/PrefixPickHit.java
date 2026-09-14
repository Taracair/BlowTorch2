package com.resurrection.blowtorch2.lib.window;

/**
 * Map a visual column on a (possibly wrapped) plain line onto an alnum token.
 * When wrapping, the column stays on this wrap row.
 */
public final class PrefixPickHit {

	private PrefixPickHit() {
	}

	public static String wordOnLine(final String plain, final int wrapRow,
			final int visualCol, final int wrapColumns) {
		AlnumWordAt.Span span = spanOnLine(plain, wrapRow, visualCol, wrapColumns);
		return span == null ? null : span.text;
	}

	public static AlnumWordAt.Span spanOnLine(final String plain,
			final int wrapRow, final int visualCol, final int wrapColumns) {
		if (plain == null || plain.length() == 0) {
			return null;
		}
		int rowStart;
		int index;
		if (wrapColumns <= 0) {
			rowStart = 0;
			index = visualCol;
			if (index < 0) {
				index = 0;
			} else if (index >= plain.length()) {
				index = plain.length() - 1;
			}
		} else {
			if (wrapRow < 0) {
				return null;
			}
			long rowStartLong = (long) wrapRow * (long) wrapColumns;
			if (rowStartLong >= plain.length()) {
				return null;
			}
			rowStart = (int) rowStartLong;
			int rowEnd = Math.min(plain.length(), rowStart + wrapColumns);
			int col = visualCol;
			if (col < 0) {
				col = 0;
			} else if (col >= wrapColumns) {
				col = wrapColumns - 1;
			}
			index = rowStart + col;
			if (index >= rowEnd) {
				index = rowEnd - 1;
			}
		}
		AlnumWordAt.Span span = AlnumWordAt.spanAt(plain, index);
		if (span == null && canFallback(plain, index, rowStart)) {
			span = AlnumWordAt.spanAt(plain, index - 1);
		}
		return span;
	}

	/** Gap after a word on this wrap row, not the previous row or line. */
	private static boolean canFallback(final String plain, final int index,
			final int rowStart) {
		if (index <= rowStart) {
			return false;
		}
		return plain.charAt(index - 1) != '\n';
	}
}
