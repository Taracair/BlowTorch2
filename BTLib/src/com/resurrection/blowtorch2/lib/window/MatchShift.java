package com.resurrection.blowtorch2.lib.window;

import java.util.ArrayList;

/**
 * Replace length-deltas on one line, in that line's original coordinates.
 *
 * <p>A single running total slides every later match by the full delta. That
 * is right only when every earlier replace ended at or before the later
 * match. A replace inside the match has to grow the end and leave the start
 * where it was: sliding the start paints the original match in the middle of
 * the line and drops the replacement.
 *
 * <p>{@code pstart} / {@code pend} are the arguments {@code doResponse}
 * already receives ({@code pend} is {@code matcher.end() - 2} relative to
 * the line). Callers do not adjust them again.
 */
public final class MatchShift {

	private static final class Edit {
		final int origStart;
		final int origExclusiveEnd;
		final int delta;

		Edit(int origStart, int origExclusiveEnd, int delta) {
			this.origStart = origStart;
			this.origExclusiveEnd = origExclusiveEnd;
			this.delta = delta;
		}
	}

	private final ArrayList<Edit> edits = new ArrayList<Edit>();

	public int currentStart(int pstart) {
		return pstart + deltaEndingAtOrBefore(pstart);
	}

	/** Inclusive end in the current line. */
	public int currentInclusiveEnd(int pend) {
		int origInclusive = pend + 1;
		return origInclusive + deltaStartingAtOrBefore(origInclusive);
	}

	/**
	 * Record a replace that already ran. {@code delta} is new length minus
	 * old length. Zero does not move anything later, so it is not stored.
	 */
	public void addFromResponder(int pstart, int pend, int delta) {
		if (delta == 0) {
			return;
		}
		edits.add(new Edit(pstart, pend + 2, delta));
	}

	public int totalDelta() {
		int d = 0;
		for (int i = 0; i < edits.size(); i++) {
			d += edits.get(i).delta;
		}
		return d;
	}

	private int deltaEndingAtOrBefore(int origIndex) {
		int d = 0;
		for (int i = 0; i < edits.size(); i++) {
			Edit e = edits.get(i);
			if (e.origExclusiveEnd <= origIndex) {
				d += e.delta;
			}
		}
		return d;
	}

	private int deltaStartingAtOrBefore(int origInclusiveIndex) {
		int d = 0;
		for (int i = 0; i < edits.size(); i++) {
			Edit e = edits.get(i);
			if (e.origStart <= origInclusiveIndex) {
				d += e.delta;
			}
		}
		return d;
	}
}
