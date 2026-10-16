package com.resurrection.blowtorch2.lib.trigger;

import java.util.HashMap;

import com.resurrection.blowtorch2.lib.window.MatchShift;

/**
 * {@link MatchShift} for each original line in one dispatch. The shift is
 * stored per line so one line's inserts are not reused on another line.
 */
public final class LineModCount {

	private final HashMap<Integer, MatchShift> byLine = new HashMap<Integer, MatchShift>();

	/** A fresh empty shift when this line has not been stored yet. */
	public MatchShift load(final int originalLine) {
		MatchShift v = byLine.get(Integer.valueOf(originalLine));
		return v == null ? new MatchShift() : v;
	}

	public void store(final int originalLine, final MatchShift shift) {
		byLine.put(Integer.valueOf(originalLine),
				shift == null ? new MatchShift() : shift);
	}

	public void drop(final int originalLine) {
		byLine.remove(Integer.valueOf(originalLine));
	}

	public void clear() {
		byLine.clear();
	}
}
