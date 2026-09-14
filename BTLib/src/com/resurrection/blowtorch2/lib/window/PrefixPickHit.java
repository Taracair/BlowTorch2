package com.resurrection.blowtorch2.lib.window;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Map a visual column on a (possibly wrapped) line onto an alnum token.
 * Prefer {@link #visualRows(TextTree.Line)} from the line's {@code Break}
 * units: word wrap ends a row at the last space, so the next row does not
 * start at {@code wrapRow * wrapColumns}.
 */
public final class PrefixPickHit {

	private PrefixPickHit() {
	}

	public static String wordOnLine(final String plain, final int wrapRow,
			final int visualCol, final int wrapColumns) {
		AlnumWordAt.Span span = spanOnLine(plain, wrapRow, visualCol, wrapColumns);
		return span == null ? null : span.text;
	}

	public static String wordOnRows(final String plain, final List<String> rows,
			final int wrapRow, final int visualCol) {
		AlnumWordAt.Span span = spanOnRows(plain, rows, wrapRow, visualCol);
		return span == null ? null : span.text;
	}

	/**
	 * Hard wrap: each visual row is {@code wrapColumns} cells. Tests and
	 * fallbacks. Drawing uses {@link #spanOnRows} from {@link #visualRows}.
	 */
	public static AlnumWordAt.Span spanOnLine(final String plain,
			final int wrapRow, final int visualCol, final int wrapColumns) {
		if (plain == null || plain.length() == 0) {
			return null;
		}
		if (wrapColumns <= 0) {
			return spanOnRows(plain, Collections.singletonList(plain), 0,
					visualCol);
		}
		return spanOnRows(plain, hardWrapRows(plain, wrapColumns), wrapRow,
				visualCol);
	}

	public static AlnumWordAt.Span spanOnRows(final String plain,
			final List<String> rows, final int wrapRow, final int visualCol) {
		if (plain == null || plain.length() == 0 || rows == null
				|| wrapRow < 0 || wrapRow >= rows.size()) {
			return null;
		}
		String row = rows.get(wrapRow);
		if (row.length() == 0) {
			return null;
		}
		int start = rowStart(rows, wrapRow);
		int col = visualCol;
		if (col < 0) {
			col = 0;
		} else if (col >= row.length()) {
			col = row.length() - 1;
		}
		int index = start + col;
		if (index >= plain.length()) {
			index = plain.length() - 1;
		}
		AlnumWordAt.Span span = AlnumWordAt.spanAt(plain, index);
		if (span == null && index > start && plain.charAt(index - 1) != '\n') {
			span = AlnumWordAt.spanAt(plain, index - 1);
		}
		return span;
	}

	public static int rowStart(final List<String> rows, final int wrapRow) {
		if (rows == null || wrapRow <= 0) {
			return 0;
		}
		int start = 0;
		int last = Math.min(wrapRow, rows.size());
		for (int i = 0; i < last; i++) {
			start += rows.get(i).length();
		}
		return start;
	}

	public static List<String> visualRows(final TextTree.Line line) {
		if (line == null || line.getData() == null) {
			return Collections.emptyList();
		}
		ArrayList<String> rows = new ArrayList<String>();
		StringBuilder sb = new StringBuilder();
		for (TextTree.Unit u : line.getData()) {
			if (u instanceof TextTree.Break) {
				rows.add(sb.toString());
				sb.setLength(0);
			} else if (u instanceof TextTree.Text) {
				String s = ((TextTree.Text) u).getString();
				if (s != null) {
					sb.append(s);
				}
			}
		}
		if (sb.length() > 0) {
			rows.add(sb.toString());
		} else if (rows.isEmpty()) {
			rows.add("");
		}
		return rows;
	}

	static List<String> hardWrapRows(final String plain, final int wrapColumns) {
		ArrayList<String> rows = new ArrayList<String>();
		int i = 0;
		while (i < plain.length()) {
			int end = Math.min(plain.length(), i + wrapColumns);
			rows.add(plain.substring(i, end));
			i = end;
		}
		return rows;
	}
}
