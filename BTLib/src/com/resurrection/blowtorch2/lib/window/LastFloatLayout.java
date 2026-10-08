package com.resurrection.blowtorch2.lib.window;

/**
 * Where the recent-command chips sit when the player has not dragged them.
 * A placed strip ignores this and keeps its own margins.
 */
public final class LastFloatLayout {

	private LastFloatLayout() {
	}

	/**
	 * Bottom margin with suggestions hidden is the input bar plus a gap.
	 * With the suggestion strip showing, the chips sit one gap above it.
	 */
	public static int unplacedBottom(final boolean suggestionVisible,
			final int suggestionBottom, final int suggestionHeight,
			final int fallbackHeight, final int barBottom, final int gap) {
		if (!suggestionVisible) {
			return barBottom + gap;
		}
		int height = suggestionHeight < 1 ? fallbackHeight : suggestionHeight;
		return suggestionBottom + height + gap;
	}

	/** Left margin follows the suggestion strip only while that strip is showing. */
	public static int unplacedLeft(final boolean suggestionVisible,
			final int suggestionLeft, final int defaultLeft) {
		return suggestionVisible ? suggestionLeft : defaultLeft;
	}

	public static int clamp(final int value, final int max) {
		int cap = max < 0 ? 0 : max;
		if (value < 0) {
			return 0;
		}
		if (value > cap) {
			return cap;
		}
		return value;
	}

	public static boolean near(final int left, final int bottom,
			final int defLeft, final int defBottom, final int snap) {
		return Math.abs(left - defLeft) <= snap
				&& Math.abs(bottom - defBottom) <= snap;
	}

	/** Fold a keyboard translation into the bottom margin. */
	public static int withLift(final int bottomMargin, final int liftPx) {
		return bottomMargin + liftPx;
	}

	/**
	 * Where a drop counts as "back on the automatic spot".
	 * A strip that follows the input bar is visually higher by the keyboard
	 * lift. One stacked on a suggestion strip the player already placed is not.
	 */
	public static int snapBottom(final int homeBottom, final boolean followsBar,
			final int barLiftPx) {
		return followsBar ? homeBottom + barLiftPx : homeBottom;
	}
}
