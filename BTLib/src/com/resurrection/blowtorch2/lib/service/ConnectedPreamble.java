package com.resurrection.blowtorch2.lib.service;

import java.util.List;

/**
 * Joins the bytes each plugin returned from {@code OnConnected}.
 * A null or empty piece is skipped. No pieces means nothing is written.
 */
public final class ConnectedPreamble {

	private ConnectedPreamble() {
	}

	/**
	 * @param parts plugin returns, in the order they should hit the wire.
	 *        Null is the same as an empty list.
	 * @return a new array, or null when there is nothing to send. The result
	 *         does not share storage with {@code parts}.
	 */
	public static byte[] concat(final List<byte[]> parts) {
		if (parts == null || parts.isEmpty()) {
			return null;
		}
		int total = 0;
		for (int i = 0; i < parts.size(); i++) {
			byte[] part = parts.get(i);
			if (part != null && part.length > 0) {
				total += part.length;
			}
		}
		if (total == 0) {
			return null;
		}
		byte[] out = new byte[total];
		int pos = 0;
		for (int i = 0; i < parts.size(); i++) {
			byte[] part = parts.get(i);
			if (part == null || part.length == 0) {
				continue;
			}
			System.arraycopy(part, 0, out, pos, part.length);
			pos += part.length;
		}
		return out;
	}
}
