package com.resurrection.blowtorch2.lib.responder.setvariable;

import java.util.Locale;

import com.resurrection.blowtorch2.lib.trigger.condition.SessionVariableStore;

/**
 * How a Set Variable action changes the session map.
 *
 * <p>Modes are applied on the connection handler, not in
 * {@code doResponse}: triggers and timers do not share a thread. {@code $1} is
 * already a capture by the time a value arrives here.
 */
public final class SetVariableApply {

	public static final String MODE_SET = "set";
	public static final String MODE_ADD = "add";
	public static final String MODE_SUBTRACT = "subtract";
	public static final String MODE_APPEND = "append";
	public static final String MODE_UNSET = "unset";

	public static final String[] MODE_TOKENS = {
			MODE_SET, MODE_ADD, MODE_SUBTRACT, MODE_APPEND, MODE_UNSET
	};

	public static final String[] MODE_LABELS = {
			"Set", "Add number", "Subtract number", "Append", "Unset"
	};

	private SetVariableApply() {
	}

	/**
	 * Missing, empty, or unknown {@code mode} is today's replace.
	 */
	public static String normalizeMode(String raw) {
		if (raw == null) {
			return MODE_SET;
		}
		String s = raw.trim().toLowerCase(Locale.US);
		if (MODE_ADD.equals(s) || MODE_SUBTRACT.equals(s) || MODE_APPEND.equals(s)
				|| MODE_UNSET.equals(s) || MODE_SET.equals(s)) {
			return s;
		}
		return MODE_SET;
	}

	/** Same tokens as chat {@code mine}: {@code true} / {@code 1}. */
	public static boolean parsePersist(String raw) {
		return "true".equalsIgnoreCase(raw) || "1".equals(raw);
	}

	public static int indexOfMode(String mode) {
		String token = normalizeMode(mode);
		for (int i = 0; i < MODE_TOKENS.length; i++) {
			if (MODE_TOKENS[i].equals(token)) {
				return i;
			}
		}
		return 0;
	}

	/**
	 * @return the value now stored, or {@code null} when the name was removed
	 *         or the key was empty
	 */
	public static String applyToStore(SessionVariableStore store, String key,
			String value, String mode) {
		if (store == null || key == null || key.length() == 0) {
			return null;
		}
		String m = normalizeMode(mode);
		String operand = value != null ? value : "";
		if (MODE_UNSET.equals(m)) {
			store.unset(key);
			return null;
		}
		if (MODE_APPEND.equals(m)) {
			String current = store.get(key);
			if (current == null) {
				current = "";
			}
			String next = current + operand;
			store.set(key, next);
			return next;
		}
		if (MODE_ADD.equals(m) || MODE_SUBTRACT.equals(m)) {
			long cur = parseLongOrZero(store.get(key));
			long op = parseLongOrZero(operand);
			long next = MODE_ADD.equals(m) ? cur + op : cur - op;
			String s = Long.toString(next);
			store.set(key, s);
			return s;
		}
		store.set(key, operand);
		return operand;
	}

	static long parseLongOrZero(String raw) {
		if (raw == null) {
			return 0L;
		}
		String t = raw.trim();
		if (t.length() == 0) {
			return 0L;
		}
		try {
			return Long.parseLong(t);
		} catch (NumberFormatException e) {
			return 0L;
		}
	}
}
