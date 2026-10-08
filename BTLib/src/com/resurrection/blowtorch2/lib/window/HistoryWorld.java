package com.resurrection.blowtorch2.lib.window;

/**
 * Which world's command list the live {@link CommandKeeper} is holding.
 * Pin before the activity intent is replaced, the same way {@link InputDrafts}
 * pins the input bar.
 */
public final class HistoryWorld {

	private String showing;

	/** World the live list belongs to, or null until the first pin. */
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
	 * @return the world to save before loading {@code next}, or null when
	 *         the live list must stay as it is
	 */
	public String leaveFor(final String next) {
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
		String left = showing;
		showing = next;
		return left;
	}

	/** The live list now belongs to {@code next}, even if a name was already pinned. */
	public void adopt(final String next) {
		if (next == null || next.length() == 0) {
			return;
		}
		showing = next;
	}

	/**
	 * World to write before loading {@code next}, or null when the live list
	 * already belongs to {@code next}. {@code loadedFor} is the name last
	 * passed to load, not whichever display the intent currently carries.
	 */
	public static String parkedName(final String loadedFor, final String next) {
		if (next == null || next.length() == 0) {
			return null;
		}
		if (loadedFor == null || loadedFor.equals(next)) {
			return null;
		}
		return loadedFor;
	}
}
