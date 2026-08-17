package com.resurrection.blowtorch2.lib.window;

import java.util.Locale;

/**
 * OSC 8 hyperlinks: {@code ESC ] 8 ; params ; URI BEL} (or ST).
 *
 * <p>Returns null when the payload is not OSC 8 (title, BTIMG, junk) so the
 * caller keeps skipping it. An empty URI, Darkwind's {@code )}, or a scheme we
 * will not open is a close ({@code uri == null}).
 */
public final class OscEight {

	private OscEight() {
	}

	public static final class Result {
		/** Null means close. */
		public final String uri;
		/** Optional {@code id=} from params; unused in v1 matching. */
		public final String id;

		Result(final String uri, final String id) {
			this.uri = uri;
			this.id = id;
		}

		public boolean isClose() {
			return uri == null;
		}
	}

	/**
	 * Bytes between {@code ESC ]} and the terminator, already decoded.
	 *
	 * @return null if this is not OSC 8
	 */
	public static Result parse(final String payload) {
		if (payload == null || payload.length() < 2) {
			return null;
		}
		if (payload.charAt(0) != '8' || payload.charAt(1) != ';') {
			return null;
		}
		final String rest = payload.substring(2);
		final int semi = rest.indexOf(';');
		if (semi < 0) {
			return null;
		}
		final String params = rest.substring(0, semi);
		final String uri = rest.substring(semi + 1).trim();
		final String id = parseId(params);
		if (uri.length() == 0 || ")".equals(uri)) {
			return new Result(null, id);
		}
		if (!isSafeUri(uri)) {
			return new Result(null, id);
		}
		return new Result(uri, id);
	}

	/** http, https, mailto, and MXP SEND/menu/prompt/expire schemes. */
	public static boolean isSafeUri(final String uri) {
		if (uri == null) {
			return false;
		}
		final String u = uri.trim();
		if (u.length() == 0) {
			return false;
		}
		final String lower = u.toLowerCase(Locale.US);
		if (lower.startsWith("javascript:") || lower.startsWith("data:")
				|| lower.startsWith("vbscript:") || lower.startsWith("file:")) {
			return false;
		}
		return lower.startsWith("https://") || lower.startsWith("http://")
				|| lower.startsWith("mailto:")
				|| com.resurrection.blowtorch2.lib.service.mxp.MxpLinks.isMxpHref(u);
	}

	static String parseId(final String params) {
		if (params == null || params.length() == 0) {
			return null;
		}
		final String[] parts = params.split(":");
		for (int i = 0; i < parts.length; i++) {
			final String p = parts[i];
			if (p.length() >= 3 && p.regionMatches(true, 0, "id=", 0, 3)) {
				return p.substring(3);
			}
		}
		return null;
	}
}
