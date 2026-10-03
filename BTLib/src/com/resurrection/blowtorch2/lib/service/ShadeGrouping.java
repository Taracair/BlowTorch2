package com.resurrection.blowtorch2.lib.service;

/**
 * Which shade group a notification belongs to.
 * Android-free so the nested-vs-separate choice can be tested without a Service.
 */
public final class ShadeGrouping {

	/** Options key. List index 0 is nested, 1 is a bar per type. */
	public static final String OPTION_KEY = "notification_grouping";

	public static final int NESTED = 0;
	public static final int SEPARATE = 1;

	public static final int KIND_CONNECTION = 0;
	public static final int KIND_ALERT = 1;
	public static final int KIND_CHAT = 2;

	/** One stack: connection, alerts and chat. */
	public static final String GROUP_STACK = "blowtorch.sessions";
	public static final String GROUP_CONNECTION = "blowtorch.connection";
	public static final String GROUP_ALERTS = "blowtorch.alerts";
	public static final String GROUP_CHAT = "blowtorch.chat";

	private ShadeGrouping() {
	}

	/** Unknown or missing index stays on the nested default. */
	public static int layoutFromIndex(final Integer index) {
		if (index != null && index.intValue() == SEPARATE) {
			return SEPARATE;
		}
		return NESTED;
	}

	/**
	 * Group key, or null when this notification is its own bar.
	 * Spawn-new on an alert overrides nested mode.
	 */
	public static String groupKey(final int layout, final int kind, final boolean spawnNew) {
		if (kind == KIND_ALERT && spawnNew) {
			return null;
		}
		if (layout == SEPARATE) {
			if (kind == KIND_CONNECTION) {
				return GROUP_CONNECTION;
			}
			if (kind == KIND_CHAT) {
				return GROUP_CHAT;
			}
			return GROUP_ALERTS;
		}
		return GROUP_STACK;
	}

	public static boolean isManagedGroup(final String group) {
		return GROUP_STACK.equals(group)
				|| GROUP_CONNECTION.equals(group)
				|| GROUP_ALERTS.equals(group)
				|| GROUP_CHAT.equals(group);
	}

	/** Chat channels contain {@code _chat_}. Anything else already in a managed group is an alert. */
	public static int kindForChannel(final String channelId) {
		if (channelId != null && channelId.indexOf("_chat_") >= 0) {
			return KIND_CHAT;
		}
		return KIND_ALERT;
	}
}
