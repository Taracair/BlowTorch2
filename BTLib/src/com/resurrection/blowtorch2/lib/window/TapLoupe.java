package com.resurrection.blowtorch2.lib.window;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Decide whether a long-press near tappable words should open the command
 * menu or a magnifier to pick one word.
 *
 * <p>No View or Canvas: the window supplies boxes and the finger, and this
 * returns which overlay to show. Boxes with the same commands that touch are
 * one word — a colour change mid-word is still one candidate.
 *
 * <p>A loupe is not only "finger circle covers two boxes". Trigger Tappable
 * Words are often a whole capture ({@code a rusty sword}) sitting one space
 * from the next; the circle then stays inside the first box. Same-line
 * neighbours within {@code radius} join the cluster. Two matches stacked in
 * the same column stay two words (padded hitboxes overlap) and also loupe.
 * OSC 8 / MXP short links already fall in the circle; this is the wide-word
 * and stacked-word case.
 */
public final class TapLoupe {

	public enum Kind {
		NONE,
		MENU,
		LOUPE
	}

	public static final class Target {
		public final int left;
		public final int top;
		public final int right;
		public final int bottom;
		public final String label;
		public final String[] commands;
		public final boolean tapSendsFirst;
		public final boolean launchHref;

		public Target(final int left, final int top, final int right,
				final int bottom, final String label, final String[] commands,
				final boolean tapSendsFirst, final boolean launchHref) {
			this.left = left;
			this.top = top;
			this.right = right;
			this.bottom = bottom;
			this.label = label != null ? label : "";
			this.commands = commands != null ? commands : new String[0];
			this.tapSendsFirst = tapSendsFirst;
			this.launchHref = launchHref;
		}

		boolean contains(final int x, final int y) {
			return x >= left && x < right && y >= top && y < bottom;
		}

		int width() {
			return Math.max(0, right - left);
		}

		int height() {
			return Math.max(0, bottom - top);
		}
	}

	public static final class Query {
		public final Kind kind;
		public final List<Target> candidates;
		public final Target selected;

		Query(final Kind kind, final List<Target> candidates,
				final Target selected) {
			this.kind = kind;
			this.candidates = candidates;
			this.selected = selected;
		}

		public static Query none() {
			return new Query(Kind.NONE, Collections.<Target>emptyList(), null);
		}
	}

	private TapLoupe() {
	}

	public static int radiusPx(final int lineHeightPx, final float density) {
		int fromLine = (int) (1.25f * lineHeightPx);
		int fromDp = (int) (28f * density);
		return Math.max(fromLine, fromDp);
	}

	/**
	 * Collapse boxes that are the same word split across runs (colour change,
	 * wrap on the same line). Different command lists stay separate even if
	 * the rects overlap. Two matches on consecutive lines stay two words —
	 * padded hitboxes overlap, but that is FlugHammer under FlugHammer, not
	 * a colour split.
	 */
	public static List<Target> merge(final List<Target> boxes,
			final int lineHeight) {
		ArrayList<Target> remaining = new ArrayList<Target>();
		if (boxes != null) {
			remaining.addAll(boxes);
		}
		ArrayList<Target> out = new ArrayList<Target>();
		while (!remaining.isEmpty()) {
			Target cur = remaining.remove(0);
			boolean grew = true;
			while (grew) {
				grew = false;
				for (int i = 0; i < remaining.size();) {
					Target other = remaining.get(i);
					if (sameWord(cur, other) && touches(cur, other, 1)
							&& sameRow(cur, other, lineHeight)) {
						cur = union(cur, other);
						remaining.remove(i);
						grew = true;
					} else {
						i++;
					}
				}
			}
			out.add(cur);
		}
		return out;
	}

	public static Query query(final List<Target> merged, final int x,
			final int y, final int radius, final int lineHeight) {
		Cluster c = clusterAt(merged, x, y, radius, lineHeight);
		if (c == null || c.selected == null) {
			return Query.none();
		}
		if (c.candidates.size() >= 2) {
			return new Query(Kind.LOUPE, c.candidates, c.selected);
		}
		if (c.selected.commands.length > 1) {
			return new Query(Kind.MENU, c.candidates, c.selected);
		}
		return Query.none();
	}

	/**
	 * Word under/near the finger after same-line clustering. Unlike
	 * {@link #query}, an isolated one-command word still returns that word —
	 * a slide off a loupe cluster onto {@code exit} must retarget, not keep
	 * the previous pick.
	 */
	public static Target underFinger(final List<Target> merged, final int x,
			final int y, final int radius, final int lineHeight) {
		Cluster c = clusterAt(merged, x, y, radius, lineHeight);
		return c == null ? null : c.selected;
	}

	private static final class Cluster {
		final List<Target> candidates;
		final Target selected;

		Cluster(final List<Target> candidates, final Target selected) {
			this.candidates = candidates;
			this.selected = selected;
		}
	}

