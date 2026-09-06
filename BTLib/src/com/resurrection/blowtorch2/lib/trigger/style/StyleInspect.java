package com.resurrection.blowtorch2.lib.trigger.style;

import java.util.List;

import com.resurrection.blowtorch2.lib.window.TextTree.Line;

/**
 * Map a broken-line index (0 = newest) plus a visual column onto a
 * {@link StyleLineModel} snapshot. The copy widget clamps the same index
 * space; an unclamped walk returns null for padding above short buffers.
 */
public final class StyleInspect {

	public static final class Hit {
		public final StyleSnapshot snap;
		public final String glyph;

		public Hit(final StyleSnapshot snap, final String glyph) {
			this.snap = snap;
			this.glyph = glyph == null ? "" : glyph;
		}
	}

	private StyleInspect() {
	}

	public static Hit at(final List<Line> lines, final StyleLineModel[] models,
			final int broken, final int visualCol, final int wrapColumns) {
		if (lines == null || lines.isEmpty() || models == null || models.length == 0) {
			return null;
		}
		final int n = Math.min(lines.size(), models.length);
		int totalRows = 0;
		for (int i = 0; i < n; i++) {
			totalRows += visualRows(lines.get(i));
		}
		if (totalRows <= 0) {
			return null;
		}
		int useBroken = broken;
		if (useBroken < 0) {
			useBroken = 0;
		} else if (useBroken >= totalRows) {
			useBroken = totalRows - 1;
		}
		int working = 0;
		int lineIdx = -1;
		int wrapRow = 0;
		for (int i = 0; i < n; i++) {
			int rows = visualRows(lines.get(i));
			if (useBroken >= working && useBroken < working + rows) {
				lineIdx = i;
				wrapRow = useBroken - working;
				break;
			}
			working += rows;
		}
		if (lineIdx < 0) {
			return null;
		}
		Hit hit = fromLine(models, lineIdx, wrapRow, visualCol, wrapColumns);
		if (hit != null) {
			return hit;
		}
		for (int d = 1; d < n; d++) {
			int newer = lineIdx - d;
			if (newer >= 0) {
				hit = fromLine(models, newer, 0, visualCol, wrapColumns);
				if (hit != null) {
					return hit;
				}
			}
			int older = lineIdx + d;
			if (older < n) {
				hit = fromLine(models, older, 0, visualCol, wrapColumns);
				if (hit != null) {
					return hit;
				}
			}
		}
		return null;
	}

	private static int visualRows(final Line line) {
		if (line == null) {
			return 1;
		}
		int breaks = line.getBreaks();
		return breaks > 0 ? breaks + 1 : 1;
	}

	private static Hit fromLine(final StyleLineModel[] models, final int lineIdx,
			final int wrapRow, final int visualCol, final int wrapColumns) {
		if (lineIdx < 0 || lineIdx >= models.length || models[lineIdx] == null) {
			return null;
		}
		StyleLineModel model = models[lineIdx];
		if (model.byChar.length == 0) {
			return null;
		}
		int logical = visualCol;
		if (wrapRow > 0 && wrapColumns > 0) {
			logical = wrapRow * wrapColumns + visualCol;
		}
		if (logical >= model.byChar.length) {
			logical = model.byChar.length - 1;
		}
		if (logical < 0) {
			logical = 0;
		}
		StyleSnapshot snap = model.atColumn(logical);
		if (snap == null) {
			return null;
		}
		StyleLineModel.Run run = model.runAt(logical);
		String glyph = run != null ? run.text : "";
		return new Hit(snap, glyph);
	}
}
