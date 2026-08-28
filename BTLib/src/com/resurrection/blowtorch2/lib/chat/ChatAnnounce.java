package com.resurrection.blowtorch2.lib.chat;

/**
 * Cadence for a new-message line in the game window and for Android
 * notifications. Pure Java so the rules can be tested on the JVM.
 *
 * <p>Own lines, absorb, and mine must pass {@code countedUnread=false}: they
 * never announce. Digest does not print on the first append — the service
 * timer fires after the interval so the count is the batch (five tells →
 * {@code 5}, not {@code 1}). Notifications refresh the shade on every
 * counted append; sound/heads-up is Every, or once per digest window.
 */
public final class ChatAnnounce {

	public static final int MODE_OFF = 0;
	public static final int MODE_EVERY = 1;
	public static final int MODE_DIGEST = 2;

	/** PendingIntent extra: open this conversation when the player taps. */
	public static final String EXTRA_THREAD = "CHAT_THREAD";

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

	/**
	 * Game-window copy: a newline before so we do not sit on the previous MUD
	 * line, and a newline after so the next MUD line does not glue on
	 * ({@code …messages: 1CORPCHAT:}). Colour codes wrap the title line only.
	 */
	public static String windowLine(String title, int unread) {
		return "\n" + lineText(title, unread) + "\n";
	}

	/** Append-time game line. Digest waits for {@link #shouldPublishDigestLine}. */
	public static boolean shouldAnnounceLine(int mode, boolean countedUnread) {
		if (!countedUnread) {
			return false;
		}
		return mode == MODE_EVERY;
	}

	/**
	 * Timer: Digest prints the current unread (0 means they already opened
	 * the thread — skip). Off/Every do not print here.
	 */
	public static boolean shouldPublishDigestLine(int mode, int unreadAtFire) {
		return mode == MODE_DIGEST && unreadAtFire > 0;
	}

	/** Shade text follows the badge: every counted append while notify is on. */
	public static boolean shouldRefreshNotify(boolean notifyOn,
			boolean countedUnread) {
		return notifyOn && countedUnread;
	}

	/**
	 * Sound/heads-up. Every message in Every mode; once when a Digest/Off
	 * window opens ({@code windowJustOpened}).
	 */
	public static boolean shouldAlertNotify(boolean notifyOn, int mode,
			boolean countedUnread, boolean windowJustOpened) {
		if (!notifyOn || !countedUnread) {
			return false;
		}
		if (mode == MODE_EVERY) {
			return true;
		}
		return windowJustOpened;
	}

	/**
	 * Start the interval timer on the first counted append of a window.
	 * Digest needs it for the game line; Off+notify needs it so the next
	 * window can alert again.
	 */
	public static boolean shouldStartDigestTimer(int mode, boolean notifyOn,
			boolean countedUnread, boolean windowJustOpened) {
		if (!countedUnread || !windowJustOpened) {
			return false;
		}
		if (mode == MODE_DIGEST) {
			return true;
		}
		return mode == MODE_OFF && notifyOn;
	}
}