	/**
	 * Seed is the word under the finger on the row whose centre is closest to
	 * the finger, not every box the circle overlaps and not the smallest
	 * containing box (a narrower word on the line below also contains the
	 * point after min-height padding).
	 */
	private static Cluster clusterAt(final List<Target> merged, final int x,
			final int y, final int radius, final int lineHeight) {
		List<Target> hits = inCircle(merged, x, y, radius);
		if (hits.isEmpty()) {
			return null;
		}
		Target seed = pickSeed(hits, x, y, lineHeight);
		if (seed == null) {
			return null;
		}
		// Every same-row circle hit is a seed, not only the closest word.
		// Two short links with a gap wider than radius still both sit in the
		// circle when the finger is in the gap; expanding from one of them
		// would drop the other.
		ArrayList<Target> rowSeeds = new ArrayList<Target>();
		for (int i = 0; i < hits.size(); i++) {
			if (sameRow(hits.get(i), seed, lineHeight)) {
				rowSeeds.add(hits.get(i));
			}
		}
		List<Target> cluster = expandSameLine(merged, rowSeeds, radius,
				lineHeight);
		// Same column, next line: padded boxes overlap, so a hold on
		// FlugHammer sits in two hitboxes. Do not grow that other row
		// sideways — only the stacked neighbour.
		if (merged != null && seed != null) {
			for (int i = 0; i < merged.size(); i++) {
				Target t = merged.get(i);
				if (containsRef(cluster, t)) {
					continue;
				}
				if (closeInColumn(seed, t, radius, lineHeight)) {
					cluster.add(t);
				}
			}
		}
		return new Cluster(cluster, pickSeed(cluster, x, y, lineHeight));
	}

	/**
	 * Words on the same line whose boxes are within {@code gap} of a seed.
	 * Same line is centre-Y within half {@code lineHeight}, not Y-overlap:
	 * production hitboxes of neighbouring rows overlap.
	 * Fragments that are the same word (colour split with a small gap) are
	 * unioned, not offered as two loupe picks.
	 */
	static List<Target> expandSameLine(final List<Target> merged,
			final List<Target> seeds, final int gap, final int lineHeight) {
		ArrayList<Target> cluster = new ArrayList<Target>();
		if (seeds != null) {
			cluster.addAll(seeds);
		}
		if (merged == null || cluster.isEmpty()) {
			return cluster;
		}
		boolean[] used = new boolean[merged.size()];
		for (int i = 0; i < merged.size(); i++) {
			if (containsRef(cluster, merged.get(i))) {
				used[i] = true;
			}
		}
		boolean grew = true;
		while (grew) {
			grew = false;
			for (int i = 0; i < merged.size(); i++) {
				if (used[i]) {
					continue;
				}
				Target t = merged.get(i);
				for (int j = 0; j < cluster.size(); j++) {
					if (!closeOnLine(cluster.get(j), t, gap, lineHeight)) {
						continue;
					}
					if (sameWord(cluster.get(j), t)) {
						cluster.set(j, union(cluster.get(j), t));
					} else {
						cluster.add(t);
					}
					used[i] = true;
					grew = true;
					break;
				}
			}
		}
		return collapseSameWords(cluster, gap, lineHeight);
	}

	/**
	 * Two colour-split fragments of one command list are one word, even when
	 * both already sat in the finger circle (so they never went through the
	 * union path above).
	 */
	static List<Target> collapseSameWords(final List<Target> cluster,
			final int gap, final int lineHeight) {
		ArrayList<Target> remaining = new ArrayList<Target>();
		if (cluster != null) {
			remaining.addAll(cluster);
		}
		ArrayList<Target> out = new ArrayList<Target>();
		while (!remaining.isEmpty()) {
			Target cur = remaining.remove(0);
			boolean grew = true;
			while (grew) {
				grew = false;
				for (int i = 0; i < remaining.size();) {
					Target other = remaining.get(i);
					if (sameWord(cur, other)
							&& closeOnLine(cur, other, gap, lineHeight)) {
						cur = union(cur, other);
						remaining.remove(i);
						grew = true;
					} else {
						i++;
					}
				}
			}
			out.add(cur);
		}
		return out;
	}

	static boolean closeOnLine(final Target a, final Target b, final int gap,
			final int lineHeight) {
		if (!sameRow(a, b, lineHeight)) {
			return false;
		}
		return a.left <= b.right + gap && b.left <= a.right + gap;
	}

	/**
	 * Directly above/below, overlapping in X. Adjacent rows' hitboxes overlap
	 * after min-height padding; that is two tappable words stacked, not one.
	 */
	static boolean closeInColumn(final Target a, final Target b, final int gap,
			final int lineHeight) {
		if (!overlapsX(a, b) || !adjacentRow(a, b, lineHeight)) {
			return false;
		}
		int gapY = 0;
		if (a.bottom < b.top) {
			gapY = b.top - a.bottom;
		} else if (b.bottom < a.top) {
			gapY = a.top - b.bottom;
		}
		return gapY <= gap;
	}

	static boolean overlapsX(final Target a, final Target b) {
		return a.left < b.right && b.left < a.right;
	}

