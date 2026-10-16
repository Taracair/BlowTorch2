package com.resurrection.blowtorch2.lib.trigger;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Order for hits already found: earlier line, then lower list index, then
 * smaller start. The line is the number of newlines before {@code start}.
 */
public final class TriggerFireOrder {

	/** List index and match start. Matched text is not an input. */
	public static final class Placed {
		public final int entryIndex;
		public final int start;

		public Placed(final int entryIndex, final int start) {
			this.entryIndex = entryIndex;
			this.start = start;
		}
	}

	private TriggerFireOrder() {
	}

	/** Number of {@code '\\n'} in {@code source} before {@code start}. */
	public static int lineOf(final CharSequence source, final int start) {
		if (source == null || source.length() == 0 || start <= 0) {
			return 0;
		}
		int n = 0;
		int lim = start;
		if (lim > source.length()) {
			lim = source.length();
		}
		for (int i = 0; i < lim; i++) {
			if (source.charAt(i) == '\n') {
				n++;
			}
		}
		return n;
	}

	/**
	 * Negative when {@code (entryIndexA, startA)} fires before
	 * {@code (entryIndexB, startB)}.
	 */
	public static int compare(final CharSequence source,
			final int entryIndexA, final int startA,
			final int entryIndexB, final int startB) {
		int lineA = lineOf(source, startA);
		int lineB = lineOf(source, startB);
		if (lineA != lineB) {
			return lineA < lineB ? -1 : 1;
		}
		if (entryIndexA != entryIndexB) {
			return entryIndexA < entryIndexB ? -1 : 1;
		}
		if (startA != startB) {
			return startA < startB ? -1 : 1;
		}
		return 0;
	}

	public static void sort(final CharSequence source, final List<Placed> hits) {
		if (hits == null || hits.size() < 2) {
			return;
		}
		Collections.sort(hits, new Comparator<Placed>() {
			@Override
			public int compare(final Placed a, final Placed b) {
				return TriggerFireOrder.compare(source, a.entryIndex, a.start,
						b.entryIndex, b.start);
			}
		});
	}
}
