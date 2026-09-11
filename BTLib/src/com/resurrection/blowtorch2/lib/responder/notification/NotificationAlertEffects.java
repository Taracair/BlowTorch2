package com.resurrection.blowtorch2.lib.responder.notification;

/**
 * Sound and vibrate choices for a notification action.
 *
 * <p>The shade entry is silent: Android O+ ignores {@code Builder.setSound} /
 * {@code setVibrate} on a channel, and the notification stream follows the
 * ringer. The service process plays the sound and the buzz itself.
 */
public final class NotificationAlertEffects {

	public static final long[] VIBRATE_DEFAULT = {0, 250, 200, 250};
	public static final long[] VIBRATE_VERY_SHORT = {0, 200, 50, 200};
	public static final long[] VIBRATE_SHORT = {0, 500, 100, 300};
	public static final long[] VIBRATE_LONG = {0, 1000, 500, 1000};
	public static final long[] VIBRATE_SUPER_LONG = {0, 2000, 1000, 2000, 1000, 2000};

	private NotificationAlertEffects() {
	}

	/**
	 * @param vibrateLength 0 Default, 1 Very Short, 2 Short, 3 Long, 4 Super Long
	 * @return off-on timings, or null when vibrate is off
	 */
	public static long[] vibratePattern(final boolean enabled, final int vibrateLength) {
		if (!enabled) {
			return null;
		}
		switch (vibrateLength) {
		case 1:
			return VIBRATE_VERY_SHORT;
		case 2:
			return VIBRATE_SHORT;
		case 3:
			return VIBRATE_LONG;
		case 4:
			return VIBRATE_SUPER_LONG;
		default:
			return VIBRATE_DEFAULT;
		}
	}

	public static boolean usePhoneDefaultSound(final boolean useSound, final String soundPath) {
		return useSound && (soundPath == null || soundPath.length() == 0);
	}

	public static String customSoundPath(final boolean useSound, final String soundPath) {
		if (!useSound || soundPath == null || soundPath.length() == 0) {
			return null;
		}
		return soundPath;
	}
}