	static boolean adjacentRow(final Target a, final Target b,
			final int lineHeight) {
		int pitch = lineHeight > 0 ? lineHeight
				: Math.max(1, Math.max(a.height(), b.height()));
		int slack = Math.max(1, pitch / 2);
		int dy = Math.abs(centerY(a) - centerY(b));
		return dy >= slack && dy < pitch + slack;
	}

	static boolean sameRow(final Target a, final Target b,
			final int lineHeight) {
		int pitch = lineHeight > 0 ? lineHeight
				: Math.max(1, Math.max(a.height(), b.height()));
		int slack = Math.max(1, pitch / 2);
		return Math.abs(centerY(a) - centerY(b)) < slack;
	}

	static int centerY(final Target t) {
		return (t.top + t.bottom) / 2;
	}

	/**
	 * Adjacent rows overlap, so the circle can contain a narrower word on the
	 * line below. {@link #pick} would take that smaller box and the wide
	 * capture on this line would lose its neighbour. Prefer the row whose
	 * centre is closer to the finger, then pick on that row.
	 */
	static Target pickSeed(final List<Target> hits, final int x, final int y,
			final int lineHeight) {
		if (hits == null || hits.isEmpty()) {
			return null;
		}
		Target nearestRow = hits.get(0);
		int bestDy = Math.abs(centerY(nearestRow) - y);
		for (int i = 1; i < hits.size(); i++) {
			int dy = Math.abs(centerY(hits.get(i)) - y);
			if (dy < bestDy) {
				bestDy = dy;
				nearestRow = hits.get(i);
			}
		}
		ArrayList<Target> onRow = new ArrayList<Target>();
		for (int i = 0; i < hits.size(); i++) {
			if (sameRow(hits.get(i), nearestRow, lineHeight)) {
				onRow.add(hits.get(i));
			}
		}
		return pick(onRow, x, y);
	}

	private static boolean containsRef(final List<Target> list, final Target t) {
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i) == t) {
				return true;
			}
		}
		return false;
	}

	public static List<Target> inCircle(final List<Target> merged, final int x,
			final int y, final int radius) {
		ArrayList<Target> hits = new ArrayList<Target>();
		if (merged == null || radius < 0) {
			return hits;
		}
		for (int i = 0; i < merged.size(); i++) {
			Target t = merged.get(i);
			if (circleHits(x, y, radius, t)) {
				hits.add(t);
			}
		}
		return hits;
	}

	public static Target pick(final List<Target> candidates, final int x,
			final int y) {
		if (candidates == null || candidates.isEmpty()) {
			return null;
		}
		Target best = candidates.get(0);
		long bestScore = score(best, x, y);
		for (int i = 1; i < candidates.size(); i++) {
			Target t = candidates.get(i);
			long s = score(t, x, y);
			if (s < bestScore) {
				bestScore = s;
				best = t;
			}
		}
		return best;
	}

	/**
	 * A loupe pick is still a hold. Several commands always open the menu,
	 * even when a tap would send the first one.
	 */
	public static boolean pickOpensMenu(final Target t) {
		return t != null && !t.launchHref && t.commands.length > 1;
	}

	static boolean sameWord(final Target a, final Target b) {
		return a.launchHref == b.launchHref
				&& a.tapSendsFirst == b.tapSendsFirst
				&& Arrays.equals(a.commands, b.commands);
	}

	static boolean touches(final Target a, final Target b, final int gap) {
		return a.left <= b.right + gap
				&& b.left <= a.right + gap
				&& a.top <= b.bottom + gap
				&& b.top <= a.bottom + gap;
	}

	static Target union(final Target a, final Target b) {
		String label;
		if (a.label.length() == 0) {
			label = b.label;
		} else if (b.label.length() == 0 || a.label.contains(b.label)) {
			label = a.label;
		} else if (b.label.contains(a.label)) {
			label = b.label;
		} else {
			label = a.label + b.label;
		}
		return new Target(
				Math.min(a.left, b.left),
				Math.min(a.top, b.top),
				Math.max(a.right, b.right),
				Math.max(a.bottom, b.bottom),
				label, a.commands, a.tapSendsFirst, a.launchHref);
	}

	static long score(final Target t, final int x, final int y) {
		if (t.contains(x, y)) {
			return t.width() * (long) t.height();
		}
		return (1L << 40) + distSqToRect(x, y, t);
	}

	static boolean circleHits(final int cx, final int cy, final int r,
			final Target t) {
		return distSqToRect(cx, cy, t) <= (long) r * r;
	}

	static long distSqToRect(final int x, final int y, final Target t) {
		int nx = x;
		if (x < t.left) {
			nx = t.left;
		} else if (t.right > t.left && x > t.right - 1) {
			nx = t.right - 1;
		} else if (x >= t.right) {
			nx = t.right;
		}
		int ny = y;
		if (y < t.top) {
			ny = t.top;
		} else if (t.bottom > t.top && y > t.bottom - 1) {
			ny = t.bottom - 1;
		} else if (y >= t.bottom) {
			ny = t.bottom;
		}
		long dx = x - nx;
		long dy = y - ny;
		return dx * dx + dy * dy;
	}
}
