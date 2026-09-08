package com.resurrection.blowtorch2.lib.service;

import java.util.List;

/**
 * Player-visible {@code .wait show} / {@code .wait info} text. Android-free so
 * tests can pin the lines without a Connection.
 */
public final class WaitQueueText {

	private static final int PREVIEW_MAX = 80;

	private WaitQueueText() {
	}

	/** One armed remainder. */
	public static final class Item {
		public final long remainingMs;
		public final String remainder;

		public Item(final long remainingMs, final String remainder) {
			this.remainingMs = remainingMs;
			this.remainder = remainder == null ? "" : remainder;
		}
	}

	public static String format(final List<Item> items) {
		if (items == null || items.isEmpty()) {
			return "\n[wait queue empty]\n";
		}
		StringBuilder sb = new StringBuilder();
		sb.append("\n[wait queue]\n");
		for (int i = 0; i < items.size(); i++) {
			Item it = items.get(i);
			sb.append(i + 1).append(") ");
			if (it.remainingMs <= 0L) {
				sb.append("due now");
			} else {
				sb.append("in ").append(CommandWait.format(it.remainingMs));
			}
			if (it.remainder.length() > 0) {
				sb.append(" — ").append(it.remainder);
			}
			sb.append('\n');
		}
		return sb.toString();
	}

	public static String remainderPreview(final List<String> segments,
			final String holdover) {
		StringBuilder sb = new StringBuilder();
		if (holdover != null && holdover.length() > 0) {
			sb.append(holdover);
		}
		if (segments != null) {
			for (int i = 0; i < segments.size(); i++) {
				String s = segments.get(i);
				if (s == null) {
					continue;
				}
				if (sb.length() > 0 && sb.charAt(sb.length() - 1) != ';') {
					sb.append(';');
				}
				sb.append(s);
			}
		}
		if (sb.length() > PREVIEW_MAX) {
			return sb.substring(0, PREVIEW_MAX - 3) + "...";
		}
		return sb.toString();
	}
}
