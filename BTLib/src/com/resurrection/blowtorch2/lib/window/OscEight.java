package com.resurrection.blowtorch2.lib.window;

import java.util.Locale;

/**
 * OSC 8 hyperlinks: {@code ESC ] 8 ; params ; URI BEL} (or ST).
 *
 * <p>Returns null when the payload is not OSC 8 (title, BTIMG, junk) so the
 * caller keeps skipping it. An empty URI, Darkwind's {@code )}, or a scheme we
 * will not open is a close ({@code uri == null}).
 *
 * <p>StickMUD (and Mudlet's OSC 8 extensions) use {@code send:} to fire a
 * command and {@code prompt:} to fill the input bar. Those were measured in
 * the 17 Aug StickMUD session log: the OSC 8 test lab printed its labels, and
 * {@code isSafeUri} treated {@code send:look} as a close, so nothing was
 * tappable. Query strings on those schemes ({@code ?config=}, {@code ?preset=})
 * are discarded; they are Mudlet styling, not part of the command.
 */
public final class OscEight {

	public static final String SEND = "send:";
	public static final String PROMPT = "prompt:";

	private OscEight() {
	}

	/**
	 * {@code ESC ] 8 ;} in telnet-cleared bytes. A close ({@code ESC ] 8 ;;})
	 * matches too — the server still speaks OSC 8.
	 */
	public static boolean containsOpen(final byte[] raw) {
		if (raw == null || raw.length < 4) {
			return false;
		}
		for (int i = 0; i <= raw.length - 4; i++) {
			if (raw[i] == 0x1B && raw[i + 1] == 0x5D && raw[i + 2] == 0x38
					&& raw[i + 3] == 0x3B) {
				return true;
			}
		}
		return false;
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
		final int semi = uriSeparatorIndex(rest);
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

	/**
	 * Second {@code ;} starts the URI. When params are a JSON object (Mudlet
	 * style config), that object can contain {@code ;} inside strings, so the
	 * first semicolon in {@code rest} is the wrong split.
	 */
	static int uriSeparatorIndex(final String rest) {
		if (rest.length() > 0 && rest.charAt(0) == '{') {
			int end = jsonObjectEnd(rest);
			if (end >= 0 && end + 1 < rest.length() && rest.charAt(end + 1) == ';') {
				return end + 1;
			}
		}
		return rest.indexOf(';');
	}

	/** Index of the matching {@code } } for a JSON object at index 0, or -1. */
	static int jsonObjectEnd(final String s) {
		int depth = 0;
		boolean inString = false;
		boolean escape = false;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (inString) {
				if (escape) {
					escape = false;
				} else if (c == '\\') {
					escape = true;
				} else if (c == '"') {
					inString = false;
				}
				continue;
			}
			if (c == '"') {
				inString = true;
			} else if (c == '{') {
				depth++;
			} else if (c == '}') {
				depth--;
				if (depth == 0) {
					return i;
				}
			}
		}
		return -1;
	}

	/**
	 * http, https, mailto, ftp, Mudlet {@code send:}/{@code prompt:}, and MXP
	 * SEND/menu/prompt/expire schemes.
	 */
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
				|| lower.startsWith("vbscript:") || lower.startsWith("file:")
				|| lower.startsWith("preset:")) {
			return false;
		}
		return lower.startsWith("https://") || lower.startsWith("http://")
				|| lower.startsWith("mailto:") || lower.startsWith("ftp://")
				|| isSend(u) || isPrompt(u)
				|| com.resurrection.blowtorch2.lib.service.mxp.MxpLinks.isMxpHref(u);
	}

	public static boolean isSend(final String uri) {
		return startsWithIgnoreCase(uri, SEND);
	}

	public static boolean isPrompt(final String uri) {
		return startsWithIgnoreCase(uri, PROMPT);
	}

	/**
	 * A player tappable-word trigger on the same glyph should fire instead of
	 * this href. {@code send:}/{@code prompt:} are commands, the same class as
	 * MXP SEND. {@code http}/{@code https}/{@code mailto} still win.
	 */
	public static boolean tapWordOverrides(final String href) {
		return isSend(href) || isPrompt(href);
	}

	/** Command for a {@code send:} URI, query string stripped, percent-decoded. */
	public static String sendCommand(final String uri) {
		if (!isSend(uri)) {
			return null;
		}
		return decodeCommand(stripSchemeAndQuery(uri.trim(), SEND.length()));
	}

	/** Text for a {@code prompt:} URI, query string stripped, percent-decoded. */
	public static String promptCommand(final String uri) {
		if (!isPrompt(uri)) {
			return null;
		}
		return decodeCommand(stripSchemeAndQuery(uri.trim(), PROMPT.length()));
	}

	static String stripSchemeAndQuery(final String uri, final int schemeLen) {
		String rest = uri.substring(schemeLen);
		int q = rest.indexOf('?');
		if (q >= 0) {
			rest = rest.substring(0, q);
		}
		return rest;
	}

	static String decodeCommand(final String encoded) {
		if (encoded == null || encoded.length() == 0) {
			return "";
		}
		StringBuilder sb = new StringBuilder(encoded.length());
		for (int i = 0; i < encoded.length(); i++) {
			char c = encoded.charAt(i);
			if (c == '%' && i + 2 < encoded.length()) {
				int v = hexVal(encoded.charAt(i + 1), encoded.charAt(i + 2));
				if (v >= 0) {
					sb.append((char) v);
					i += 2;
					continue;
				}
			}
			sb.append(c);
		}
		return sb.toString();
	}

	private static int hexVal(final char a, final char b) {
		int hi = Character.digit(a, 16);
		int lo = Character.digit(b, 16);
		if (hi < 0 || lo < 0) {
			return -1;
		}
		return (hi << 4) | lo;
	}

	private static boolean startsWithIgnoreCase(final String uri, final String prefix) {
		if (uri == null) {
			return false;
		}
		String u = uri.trim();
		return u.length() >= prefix.length()
				&& u.regionMatches(true, 0, prefix, 0, prefix.length());
	}

	static String parseId(final String params) {
		if (params == null || params.length() == 0) {
			return null;
		}
		if (params.charAt(0) == '{') {
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
