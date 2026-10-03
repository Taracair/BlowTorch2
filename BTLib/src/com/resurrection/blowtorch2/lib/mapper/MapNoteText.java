package com.resurrection.blowtorch2.lib.mapper;

/**
 * Newlines cannot travel inside {@code .map note}. The command matcher is
 * {@code ^.(word) rest} and {@code .} stops at a line end, so a second line
 * never reaches {@code setNotes}. The stand-in is ASCII: the line is sent as
 * bytes in the world's encoding, and a private-use character becomes {@code ?}
 * on ISO-8859-1.
 */
public final class MapNoteText {

	/** ASCII stand-in for a newline on the way through {@code .map note}. */
	public static final String NEWLINE = "{{NL}}";

	private MapNoteText() {
	}

	public static String encode(final String text) {
		if (text == null || text.length() == 0) {
			return "";
		}
		String n = text.replace("\r\n", "\n").replace('\r', '\n');
		return n.replace("\n", NEWLINE);
	}

	public static String decode(final String text) {
		if (text == null) {
			return "";
		}
		return text.replace(NEWLINE, "\n");
	}
}
