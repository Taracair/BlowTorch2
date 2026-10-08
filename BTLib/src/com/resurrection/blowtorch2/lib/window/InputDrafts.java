package com.resurrection.blowtorch2.lib.window;

import java.util.HashMap;
import java.util.Map;

/**
 * Unsent input for each open world. The live bar belongs to
 * {@link #showing()}, pinned while that world is on screen. A later
 * name does not retarget it.
 */
public final class InputDrafts {

	/** Text and selection as the input widget holds them. */
	public static final class Draft {
		public static final Draft EMPTY = new Draft("", 0, 0);

		private final String text;
		private final int selectionStart;
		private final int selectionEnd;

		Draft(final String text, final int selectionStart, final int selectionEnd) {
			this.text = text;
			this.selectionStart = selectionStart;
			this.selectionEnd = selectionEnd;
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
	}

	private final Map<String, Draft> parked = new HashMap<String, Draft>();
	private String showing;

	/** World the live bar belongs to, or null until the first pin. */
	public String showing() {
		return showing;
	}

	/**
	 * Remember {@code display} as the world on screen, if none is set yet.
	 * Call this before the activity intent is replaced.
	 */
	public void pinShowing(final String display) {
		if (showing != null) {
			return;
		}
		if (display == null || display.length() == 0) {
			return;
		}
		showing = display;
	}

	/**
	 * Park the live bar under {@link #showing()} and return what the bar
	 * should show for {@code next}.
	 *
	 * @return null when the bar must be left alone (blank {@code next}, or
	 *         already showing that world). {@link Draft#EMPTY} when
	 *         {@code next} has nothing parked.
	 */
	public Draft switchTo(final String next, final String liveText,
			final int selStart, final int selEnd) {
		if (next == null || next.length() == 0) {
			return null;
		}
		if (showing == null) {
			showing = next;
			return null;
		}
		if (next.equals(showing)) {
			return null;
		}
		String text = liveText == null ? "" : liveText;
		int length = text.length();
		int start = clamp(selStart, length);
		int end = clamp(selEnd, length);
		if (text.length() == 0) {
			parked.remove(showing);
		} else {
			parked.put(showing, new Draft(text, start, end));
		}
		showing = next;
		Draft found = parked.remove(next);
		return found == null ? Draft.EMPTY : found;
	}

	/** Forget a world that has closed. The live bar is untouched. */
	public void drop(final String display) {
		if (display == null || display.length() == 0) {
			return;
		}
		parked.remove(display);
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
