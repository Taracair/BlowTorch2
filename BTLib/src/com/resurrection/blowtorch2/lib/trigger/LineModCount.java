package com.resurrection.blowtorch2.lib.trigger;

import java.util.HashMap;

/**
 * Length deltas from {@code ReplaceResponder} on one dispatch, keyed by the
 * original line index in the chunk.
 *
 * <p>{@code TextTree.modCount} is one integer on the tree. The cascade walks
 * one trigger through every line, then the next trigger, so a single
 * "reset when the line changes" flag zeroes line 0's delta when line 1
 * replaces, and {@code says} on line 0 becomes {@code sasays} again.
 */
public final class LineModCount {

	private final HashMap<Integer, Integer> byLine = new HashMap<Integer, Integer>();

	/** {@code 0} when this line has not been replaced yet in this chunk. */
	public int load(final int originalLine) {
		Integer v = byLine.get(Integer.valueOf(originalLine));
		return v == null ? 0 : v.intValue();
	}

	public void store(final int originalLine, final int modCount) {
		byLine.put(Integer.valueOf(originalLine), Integer.valueOf(modCount));
	}

	public void drop(final int originalLine) {
		byLine.remove(Integer.valueOf(originalLine));
	}

	public void clear() {
		byLine.clear();
	}
}
