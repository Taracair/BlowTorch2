package com.resurrection.blowtorch2.lib.window;

/**
 * Armed state for prefix+screen-word. Prefix is read live at fire (bar or
 * button). Empty at arm is allowed: type {@code .pick}, then put {@code fix }
 * in the bar, then tap.
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
		kind = Kind.ONESHOT;
		return true;
	}

	public boolean armSticky(final String prefix) {
		kind = Kind.STICKY;
		return true;
	}

	public void disarm() {
		kind = Kind.IDLE;
	}

	/**
	 * Toggle sticky: second arm while sticky is off. Empty prefix still
	 * turns on — the live bar is checked at fire.
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
	 * only after a successful join. Empty / {@code .} prefix while sticky
	 * stays armed.
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
