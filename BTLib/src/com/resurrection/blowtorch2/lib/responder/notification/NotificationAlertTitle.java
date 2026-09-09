package com.resurrection.blowtorch2.lib.responder.notification;

/**
 * Shade title for a trigger/timer notification when several worlds may be up.
 * Android-free so the format can be tested without a Service.
 */
public final class NotificationAlertTitle {

	private NotificationAlertTitle() {
	}

	/** {@code world-a · goblin}, or whichever side is non-empty. */
	public static String withWorld(final String display, final String title) {
		String world = display == null ? "" : display.trim();
		String body = title == null ? "" : title;
		if (world.length() == 0) {
			return body;
		}
		if (body.length() == 0) {
			return world;
		}
		return world + " · " + body;
	}

	public static String throttleKey(final String display, final String title,
			final String message) {
		String world = display == null ? "" : display.trim();
		String t = title == null ? "" : title;
		String m = message == null ? "" : message;
		return world + "\n" + t + "\n" + m;
	}
}
