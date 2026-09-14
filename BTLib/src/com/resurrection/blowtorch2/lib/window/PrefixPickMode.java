package com.resurrection.blowtorch2.lib.window;

/**
 * Armed state for prefix+screen-word. Prefix emptiness is checked at arm and
 * at fire against the live bar string; this object does not store the prefix.
 */
public final class PrefixPickMode {

	public enum Kind {
		IDLE,
		ONESHOT,
		STICKY
	}

	private Kind kind = Kind.IDLE;

	public Kind kind() {
		return kind;
	}

	public boolean isArmed() {
		return kind != Kind.IDLE;
	}

	public boolean armOneshot(final String prefix) {
		if (!hasPrefix(prefix)) {
			return false;
		}
		kind = Kind.ONESHOT;
		return true;
	}

	public boolean armSticky(final String prefix) {
		if (!hasPrefix(prefix)) {
			return false;
		}
		kind = Kind.STICKY;
		return true;
	}

	private static boolean hasPrefix(final String prefix) {
		return prefix != null && prefix.trim().length() > 0;
	}

	public void disarm() {
		kind = Kind.IDLE;
	}

	/**
	 * Toggle sticky: second arm while sticky is off. Empty prefix refuses
	 * turning on and leaves the current kind unchanged.
	 */
	public boolean toggleSticky(final String prefix) {
		if (kind == Kind.STICKY) {
			disarm();
			return false;
		}
		return armSticky(prefix);
	}

	/**
	 * Join prefix+word to send, or null to refuse. Oneshot returns to idle
	 * only after a successful join. Empty prefix while sticky stays armed.
	 */
	public String fire(final String prefix, final String word) {
		if (kind == Kind.IDLE) {
			return null;
		}
		String line = PrefixWordJoin.sendLine(prefix, word);
		if (line == null) {
			return null;
		}
		if (kind == Kind.ONESHOT) {
			kind = Kind.IDLE;
		}
		return line;
	}
}
