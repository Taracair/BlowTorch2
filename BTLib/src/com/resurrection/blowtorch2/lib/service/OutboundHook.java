package com.resurrection.blowtorch2.lib.service;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * {@code OnOutgoing} on one already-aliased batch. Empty command text is
 * the bare Enter path and is not shown to filters. A string equal to the
 * current batch leaves the echo the client already built. A different
 * string replaces the batch, and replaces the echo when an echo was
 * showing. An empty array drops the batch and its echo.
 */
public final class OutboundHook {

	/** Command bytes about to be written, and the local-echo text beside them. */
	public static final class Batch {
		public final String command;
		public final String echo;

		public Batch(final String command, final String echo) {
			this.command = command;
			this.echo = echo;
		}
	}

	private OutboundHook() {
	}

	/** False for a disabled plugin and for a plugin with no {@code OnOutgoing}. */
	public static boolean include(final boolean enabled, final boolean offers) {
		return enabled && offers;
	}

	/**
	 * @param command {@code mCmdString} after aliases. Empty skips the filters.
	 * @param echo {@code mVisString}. Empty or null stays empty when the batch changes.
	 * @param encoding world encoding. Null skips the filters and returns the inputs.
	 */
	public static Batch apply(final String command, final String echo,
			final List<InboundTextHook.Filter> filters, final String encoding)
			throws UnsupportedEncodingException {
		if (command == null || command.length() == 0 || encoding == null) {
			return new Batch(command, echo);
		}
		byte[] in = command.getBytes(encoding);
		byte[] out = InboundTextHook.apply(in, filters);
		if (out == in || Arrays.equals(out, in)) {
			return new Batch(command, echo);
		}
		if (out.length == 0) {
			return new Batch("", "");
		}
		String next = new String(out, encoding);
		if (echo == null || echo.length() == 0) {
			return new Batch(next, echo);
		}
		return new Batch(next, next);
	}

	/** One piece of not-yet-shown echo. {@code sent} belongs to a queued command. */
	public static final class EchoPiece {
		public final String text;
		public final boolean sent;
		/** Already written ahead of {@code .password}. A later hook must not eat it. */
		public final boolean parked;

		public EchoPiece(final String text, final boolean sent) {
			this(text, sent, false);
		}

		public EchoPiece(final String text, final boolean sent, final boolean parked) {
			this.text = text;
			this.sent = sent;
			this.parked = parked;
		}
	}

	/**
	 * Echo split for commands flushed ahead of {@code .password}.
	 * {@code echoNow} is shown with this write. {@code kept} stays for the
	 * rest of the line. An unchanged command keeps every piece and shows
	 * nothing yet, so the outer batch can still format {@code name;look}.
	 */
	public static final class FlushEcho {
		public final String command;
		public final String echoNow;
		public final List<EchoPiece> kept;
		public final boolean replaced;

		public FlushEcho(final String command, final String echoNow,
				final List<EchoPiece> kept, final boolean replaced) {
			this.command = command;
			this.echoNow = echoNow;
			this.kept = kept;
			this.replaced = replaced;
		}
	}

	/** Echo still queued for the command that is about to be flushed. Parked pieces are skipped. */
	public static String sentEcho(final List<EchoPiece> parts) {
		if (parts == null || parts.isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < parts.size(); i++) {
			EchoPiece piece = parts.get(i);
			if (piece != null && piece.sent && !piece.parked && piece.text != null) {
				sb.append(piece.text);
			}
		}
		return sb.toString();
	}

	/** @param parked true for pieces already flushed, false for the rest of the line */
	public static String pieceText(final List<EchoPiece> parts, final boolean parked) {
		if (parts == null || parts.isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < parts.size(); i++) {
			EchoPiece piece = parts.get(i);
			if (piece != null && piece.parked == parked && piece.text != null) {
				sb.append(piece.text);
			}
		}
		return sb.toString();
	}

	/** Same trailing-semicolon and newline rule as the end of a send. */
	public static String formatEcho(final String raw, final String crlf) {
		if (raw == null || raw.length() == 0) {
			return "";
		}
		String s = raw;
		if (s.endsWith(";")) {
			s = s.substring(0, s.length() - 1);
		}
		if (crlf != null && crlf.length() > 0 && !s.endsWith(crlf)) {
			s = s + crlf;
		}
		return s;
	}

