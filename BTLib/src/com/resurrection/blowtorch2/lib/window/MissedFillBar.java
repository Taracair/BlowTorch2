package com.resurrection.blowtorch2.lib.window;

/**
 * A missing {@code .last input} slot puts the input bar back to the text and
 * selection from before Send. The send reset has already cleared the bar
 * (Keep Last off) or selected the recall line (Keep Last on).
 */
public final class MissedFillBar {

	public static final class State {
		private final String text;
		private final int selectionStart;
		private final int selectionEnd;
		private final int keepLastReplaceLength;
		private final boolean historyWidgetKept;

		public State(final String text, final int selectionStart, final int selectionEnd,
				final int keepLastReplaceLength, final boolean historyWidgetKept) {
			this.text = text == null ? "" : text;
			this.selectionStart = selectionStart;
			this.selectionEnd = selectionEnd;
			this.keepLastReplaceLength = keepLastReplaceLength;
			this.historyWidgetKept = historyWidgetKept;
		}

		public String text() {
			return text;
		}

		public int selectionStart() {
			return selectionStart;
		}

		public int selectionEnd() {
			return selectionEnd;
		}

		public int keepLastReplaceLength() {
			return keepLastReplaceLength;
		}

		public boolean historyWidgetKept() {
			return historyWidgetKept;
		}
	}

	private MissedFillBar() {
	}

	/** The bar the send reset left is not used. */
	public static State afterMissingSlot(final State beforeSend) {
		String text = beforeSend.text();
		int len = text.length();
		int keep = beforeSend.keepLastReplaceLength();
		if (keep < 0) {
			keep = 0;
		} else if (keep > len) {
			keep = len;
		}
		boolean kept = keep > 0 && beforeSend.historyWidgetKept();
		return new State(text, clamp(beforeSend.selectionStart(), len),
				clamp(beforeSend.selectionEnd(), len), keep, kept);
	}

	private static int clamp(final int index, final int length) {
		if (index < 0) {
			return 0;
		}
		if (index > length) {
			return length;
		}
		return index;
	}
}
