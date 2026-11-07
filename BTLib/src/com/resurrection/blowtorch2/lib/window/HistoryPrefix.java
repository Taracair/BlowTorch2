package com.resurrection.blowtorch2.lib.window;

import java.util.List;

/**
 * Up and Down on a written line. {@code newestFirst} is the history list.
 * {@code current} is -1 while the bar still shows what was typed.
 */
public final class HistoryPrefix {

	private HistoryPrefix() {
	}

	public static final class Step {
		public final int index;
		/** Null when the bar should stay as it is. */
		public final String line;

		private Step(final int index, final String line) {
			this.index = index;
			this.line = line;
		}
	}

	public static Step step(final List<String> newestFirst, final String prefix,
			final int current, final boolean older) {
		if (newestFirst == null || prefix == null || prefix.length() == 0) {
			return new Step(current, null);
		}
		int i = older ? current + 1 : current - 1;
		if (older && current < 0) {
			i = 0;
		}
		int dir = older ? 1 : -1;
		while (i >= 0 && i < newestFirst.size()) {
			String line = newestFirst.get(i);
			if (line != null && line.startsWith(prefix)) {
				if (!(current < 0 && line.equals(prefix))) {
					return new Step(i, line);
				}
			}
			i += dir;
		}
		if (!older && current >= 0) {
			return new Step(-1, prefix);
		}
		return new Step(current, null);
	}

	/** A mark-only rewrite keeps the walk. Any other change of the stripped line ends it. */
	public static boolean ended(final String prefix, final String lastShown, final String now) {
		if (prefix == null || prefix.length() == 0 || lastShown == null) {
			return false;
		}
		String current = now == null ? "" : now;
		return !current.equals(lastShown);
	}
}
