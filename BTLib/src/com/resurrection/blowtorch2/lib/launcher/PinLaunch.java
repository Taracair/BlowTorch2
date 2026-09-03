package com.resurrection.blowtorch2.lib.launcher;

/**
 * What a home-screen pin (or list tap that reuses {@code MainWindow}) should
 * do to the live session. Android-free so the table can be tested on the JVM.
 *
 * <p>{@code pinAlreadyOpen} is "this display is a key in the connection map",
 * not "the socket is up".
 */
public final class PinLaunch {

	public enum Action {
		NONE,
		RESUME,
		SWITCH_EXISTING,
		OPEN_NEW
	}

	private PinLaunch() {
	}

	public static Action decide(final String pinDisplay, final String clutchDisplay,
			final boolean pinAlreadyOpen) {
		if (pinDisplay == null || pinDisplay.length() == 0) {
			return Action.NONE;
		}
		final String clutch = clutchDisplay == null ? "" : clutchDisplay;
		if (pinAlreadyOpen) {
			if (pinDisplay.equals(clutch)) {
				return Action.RESUME;
			}
			return Action.SWITCH_EXISTING;
		}
		return Action.OPEN_NEW;
	}
}
