package com.resurrection.blowtorch2.lib.service;

import java.util.Locale;

/**
 * The decision and reply half of the {@code mudstd.frame} GMCP package.
 *
 * <p>Specification: {@code https://mudstandards.org/gmcp/mudstandards_frame/}.
 * Note that the how-to page on the same site carries an older, shorter
 * vocabulary; this follows the package page, which is the normative one.
 *
 * <p>Parsing JSON needs Android, so that stays at the call site and this class
 * takes fields that have already been pulled out. What is here is the part worth
 * testing: which frames we accept, and the exact bytes we send back.
 *
 * <p>A client that cannot draw a frame is still a useful partner if it says so
 * precisely, so an unsupported request is answered with
 * {@code frame.closed reason=system} rather than silence.
 */
public final class MudstdFrame {

	/** The package name, spelled as the specification spells it. */
	public static final String MODULE = "mudstd.frame";

	/** Frame types the specification defines. */
	public static final String TYPE_EXTERNAL = "external";
	public static final String TYPE_DOCKED = "docked";
	public static final String TYPE_FLOATING = "floating";
	public static final String TYPE_CHILD = "child";
	public static final String TYPE_TAB = "tab";

	/** Content types the specification defines. */
	public static final String CONTENT_TERMINAL = "terminal";
	public static final String CONTENT_WEBVIEW = "webview";
	public static final String CONTENT_IMAGE = "image";

	/** Why a frame closed, per the specification. */
	public static final String REASON_SYSTEM = "system";
	public static final String REASON_USER = "user";

	private MudstdFrame() {
	}

	/**
	 * What we tell a server we can host.
	 *
	 * <p>Only what actually works. Image frames need a floating window that
	 * draws a bitmap, which does not exist yet; announcing it would promise
	 * something we would then decline.
	 */
	public static String supportMessage() {
		return MODULE + ".support {\"type\": [\"" + TYPE_FLOATING
				+ "\"], \"content\": [\"" + CONTENT_TERMINAL + "\"]}";
	}

	/** True when we can host a frame of this shape. */
	public static boolean canHost(final String type, final String content) {
		return TYPE_FLOATING.equalsIgnoreCase(norm(type))
				&& CONTENT_TERMINAL.equalsIgnoreCase(norm(content));
	}

	/**
	 * Why we are turning a frame down, in words fit for a log a server author
	 * will read.
	 *
	 * @return The reason, or null when the frame is one we can host.
	 */
	public static String refusalFor(final String type, final String content) {
		String t = norm(type);
		String c = norm(content);
		if (!isKnownType(t)) {
			return "unknown frame type '" + type + "'";
		}
		if (!isKnownContent(c)) {
			return "unknown content type '" + content + "'";
		}
		if (!TYPE_FLOATING.equals(t)) {
			return "only floating frames are supported, not '" + t + "'";
		}
		if (!CONTENT_TERMINAL.equals(c)) {
			return "only terminal content is supported so far, not '" + c + "'";
		}
		return null;
	}

	public static boolean isKnownType(final String type) {
		String t = norm(type);
		return TYPE_EXTERNAL.equals(t) || TYPE_DOCKED.equals(t) || TYPE_FLOATING.equals(t)
				|| TYPE_CHILD.equals(t) || TYPE_TAB.equals(t);
	}

	public static boolean isKnownContent(final String content) {
		String c = norm(content);
		return CONTENT_TERMINAL.equals(c) || CONTENT_WEBVIEW.equals(c)
				|| CONTENT_IMAGE.equals(c);
	}

	/**
	 * {@code mudstd.frame.opened} — sent once a frame is on screen.
	 *
	 * @param id The frame id the server chose.
	 * @param cols Width in characters.
	 * @param rows Height in characters.
	 * @param widthPx Width in pixels.
	 * @param heightPx Height in pixels.
	 */
	public static String openedEvent(final String id, final int cols, final int rows,
			final int widthPx, final int heightPx) {
		return MODULE + ".opened " + sizeBody(id, cols, rows, widthPx, heightPx);
	}

	/** {@code mudstd.frame.resized} — same body as opened. */
	public static String resizedEvent(final String id, final int cols, final int rows,
			final int widthPx, final int heightPx) {
		return MODULE + ".resized " + sizeBody(id, cols, rows, widthPx, heightPx);
	}

	/**
	 * {@code mudstd.frame.closed}.
	 *
	 * @param reason {@link #REASON_SYSTEM} when the client closed it of its own
	 *        accord, {@link #REASON_USER} when the player did.
	 */
	public static String closedEvent(final String id, final String reason) {
		String r = REASON_USER.equalsIgnoreCase(norm(reason)) ? REASON_USER : REASON_SYSTEM;
		return MODULE + ".closed {\"id\": \"" + escape(id) + "\", \"reason\": \"" + r + "\"}";
	}

	private static String sizeBody(final String id, final int cols, final int rows,
			final int widthPx, final int heightPx) {
		return "{\"id\": \"" + escape(id) + "\""
				+ ", \"sizeChar\": {\"width\": " + cols + ", \"height\": " + rows + "}"
				+ ", \"sizePixel\": {\"width\": " + widthPx + ", \"height\": " + heightPx + "}}";
	}

	/**
	 * Escape a string for a JSON double-quoted value.
	 *
	 * <p>Frame ids come from the server, so they are not ours to trust: an id
	 * with a quote in it would otherwise produce a malformed event and the
	 * server author would be debugging their own parser over our bug.
	 */
	static String escape(final String raw) {
		if (raw == null) {
			return "";
		}
		StringBuilder out = new StringBuilder(raw.length() + 8);
		for (int i = 0; i < raw.length(); i++) {
			char c = raw.charAt(i);
			switch (c) {
			case '"':
				out.append("\\\"");
				break;
			case '\\':
				out.append("\\\\");
				break;
			case '\n':
				out.append("\\n");
				break;
			case '\r':
				out.append("\\r");
				break;
			case '\t':
				out.append("\\t");
				break;
			default:
				if (c < 0x20) {
					out.append(String.format(Locale.US, "\\u%04x", Integer.valueOf(c)));
				} else {
					out.append(c);
				}
				break;
			}
		}
		return out.toString();
	}

	private static String norm(final String s) {
		return s == null ? "" : s.trim().toLowerCase(Locale.US);
	}
}
