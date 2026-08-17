package com.resurrection.blowtorch2.lib.service.mxp;

import java.util.Locale;

/**
 * MXP {@code SOUND}/{@code MUSIC} mapping. Playback is
 * {@code GmcpMediaPlayer}; this class is the JVM-testable part.
 *
 * <p>{@code U=} may be a directory or the file itself. Only {@code http}
 * and {@code https} are followed — the same download path Client.Media
 * already uses. {@code file:}, {@code javascript:} and friends are not.
 */
public final class MxpSound {

	private MxpSound() {
	}

	public static final class Request {
		public String fname;
		public int volume = 100;
		public int loops = 1;
		public int priority = 50;
		public String mediaType = "sound";
		public String url;
		public boolean continueMusic;
		public String group;
	}

	public static boolean isStop(final String fname) {
		if (fname == null) {
			return false;
		}
		String s = fname.trim();
		return s.equalsIgnoreCase("off") || s.equalsIgnoreCase("stop");
	}

	public static boolean isAllowedDownloadUrl(final String url) {
		if (url == null) {
			return false;
		}
		String lower = url.trim().toLowerCase(Locale.US);
		return lower.startsWith("https://") || lower.startsWith("http://");
	}

	/**
	 * Build the URL to fetch, or {@code ""} when there is nothing safe to
	 * download. A {@code U=} whose last path segment looks like a file is
	 * used as-is; otherwise {@code fname} is appended as a child of that
	 * directory.
	 */
	public static String resolveDownloadUrl(final String url, final String fname) {
		if (!isAllowedDownloadUrl(url)) {
			return "";
		}
		String u = url.trim();
		String name = safeRelativeName(fname);
		int slash = u.lastIndexOf('/');
		int scheme = u.indexOf("://");
		boolean hasPath = slash > scheme + 2;
		String last = hasPath ? u.substring(slash + 1) : "";
		if (last.length() > 0) {
			if (name.length() > 0 && last.equals(name)) {
				return u;
			}
			if (last.indexOf('.') >= 0) {
				return u;
			}
		}
		if (name.length() == 0) {
			return "";
		}
		if (!u.endsWith("/")) {
			u = u + "/";
		}
		return u + name;
	}

	/**
	 * Path relative to the media cache / {@code /BlowTorch/sounds}. Drops
	 * {@code ..} and empty segments so a MUD cannot walk out of that folder.
	 */
	public static String safeRelativeName(final String fname) {
		if (fname == null) {
			return "";
		}
		String s = fname.replace('\\', '/').trim();
		if (s.length() == 0) {
			return "";
		}
		StringBuilder out = new StringBuilder();
		int start = 0;
		while (start < s.length()) {
			int slash = s.indexOf('/', start);
			if (slash < 0) {
				slash = s.length();
			}
			String part = s.substring(start, slash);
			start = slash + 1;
			if (part.length() == 0 || ".".equals(part) || "..".equals(part)) {
				continue;
			}
			if (out.length() > 0) {
				out.append('/');
			}
			out.append(part);
		}
		return out.toString();
	}
}
