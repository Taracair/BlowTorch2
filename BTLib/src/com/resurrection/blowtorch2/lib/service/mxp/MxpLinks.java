package com.resurrection.blowtorch2.lib.service.mxp;

import java.util.Locale;

/**
 * URI schemes stamped onto {@code Text.href} for MXP SEND/A/EXPIRE.
 *
 * <p>They ride OSC 8 so both the service tree and the UI copy parse the same
 * bytes. {@code mxp-expire:} is a command with no display text: the parser
 * walks the tree it is appending to.
 */
public final class MxpLinks {

	public static final String SEND = "mxp-send:";
	public static final String MENU = "mxp-menu:";
	public static final String PROMPT = "mxp-prompt:";
	public static final String EXPIRE_CMD = "mxp-expire:";
	/** Record separator between hint list and command list in {@link #MENU}. */
	public static final char MENU_SPLIT = 0x1E;
	/** Prefix for OSC 8 {@code id=} so {@code OscEight.parseId} (colon-split) keeps it. */
	public static final String EXPIRE_ID_PREFIX = "mxp-";

	private MxpLinks() {
	}

	public static boolean isMxpHref(final String uri) {
		if (uri == null) {
			return false;
		}
		final String u = uri.trim();
		return u.startsWith(SEND) || u.startsWith(MENU) || u.startsWith(PROMPT)
				|| u.startsWith(EXPIRE_CMD);
	}

	/**
	 * A player tappable-word trigger on the same glyph should fire instead of
	 * this href. MXP SEND/MENU/PROMPT are commands, the same class of action
	 * as the trigger. {@code http}/{@code https}/{@code mailto} still win:
	 * opening a browser is the surprise to get wrong.
	 */
	public static boolean tapWordOverrides(final String href) {
		return isSend(href) || isMenu(href) || isPrompt(href);
	}

	public static boolean isSend(final String uri) {
		return uri != null && uri.startsWith(SEND);
	}

	public static boolean isMenu(final String uri) {
		return uri != null && uri.startsWith(MENU);
	}

	public static boolean isPrompt(final String uri) {
		return uri != null && uri.startsWith(PROMPT);
	}

	public static boolean isExpireCommand(final String uri) {
		return uri != null && uri.startsWith(EXPIRE_CMD);
	}

	public static String sendHref(final String command) {
		return SEND + encodePayload(command == null ? "" : command);
	}

	public static String menuHref(final String hints, final String commands) {
		return MENU + encodePayload(nullToEmpty(hints))
				+ MENU_SPLIT + encodePayload(nullToEmpty(commands));
	}

	public static String promptHref(final String command) {
		return PROMPT + encodePayload(command == null ? "" : command);
	}

	public static String expireHref(final String group) {
		return EXPIRE_CMD + nullToEmpty(group);
	}

	public static String expireId(final String group) {
		if (group == null || group.length() == 0) {
			return null;
		}
		return EXPIRE_ID_PREFIX + group.replace(':', '-');
	}

	public static String groupFromExpireId(final String id) {
		if (id == null || !id.startsWith(EXPIRE_ID_PREFIX)) {
			return null;
		}
		return id.substring(EXPIRE_ID_PREFIX.length());
	}

	public static String sendCommand(final String href) {
		if (!isSend(href)) {
			return null;
		}
		return decodePayload(href.substring(SEND.length()));
	}

	public static String[] menuHintsAndCommands(final String href) {
		if (!isMenu(href)) {
			return null;
		}
		final String rest = href.substring(MENU.length());
		final int split = rest.indexOf(MENU_SPLIT);
		if (split < 0) {
			return new String[] { "", decodePayload(rest) };
		}
		return new String[] {
				decodePayload(rest.substring(0, split)),
				decodePayload(rest.substring(split + 1))
		};
	}

	public static String promptCommand(final String href) {
		if (!isPrompt(href)) {
			return null;
		}
		return decodePayload(href.substring(PROMPT.length()));
	}

	/**
	 * Labels for a SEND menu. First hint is a tooltip; the rest match commands.
	 * Falls back to the commands themselves when the hint list does not line up.
	 */
	public static String[] menuLabels(final String hints, final String commands) {
		String[] cmds = splitPipe(commands);
		if (cmds.length == 0) {
			return cmds;
		}
		String[] h = splitPipe(hints);
		if (h.length == cmds.length + 1) {
			String[] labels = new String[cmds.length];
			System.arraycopy(h, 1, labels, 0, cmds.length);
			return labels;
		}
		if (h.length == cmds.length && hints != null && hints.length() > 0) {
			return h;
		}
		return cmds;
	}

	public static String[] splitPipe(final String s) {
		if (s == null || s.length() == 0) {
			return new String[0];
		}
		return s.split("\\|", -1);
	}

	public static String expireGroup(final String href) {
		if (!isExpireCommand(href)) {
			return null;
		}
		return href.substring(EXPIRE_CMD.length());
	}

	/**
	 * OSC 8 URIs split on {@code ;}. Commands must not carry BEL, ESC, or
	 * semicolon unencoded or the hyperlink scanner eats the rest of the line.
	 */
	static String encodePayload(final String raw) {
		if (raw == null || raw.length() == 0) {
			return "";
		}
		StringBuilder sb = new StringBuilder(raw.length() + 8);
		for (int i = 0; i < raw.length(); i++) {
			char c = raw.charAt(i);
			if (c == ';' || c == 0x1B || c == 0x07 || c == '%') {
				sb.append('%');
				String hex = Integer.toHexString(c).toUpperCase(Locale.US);
				if (hex.length() < 2) {
					sb.append('0');
				}
				sb.append(hex);
			} else {
				sb.append(c);
			}
		}
		return sb.toString();
	}

	static String decodePayload(final String encoded) {
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

	private static String nullToEmpty(final String s) {
		return s == null ? "" : s;
	}
}