	/**
	 * After the remainder of the line is hooked. An unchanged command keeps
	 * {@code fullVis} ({@code name;look}). A changed command keeps the parked
	 * prefix and uses the hook's echo for the rest.
	 */
	public static String echoAfterRemainder(final String fullVis, final String original,
			final String hooked, final String hookedEcho, final String parkedEcho) {
		boolean changed = hooked == null || !hooked.equals(original);
		if (!changed) {
			return fullVis;
		}
		String now = hookedEcho == null ? "" : hookedEcho;
		if (parkedEcho == null || parkedEcho.length() == 0) {
			return now;
		}
		if (now.length() == 0) {
			return parkedEcho;
		}
		return parkedEcho + now;
	}

	/** Every piece becomes parked. The next flush ignores them. */
	public static List<EchoPiece> parkAll(final List<EchoPiece> parts) {
		ArrayList<EchoPiece> out = new ArrayList<EchoPiece>();
		if (parts == null) {
			return out;
		}
		for (int i = 0; i < parts.size(); i++) {
			EchoPiece piece = parts.get(i);
			if (piece != null) {
				out.add(new EchoPiece(piece.text, piece.sent, true));
			}
		}
		return out;
	}

	/**
	 * @param original command before the hook
	 * @param hooked command after the hook
	 * @param hookedEcho echo after the hook. Empty stays empty.
	 */
	public static FlushEcho flushEcho(final String original, final String hooked,
			final String hookedEcho, final List<EchoPiece> parts) {
		boolean replaced = hooked == null || !hooked.equals(original);
		if (!replaced) {
			return new FlushEcho(original, "", copy(parts), false);
		}
		ArrayList<EchoPiece> kept = new ArrayList<EchoPiece>();
		if (parts != null) {
			for (int i = 0; i < parts.size(); i++) {
				EchoPiece piece = parts.get(i);
				if (piece != null && (piece.parked || !piece.sent)) {
					kept.add(piece);
				}
			}
		}
		return new FlushEcho(hooked == null ? "" : hooked,
				hookedEcho == null ? "" : hookedEcho, kept, true);
	}

	/**
	 * What this credential flush shows now, and which pieces the outer batch
	 * still has to draw. A replaced command with echo paints the not-yet-shown
	 * prefix first, then the new line, and those pieces are consumed.
	 * A drop paints nothing yet. An unchanged command paints nothing yet.
	 */
	public static final class FlushPaint {
		public final String echoNow;
		public final List<EchoPiece> stillUnshown;

		public FlushPaint(final String echoNow, final List<EchoPiece> stillUnshown) {
			this.echoNow = echoNow;
			this.stillUnshown = stillUnshown;
		}
	}

	public static FlushPaint paint(final FlushEcho flush, final String crlf) {
		if (flush == null) {
			return new FlushPaint("", new ArrayList<EchoPiece>());
		}
		String now = flush.echoNow == null ? "" : flush.echoNow;
		if (flush.replaced && now.length() > 0) {
			String before = formatEcho(concat(flush.kept), crlf);
			String shown = before.length() == 0 ? now : before + now;
			return new FlushPaint(shown, new ArrayList<EchoPiece>());
		}
		return new FlushPaint("", parkAll(flush.kept));
	}

	private static String concat(final List<EchoPiece> parts) {
		if (parts == null || parts.isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < parts.size(); i++) {
			EchoPiece piece = parts.get(i);
			if (piece != null && piece.text != null) {
				sb.append(piece.text);
			}
		}
		return sb.toString();
	}

	private static List<EchoPiece> copy(final List<EchoPiece> parts) {
		ArrayList<EchoPiece> out = new ArrayList<EchoPiece>();
		if (parts == null) {
			return out;
		}
		for (int i = 0; i < parts.size(); i++) {
			EchoPiece piece = parts.get(i);
			if (piece != null) {
				out.add(piece);
			}
		}
		return out;
	}
}
