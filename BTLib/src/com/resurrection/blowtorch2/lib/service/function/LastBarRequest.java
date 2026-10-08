package com.resurrection.blowtorch2.lib.service.function;

import java.util.Locale;

/**
 * {@code .lastbar} and its arguments. Anything this does not recognise is
 * local usage, not a line for the world.
 */
public final class LastBarRequest {

	public static final int STATUS = 0;
	public static final int ON = 1;
	public static final int OFF = 2;
	public static final int COUNT = 3;
	public static final int LENGTH = 4;
	public static final int ORDER = 5;
	/** {@code .lastbar order} with no side: print the side, change nothing. */
	public static final int ORDER_STATUS = 6;
	public static final int MISUSE = 7;

	public static final int MIN_COUNT = 1;
	public static final int MAX_COUNT = 100;
	public static final int MIN_LENGTH = 3;
	public static final int MAX_LENGTH = 40;

	public final int kind;
	/** Count or length when {@link #COUNT} or {@link #LENGTH}. */
	public final int number;
	/** True when {@link #ORDER} is right. */
	public final boolean right;

	private LastBarRequest(final int kind, final int number, final boolean right) {
		this.kind = kind;
		this.number = number;
		this.right = right;
	}

	public static LastBarRequest parse(final String argument) {
		if (argument == null) {
			return bare(STATUS);
		}
		String s = argument.trim().toLowerCase(Locale.US);
		if (s.length() == 0) {
			return bare(STATUS);
		}
		if ("on".equals(s)) {
			return bare(ON);
		}
		if ("off".equals(s)) {
			return bare(OFF);
		}
		if ("order".equals(s)) {
			return bare(ORDER_STATUS);
		}
		if (headed(s, "order")) {
			String side = s.substring("order".length()).trim();
			if ("right".equals(side)) {
				return new LastBarRequest(ORDER, 0, true);
			}
			if ("left".equals(side)) {
				return new LastBarRequest(ORDER, 0, false);
			}
			return bare(MISUSE);
		}
		if ("length".equals(s)) {
			return bare(MISUSE);
		}
		if (headed(s, "length")) {
			String rest = s.substring("length".length()).trim();
			if (rest.indexOf(' ') >= 0 || rest.indexOf('\t') >= 0 || !digits(rest)) {
				return bare(MISUSE);
			}
			int n = parseInt(rest);
			if (n < MIN_LENGTH || n > MAX_LENGTH) {
				return bare(MISUSE);
			}
			return new LastBarRequest(LENGTH, n, false);
		}
		if (!digits(s)) {
			return bare(MISUSE);
		}
		int n = parseInt(s);
		if (n < MIN_COUNT || n > MAX_COUNT) {
			return bare(MISUSE);
		}
		return new LastBarRequest(COUNT, n, false);
	}

	private static boolean headed(final String s, final String head) {
		if (!s.startsWith(head) || s.length() == head.length()) {
			return false;
		}
		char c = s.charAt(head.length());
		return c == ' ' || c == '\t';
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

	private static LastBarRequest bare(final int kind) {
		return new LastBarRequest(kind, 0, false);
	}
}
