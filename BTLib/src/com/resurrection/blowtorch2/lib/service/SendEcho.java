package com.resurrection.blowtorch2.lib.service;

import com.resurrection.blowtorch2.lib.alias.AliasLocalEcho;

/**
 * Echo mode for one outbound send. {@link #INHERIT} is the input bar.
 * A shortcut uses {@link #SHOW} or {@link #HIDE}. Telnet password masking
 * still wins inside {@link AliasLocalEcho#shouldDisplay}.
 */
public final class SendEcho {

	/** Follow Options → Connection → Local Echo?. */
	public static final int INHERIT = 0;
	/** Do not print this send. */
	public static final int HIDE = 1;
	/** Print this send even when Local Echo? is off. */
	public static final int SHOW = 2;

	private SendEcho() {
	}

	/** {@code MESSAGE_SENDDATA_BYTES} arg1. Anything else follows the option. */
	public static int mode(final int arg1) {
		if (arg1 == HIDE || arg1 == SHOW) {
			return arg1;
		}
		return INHERIT;
	}

	public static AliasLocalEcho policy(final int mode) {
		if (mode == SHOW) {
			return AliasLocalEcho.FORCE_ON;
		}
		if (mode == HIDE) {
			return AliasLocalEcho.FORCE_OFF;
		}
		return AliasLocalEcho.INHERIT;
	}

	/** A segment that inherits follows this send. An alias choice stays. */
	public static AliasLocalEcho shown(final AliasLocalEcho segment, final int mode) {
		if (segment == null || segment == AliasLocalEcho.INHERIT) {
			return policy(mode);
		}
		return segment;
	}

	/** Unchecked local echo hides the finished line, including a force-on alias. */
	public static boolean hideLine(final int mode) {
		return mode == HIDE;
	}
}
