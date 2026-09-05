package com.resurrection.blowtorch2.lib.trigger.style;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import com.resurrection.blowtorch2.lib.window.TextTree;
import com.resurrection.blowtorch2.lib.window.TextTree.Color;
import com.resurrection.blowtorch2.lib.window.TextTree.Line;
import com.resurrection.blowtorch2.lib.window.TextTree.Text;
import com.resurrection.blowtorch2.lib.window.TextTree.Unit;

/**
 * Per-code-point snapshots for one {@link Line}, plus maximal equal-recipe runs.
 * Skips {@code triggerPaint} colour units so our own Color actions do not
 * match as if the MUD sent them.
 */
public final class StyleLineModel {

	public static final class Run {
		public final int start;
		public final int end;
		public final StyleSnapshot snapshot;
		public final String text;

		Run(final int start, final int end, final StyleSnapshot snapshot,
				final String text) {
			this.start = start;
			this.end = end;
			this.snapshot = snapshot;
			this.text = text;
		}
	}

	public final String plain;
	public final StyleSnapshot[] byChar;
	public final List<Run> runs;

	private StyleLineModel(final String plain, final StyleSnapshot[] byChar,
			final List<Run> runs) {
		this.plain = plain;
		this.byChar = byChar;
		this.runs = runs;
	}

	public static StyleLineModel build(final Line line, final SgrRegisters registers) {
		StringBuilder plain = new StringBuilder();
		ArrayList<StyleSnapshot> chars = new ArrayList<StyleSnapshot>();
		if (line == null || registers == null) {
			return new StyleLineModel("", new StyleSnapshot[0], new ArrayList<Run>());
		}
		LinkedList<Unit> data = line.getData();
		if (data != null) {
			for (Unit u : data) {
				if (u instanceof Color) {
					Color c = (Color) u;
					if (c.isTriggerPaint()) {
						continue;
					}
					registers.beginColorUnit();
					registers.applyOps(c.getOperations());
				} else if (u instanceof Text) {
					Text t = (Text) u;
					registers.setHref(t.getHref());
					String s = t.getString();
					if (s == null || s.length() == 0) {
						continue;
					}
					StyleSnapshot snap = registers.snapshot();
					plain.append(s);
					int n = s.length();
					for (int i = 0; i < n; i++) {
						chars.add(snap);
					}
				} else if (u instanceof TextTree.NewLine
						|| u.type == TextTree.UNIT_TYPE.NEWLINE) {
					StyleSnapshot snap = registers.snapshot();
					plain.append('\n');
					chars.add(snap);
				} else if (u.type == TextTree.UNIT_TYPE.TAB) {
					StyleSnapshot snap = registers.snapshot();
					plain.append('\t');
					chars.add(snap);
				}
			}
		}
		StyleSnapshot[] byChar = chars.toArray(new StyleSnapshot[chars.size()]);
		ArrayList<Run> runs = new ArrayList<Run>();
		int i = 0;
		while (i < byChar.length) {
			if (plain.charAt(i) == '\n') {
				i++;
				continue;
			}
			StyleSnapshot snap = byChar[i];
			int j = i + 1;
			while (j < byChar.length && plain.charAt(j) != '\n'
					&& snap.sameRecipe(byChar[j])) {
				j++;
			}
			runs.add(new Run(i, j, snap, plain.substring(i, j)));
			i = j;
		}
		return new StyleLineModel(plain.toString(), byChar, runs);
	}

	/**
	 * Walk {@code tree} oldest-first so bleed carries the way the MUD sent it.
	 *
	 * @return one model per {@code getLines()} index (newest-first).
	 */
	public static StyleLineModel[] buildTree(final TextTree tree) {
		return buildTree(tree, SgrRegisters.defaults());
	}

	/**
	 * Same walk, starting from {@code seed} (connection-thread bleed). Mutates
	 * {@code seed} so the caller can keep the end state for the next chunk.
	 * Do not seed from {@code TextTree.getBleedColor()} — that list is the last
	 * CSI op-list, not accumulated SGR.
	 */
	public static StyleLineModel[] buildTree(final TextTree tree,
			final SgrRegisters seed) {
		if (tree == null || tree.getLines() == null || tree.getLines().isEmpty()) {
			return new StyleLineModel[0];
		}
		LinkedList<Line> lines = tree.getLines();
		StyleLineModel[] out = new StyleLineModel[lines.size()];
		SgrRegisters reg = seed != null ? seed : SgrRegisters.defaults();
		for (int i = lines.size() - 1; i >= 0; i--) {
			out[i] = build(lines.get(i), reg);
		}
		return out;
	}

	/** UTF-16 length ColorAction would walk (trailing newline units dropped). */
	public int matchLength() {
		int n = plain.length();
		while (n > 0 && plain.charAt(n - 1) == '\n') {
			n--;
		}
		return n;
	}

	public StyleSnapshot atColumn(final int col) {
		if (col < 0 || col >= byChar.length) {
			return null;
		}
		return byChar[col];
	}

	public Run runAt(final int col) {
		if (col < 0) {
			return null;
		}
		for (int i = 0; i < runs.size(); i++) {
			Run r = runs.get(i);
			if (col >= r.start && col < r.end) {
				return r;
			}
		}
		return null;
	}
}
