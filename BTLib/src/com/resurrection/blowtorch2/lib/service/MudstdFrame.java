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
	 * <p>Floating is the only geometry announced. A frame can also be shown as a
	 * drawer down from the top of the screen, but that is the player's choice
	 * about presentation rather than a second thing to promise a server, and
	 * widening the {@code type} array is a change to what a server can ask for.
	 * There is an open question with the specification's author about what his
	 * compatibility page matches on, and adding a type while that is unanswered
	 * would confuse the answer.
	 *
	 * <p>Both content types are announced and both are now drawn: terminal as
	 * text in the main window, image in a window of its own.
	 */
	public static String supportMessage() {
		return MODULE + ".support {\"type\": [\"" + TYPE_FLOATING
				+ "\"], \"content\": [\"" + CONTENT_TERMINAL
				+ "\", \"" + CONTENT_IMAGE + "\"]}";
	}

	/** True when we will take the frame on. Anything in the vocabulary is taken. */
	public static boolean canHost(final String type, final String content) {
		return isKnownType(type) && isKnownContent(content);
	}

	/**
	 * True when we can actually put this frame's content in front of the player.
	 *
	 * <p>Image content is drawn now, in a window of its own. Terminal content is
	 * still text in the main window labelled with the frame id.
	 *
	 * <p>Type is not part of the test, and that is deliberate. Whether a frame
	 * appears as a floating window or as a drawer down from the top is the
	 * player's choice here, exactly as it is for BlowTorch's own extra text
	 * windows — so a {@code docked} frame is as drawable as a {@code floating}
	 * one. What goes on the wire in {@link #supportMessage()} stays
	 * {@code floating} only: that is what we volunteer, and widening it is a
	 * separate conversation with the specification's author.
	 */
	public static boolean canRender(final String type, final String content) {
		String c = norm(content);
		return CONTENT_TERMINAL.equals(c) || CONTENT_IMAGE.equals(c);
	}

	/** True when this frame's content wants a window of its own. */
	public static boolean needsFrameWindow(final String content) {
		return CONTENT_IMAGE.equals(norm(content));
	}

	/**
	 * Why we are turning a frame down, in words fit for a log a server author
	 * will read. Only vocabulary outside the specification is refused now: that
	 * is a protocol error on the sending side, and worth saying so.
	 *
	 * @return The reason, or null when the frame is one we will take.
	 */
	public static String refusalFor(final String type, final String content) {
		if (!isKnownType(type)) {
			return "unknown frame type '" + type + "'";
		}
		if (!isKnownContent(content)) {
			return "unknown content type '" + content + "'";
		}
		return null;
	}

	/**
	 * How an accepted frame is being treated, so an acknowledgement is never
	 * mistaken for a drawing.
	 *
	 * @return Plain words for the log and the window, or null when it is drawn.
	 */
	public static String acceptedButNotDrawn(final String type, final String content) {
		if (canRender(type, content)) {
			return null;
		}
		// Everything else in the vocabulary is drawn, and anything outside it was
		// already turned down by refusalFor before this is reached, so webview is
		// the only case left.
		return "accepted; there is no webview, so its content is reported only";
	}

	/**
	 * Describe an image payload without echoing it.
	 *
	 * <p>A base64 map is tens of kilobytes; the useful facts are that it
	 * arrived, how it was carried, and how big it was.
	 *
	 * @param image The raw value of the image field.
	 * @return One line naming the carrier and the size.
	 */
	public static String imageSummary(final String image) {
		if (image == null || image.length() == 0) {
			return "no image field";
		}
		String trimmed = image.trim();
		if (trimmed.toLowerCase(Locale.US).startsWith("base64:")) {
			int payload = trimmed.length() - "base64:".length();
			// 4 base64 characters carry 3 bytes; padding makes this approximate.
			return "base64, " + payload + " chars (about " + ((payload / 4) * 3) + " bytes)";
		}
		if (trimmed.toLowerCase(Locale.US).startsWith("http://")
				|| trimmed.toLowerCase(Locale.US).startsWith("https://")) {
			return "url, " + trimmed;
		}
		return "unrecognised carrier, " + trimmed.length() + " chars";
	}

	/**
	 * True when an image field carries a URL rather than the bytes themselves.
	 *
	 * <p>Worth telling apart because a URL is short enough to have already been
	 * logged verbatim, while base64 is not and has to be described instead.
	 */
	public static boolean isUrlCarrier(final String image) {
		if (image == null) {
			return false;
		}
		String lower = image.trim().toLowerCase(Locale.US);
		return lower.startsWith("http://") || lower.startsWith("https://");
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
