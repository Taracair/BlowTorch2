package com.resurrection.blowtorch2.lib.service;

import java.util.List;

/**
 * Apply inbound filters in order. Null from a filter leaves the current
 * bytes in place. An empty array replaces them. A throw leaves that
 * filter's input in place and the next filter still runs.
 */
public final class InboundTextHook {

	/** One rewrite step. */
	public interface Filter {
		/**
		 * @param text the chunk so far, never null once {@link #apply} has started
		 * @return replacement bytes, or null to keep {@code text}
		 */
		byte[] apply(byte[] text) throws Exception;
	}

	private InboundTextHook() {
	}

	/**
	 * @param text chunk to rewrite. Null is returned as null.
	 * @param filters ignored when null or empty, and {@code text} is then the same reference
	 */
	public static byte[] apply(final byte[] text, final List<Filter> filters) {
		if (text == null || filters == null || filters.isEmpty()) {
			return text;
		}
		byte[] current = text;
		for (int i = 0; i < filters.size(); i++) {
			Filter filter = filters.get(i);
			if (filter == null) {
				continue;
			}
			try {
				byte[] next = filter.apply(current);
				if (next != null) {
					current = next;
				}
			} catch (Exception ignored) {
				// This step failed. current stays what this filter was given.
			}
		}
		return current;
	}
}
