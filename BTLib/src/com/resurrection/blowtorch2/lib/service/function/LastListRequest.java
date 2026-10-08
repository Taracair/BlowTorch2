package com.resurrection.blowtorch2.lib.service.function;

import java.util.Locale;

import com.resurrection.blowtorch2.lib.window.LastBarChips;

/**
 * {@code .lastlist} opens the window. {@code list} is that window,
 * {@code float} is chips over the game, {@code float off} hides them,
 * {@code float N} is how many chips, {@code float length N} is how many
 * characters of each. {@code bar} is the recent-command bar. {@code frame}
 * toggles the title, gear, and close. Anything else with that name is
 * local usage, not a line for the world.
 */
public final class LastListRequest {

	public static final int SHOW = 0;
	public static final int FRAME_ON = 1;
	public static final int FRAME_OFF = 2;
	public static final int MISUSE = 3;
	public static final int FRAME_TOGGLE = 4;
	public static final int FLOAT = 5;
	public static final int BAR = 6;
	public static final int FLOAT_OFF = 7;
	public static final int FLOAT_TOGGLE = 8;
	public static final int FLOAT_COUNT = 9;
	public static final int FLOAT_LENGTH = 10;

	/** Kind plus the count or length when those are what was asked. */
	public static final class Parsed {
		public final int kind;
		public final int number;

		Parsed(final int kind, final int number) {
			this.kind = kind;
			this.number = number;
		}
	}

	private LastListRequest() {
	}

	/**
	 * {@code .lastfloat} is {@code .lastlist float}. A word after it is
	 * {@code float} plus that word, so {@code .lastfloat off} hides the chips.
	 */
	public static String normalize(final String commandName, final String argument) {
		if (!"lastfloat".equals(commandName)) {
			return argument;
		}
		if (argument == null || argument.trim().length() == 0) {
			return "float";
		}
		return "float " + argument.trim();
	}

	public static int parse(final String argument) {
		return read(argument).kind;
	}

	public static Parsed read(final String argument) {
		if (argument == null) {
			return of(SHOW);
		}
		String s = argument.trim();
		if (s.length() == 0) {
			return of(SHOW);
		}
		String[] parts = s.split("\\s+");
		String word = parts[0].toLowerCase(Locale.US);
		if (word.equals("float") || word.equals("floating")) {
			return readFloat(parts);
		}
		if (parts.length == 1) {
			if (word.equals("list")) {
				return of(SHOW);
			}
			if (word.equals("bar")) {
				return of(BAR);
			}
		}
		if (parts.length == 0 || !parts[0].equalsIgnoreCase("frame")) {
			return of(MISUSE);
		}
		if (parts.length == 1) {
			return of(FRAME_TOGGLE);
		}
		if (parts.length != 2) {
			return of(MISUSE);
		}
		String which = parts[1].toLowerCase(Locale.US);
		if (which.equals("on")) {
			return of(FRAME_ON);
		}
		if (which.equals("off")) {
			return of(FRAME_OFF);
		}
		return of(MISUSE);
	}

	private static Parsed readFloat(final String[] parts) {
		if (parts.length == 1) {
			return of(FLOAT);
		}
		if (parts.length == 2) {
			String which = parts[1].toLowerCase(Locale.US);
			if (which.equals("on")) {
				return of(FLOAT);
			}
			if (which.equals("off")) {
				return of(FLOAT_OFF);
			}
			if (which.equals("toggle")) {
				return of(FLOAT_TOGGLE);
			}
			if (digits(which)) {
				int n = parseInt(which);
				if (n >= LastBarChips.MIN_COUNT && n <= LastBarChips.MAX_COUNT) {
					return of(FLOAT_COUNT, n);
				}
			}
			return of(MISUSE);
		}
		if (parts.length == 3 && parts[1].equalsIgnoreCase("length") && digits(parts[2])) {
			int n = parseInt(parts[2]);
			if (n >= LastBarChips.MIN_LENGTH && n <= LastBarChips.MAX_LENGTH) {
				return of(FLOAT_LENGTH, n);
			}
		}
		return of(MISUSE);
	}

	private static boolean digits(final String s) {
		if (s.length() == 0) {
			return false;
		}
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c < '0' || c > '9') {
				return false;
			}
		}
		return true;
	}

	private static int parseInt(final String s) {
		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	private static Parsed of(final int kind) {
		return new Parsed(kind, 0);
	}

	private static Parsed of(final int kind, final int number) {
		return new Parsed(kind, number);
	}
}
