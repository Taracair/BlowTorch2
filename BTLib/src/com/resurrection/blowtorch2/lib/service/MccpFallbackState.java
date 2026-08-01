/*
 * Copyright (C) Dan Block 2013
 */
package com.resurrection.blowtorch2.lib.service;

/**
 * Whether this connection should accept MCCP2, and whether the automatic
 * fallback has already fired.
 *
 * <p>Kept out of {@link Connection} because the interesting part is one rule
 * that is easy to get wrong and impossible to unit-test inside a god class:
 * <b>the profile being replayed is not a player decision</b>.
 * {@code initSettings()} hands every option back through the same setter on
 * every {@code doStartup()}, so a fallback that cleared itself on "use_mccp is
 * true" re-enabled compression on the reconnect it had just triggered, failed
 * the same way, and reconnected forever.
 *
 * <p>Synchronized: the handler thread reads it while a settings change can arrive
 * on a binder thread (UI → service calls are synchronous).
 */
final class MccpFallbackState {

	/** What the profile says. */
	private boolean mProfileOn = true;
	/** Set once decompression failed on this connection. */
	private boolean mDisabledForSession = false;

	/** @return true when COMPRESS2 should be accepted. */
	synchronized boolean isEnabled() {
		return mProfileOn && !mDisabledForSession;
	}

	/** @return true when the automatic fallback is holding compression off. */
	synchronized boolean isFallbackEngaged() {
		return mDisabledForSession;
	}

	/**
	 * The profile value, from a load or a settings replay. Never clears the
	 * fallback — the replay says what the file holds, not what the player wants.
	 */
	synchronized void applyProfileValue(final boolean on) {
		mProfileOn = on;
	}

	/** The player moved the switch by hand. Turning it back on means "try again". */
	synchronized void applyPlayerToggle(final boolean on) {
		mProfileOn = on;
		if (on) {
			mDisabledForSession = false;
		}
	}

	/**
	 * Decompression threw.
	 *
	 * @return true the first time, when the caller should warn the player and
	 *         reconnect without compression; false afterwards, so a server that
	 *         keeps failing cannot drive a reconnect loop.
	 */
	synchronized boolean onFailure() {
		if (mDisabledForSession) {
			return false;
		}
		mDisabledForSession = true;
		return true;
	}
}
