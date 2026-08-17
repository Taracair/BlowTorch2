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
	 * wrap). Different command lists stay separate even if the rects overlap.
	 */
	public static List<Target> merge(final List<Target> boxes) {
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
					if (sameWord(cur, other) && touches(cur, other, 1)) {
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
			final int y, final int radius) {
		List<Target> hits = inCircle(merged, x, y, radius);
		if (hits.isEmpty()) {
			return Query.none();
		}
		Target selected = pick(hits, x, y);
		if (hits.size() >= 2) {
			return new Query(Kind.LOUPE, hits, selected);
		}
		if (selected != null && selected.commands.length > 1) {
			return new Query(Kind.MENU, hits, selected);
		}
		return Query.none();
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
