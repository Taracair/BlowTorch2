package com.resurrection.blowtorch2.lib.window;

/**
 * Which recent-command chip sits where, and how much of the command is shown.
 * Index 1 is what {@code .last} sends.
 */
public final class LastBarChips {

	public static final int DEFAULT_COUNT = 5;
	public static final int MIN_COUNT = 1;
	public static final int MAX_COUNT = 100;
	public static final int DEFAULT_LENGTH = 20;
	public static final int MIN_LENGTH = 3;
	public static final int MAX_LENGTH = 40;

	private LastBarChips() {
	}

	/**
	 * 1-based numbers, left to right.
	 * {@code newestOnRight} puts 1 in the last slot.
	 */
	public static int[] numbersLeftToRight(final int shown, final boolean newestOnRight) {
		int n = shown < 0 ? 0 : shown;
		int[] out = new int[n];
		for (int i = 0; i < n; i++) {
			out[i] = newestOnRight ? (n - i) : (i + 1);
		}
		return out;
	}

	/** Same left-to-right slots for suggestion chips. Logical 0 is the first suggestion. */
	public static int[] logicalLeftToRight(final int count, final boolean firstOnRight) {
		int n = count < 0 ? 0 : count;
		int[] out = new int[n];
		for (int i = 0; i < n; i++) {
			out[i] = firstOnRight ? (n - 1 - i) : i;
		}
		return out;
	}

	public static String clip(final String command, final int length) {
		if (command == null || length < 1) {
			return "";
		}
		if (command.length() <= length) {
			return command;
		}
		return command.substring(0, length);
	}

	public static String label(final int number, final String command, final int length) {
		return number + " " + clip(command, length);
	}

	/** SharedPreferences name. Same folding as {@code LASTLIST_}. */
	public static String prefsName(final String world) {
		String raw = world == null ? "" : world;
		return "LASTBAR_" + raw.replaceAll("[^A-Za-z0-9._-]+", "_");
	}

	public static int clampCount(final int n) {
		if (n < MIN_COUNT || n > MAX_COUNT) {
			return DEFAULT_COUNT;
		}
		return n;
	}

	public static int clampLength(final int n) {
		if (n < MIN_LENGTH || n > MAX_LENGTH) {
			return DEFAULT_LENGTH;
		}
		return n;
	}
}
