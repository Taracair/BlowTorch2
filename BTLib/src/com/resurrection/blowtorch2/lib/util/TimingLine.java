package com.resurrection.blowtorch2.lib.util;

/**
 * One line of the timing log. No Android, so the shape can be pinned
 * without a device.
 */
public final class TimingLine {

	private TimingLine() {
	}

	public static String format(final String wall, final long up, final int seq,
			final String proc, final String kind, final String fields) {
		StringBuilder s = new StringBuilder(160);
		s.append(wall == null ? "-" : wall);
		s.append(" seq=").append(seq);
		s.append(" up=").append(up);
		s.append(" proc=").append(proc == null ? "-" : proc);
		s.append(" kind=").append(kind == null ? "-" : kind);
		if (fields != null && fields.length() > 0) {
			s.append(' ').append(fields);
		}
		return s.toString();
	}

	/**
	 * A field value. Plain tokens stay bare. Anything with a space, quote,
	 * backslash, or {@code =} is quoted so a display name cannot break the line.
	 */
	public static String token(final String raw) {
		if (raw == null || raw.length() == 0) {
			return "-";
		}
		boolean plain = true;
		for (int i = 0; i < raw.length(); i++) {
			char c = raw.charAt(i);
			if (c <= ' ' || c == '"' || c == '\\' || c == '=') {
				plain = false;
				break;
			}
		}
		if (plain) {
			return raw;
		}
		StringBuilder s = new StringBuilder(raw.length() + 2);
		s.append('"');
		for (int i = 0; i < raw.length(); i++) {
			char c = raw.charAt(i);
			if (c == '"' || c == '\\') {
				s.append('\\');
			}
			if (c == '\n' || c == '\r' || c == '\t') {
				s.append(' ');
			} else {
				s.append(c);
			}
		}
		s.append('"');
		return s.toString();
	}
}
