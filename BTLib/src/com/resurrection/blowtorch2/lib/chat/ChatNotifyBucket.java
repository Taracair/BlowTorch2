package com.resurrection.blowtorch2.lib.chat;

/**
 * Four Android notification channels for chat — not one channel per nick.
 * The player assigns a conversation to a bucket in ⚙; system Settings can
 * mute tells without muting channels.
 */
public final class ChatNotifyBucket {

	public static final String TELLS = "tells";
	public static final String CHANNELS = "channels";
	public static final String AUCTION = "auction";
	public static final String OTHER = "other";

	public static final String[] ALL = new String[] {
			TELLS, CHANNELS, AUCTION, OTHER
	};

	public static final String[] LABELS = new String[] {
			"Tells", "Channels", "Auction", "Other"
	};

	private ChatNotifyBucket() {
	}

	public static String coerce(String raw) {
		if (TELLS.equals(raw) || CHANNELS.equals(raw) || AUCTION.equals(raw)) {
			return raw;
		}
		return OTHER;
	}

	public static String label(String bucket) {
		String id = coerce(bucket);
		for (int i = 0; i < ALL.length; i++) {
			if (ALL[i].equals(id)) {
				return LABELS[i];
			}
		}
		return LABELS[LABELS.length - 1];
	}
}
