package com.resurrection.blowtorch2.lib.service;

/**
 * Which connections belong on the ongoing session shade.
 * Handshake ({@code starting}) must list before TCP is up, or a second world
 * stays invisible until something else rebuilds the notification.
 */
public final class SessionNotificationListing {

	private SessionNotificationListing() {
	}

	public static boolean include(final boolean connected, final boolean starting) {
		return connected || starting;
	}
}
