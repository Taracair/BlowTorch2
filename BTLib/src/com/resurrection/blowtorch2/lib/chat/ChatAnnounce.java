package com.resurrection.blowtorch2.lib.chat;

/**
 * Cadence for a new-message line in the game window and for Android
 * notifications. Pure Java so the rules can be tested on the JVM.
 *
 * <p>Own lines, absorb, and mine must pass {@code countedUnread=false}: they
 * never announce. Digest uses {@code lastAnnounceMs==0} as "never yet", so the
 * first counted message always fires.
 */
public final class ChatAnnounce {

	public static final int MODE_OFF = 0;
	public static final int MODE_EVERY = 1;
	public static final int MODE_DIGEST = 2;

	public static final int DEFAULT_SECONDS = 60;
	public static final int MIN_SECONDS = 5;
	public static final int MAX_SECONDS = 3600;

	private ChatAnnounce() {
	}

	/**
	 * Integer or String {@code "0"}/{@code "1"}/{@code "2"}; anything else is
	 * {@link #MODE_OFF}. Settings XML arrives as strings.
	 */
	public static int coerceMode(Object value) {
		int n;
		if (value instanceof Number) {
			n = ((Number) value).intValue();
		} else if (value instanceof String) {
			try {
				n = Integer.parseInt(((String) value).trim());
			} catch (NumberFormatException e) {
				return MODE_OFF;
			}
		} else {
			return MODE_OFF;
		}
		if (n == MODE_EVERY || n == MODE_DIGEST) {
			return n;
		}
		return MODE_OFF;
	}

	/** Clamp 5..3600; null or unparseable is 60. */
	public static int coerceSeconds(Object value) {
		int n = DEFAULT_SECONDS;
		if (value instanceof Number) {
			n = ((Number) value).intValue();
		} else if (value instanceof String) {
			try {
				n = Integer.parseInt(((String) value).trim());
			} catch (NumberFormatException e) {
				n = DEFAULT_SECONDS;
			}
		}
		if (n < MIN_SECONDS) {
			return MIN_SECONDS;
		}
		if (n > MAX_SECONDS) {
			return MAX_SECONDS;
		}
		return n;
	}

	public static boolean coerceBool(Object value, boolean defaultIfNull) {
		if (value == null) {
			return defaultIfNull;
		}
		if (value instanceof Boolean) {
			return ((Boolean) value).booleanValue();
		}
		if (value instanceof String) {
			String s = ((String) value).trim();
			if (s.equalsIgnoreCase("true") || s.equals("1")) {
				return true;
			}
			if (s.equalsIgnoreCase("false") || s.equals("0")) {
				return false;
			}
		}
		return defaultIfNull;
	}

	/**
	 * {@code Thread X has new messages: 5}. Blank title becomes {@code Chat}.
	 */
	public static String lineText(String title, int unread) {
		String name = title == null ? "" : title.trim();
		if (name.length() == 0) {
			name = "Chat";
		}
		return "Thread " + name + " has new messages: " + unread;
	}

	public static boolean shouldAnnounceLine(int mode, boolean countedUnread,
			long nowMs, long lastAnnounceMs, int seconds) {
		if (!countedUnread) {
			return false;
		}
		if (mode == MODE_EVERY) {
			return true;
		}
		if (mode == MODE_DIGEST) {
			return lastAnnounceMs == 0L
					|| (nowMs - lastAnnounceMs) >= (seconds * 1000L);
		}
		return false;
	}

	/**
	 * When the game line is Off, notifications still fire on the digest
	 * interval. Otherwise they follow the line cadence.
	 */
	public static boolean shouldNotify(boolean notifyOn, int mode,
			boolean countedUnread, long nowMs, long lastAnnounceMs, int seconds) {
		if (!countedUnread || !notifyOn) {
			return false;
		}
		int cadence = (mode == MODE_OFF) ? MODE_DIGEST : mode;
		return shouldAnnounceLine(cadence, true, nowMs, lastAnnounceMs, seconds);
	}
}
