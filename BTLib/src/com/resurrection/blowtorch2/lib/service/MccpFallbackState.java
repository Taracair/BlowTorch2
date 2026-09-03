/*
 * Copyright (C) Dan Block 2013
 */
package com.resurrection.blowtorch2.lib.service;

/**
 * MCCP2 on for this connection. Replaying the profile through
 * {@code initSettings()} is not a player decision — clearing fallback when
 * {@code use_mccp} is true re-enabled compression, failed, and reconnected
 * forever. Synchronized: handler thread vs binder settings.
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
