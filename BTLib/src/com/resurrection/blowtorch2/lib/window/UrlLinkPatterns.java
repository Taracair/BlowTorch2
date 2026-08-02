package com.resurrection.blowtorch2.lib.window;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Builds the URL finder used by {@link TextTree} for hyperlink detection.
 *
 * <p>Schemes {@code http(s)://} and {@code www.} are always matched. Bare
 * domains (e.g. {@code achaea.com}) are optional and use a built-in TLD list
 * plus optional player extras. Short, word-like TLDs ({@code to}, {@code ch},
 * {@code ai}, …) are not built-in — add them via extras if needed.
 */
public final class UrlLinkPatterns {

	/** Max extras accepted from settings (ignore the rest). */
	public static final int MAX_EXTRA_TLDS = 32;

	/**
	 * Safe built-in TLDs for bare-host matching. Omits short / English-word
	 * collisions ({@code to}, {@code ch}, {@code ai}, {@code me}, {@code in},
	 * {@code it}, {@code id}, {@code co}, {@code be}, {@code at}, {@code cc},
	 * {@code dk}, {@code ie}).
	 */
	public static final String[] BUILT_IN_TLDS = {
			"com", "org", "net", "edu", "gov", "info", "biz", "io",
			"uk", "us", "eu", "de", "fr", "pl", "cz", "sk", "nl", "se", "no", "fi",
			"es", "ca", "au", "jp", "cn", "ru", "br", "mx", "kr", "tw", "hk", "sg",
			"ph", "vn", "th", "ar", "cl", "pe", "nz", "za",
			"app", "dev", "xyz", "online", "site", "tech", "gg", "wiki", "blog", "tv"
	};

	private static final Pattern DEFAULT_PATTERN = build(true, "");

	private UrlLinkPatterns() {
	}

	/** Default pattern: bare domains on, no extras. */
	public static Pattern defaultPattern() {
		return DEFAULT_PATTERN;
	}

	/**
	 * Compile a finder. Extra tokens that fail validation are skipped.
	 *
	 * @param bareEnabled when false, only {@code http(s)://} and {@code www.} match
	 * @param extraTldsCsv comma/space/semicolon-separated TLDs without dots
	 */
	public static Pattern build(final boolean bareEnabled, final String extraTldsCsv) {
		return Pattern.compile(buildFinderString(bareEnabled, extraTldsCsv));
	}

	/** Same as {@link #build} but returns the regex source (for tests / debug). */
	public static String buildFinderString(final boolean bareEnabled, final String extraTldsCsv) {
		StringBuilder sb = new StringBuilder(256);
		sb.append("(?i)\\b(");
		sb.append("(?:https?://|www\\.)[^\\s<>\"'\\]\\),]+");
		if (bareEnabled) {
			String tldAlt = tldAlternation(extraTldsCsv);
			if (tldAlt.length() > 0) {
				sb.append("|");
				sb.append("[a-z0-9](?:[a-z0-9\\-]{0,61}[a-z0-9])?");
				sb.append("(?:\\.[a-z0-9](?:[a-z0-9\\-]{0,61}[a-z0-9])?)*");
				sb.append("\\.(?:").append(tldAlt).append(")");
				// TLD must end the hostname label — blocks 2.ch inside 2.chudstopper.
				sb.append("(?![a-z0-9-])");
				sb.append("(?::[0-9]{2,5})?");
				sb.append("(?:/[^\\s<>\"'\\]\\),]*)?");
			}
		}
		sb.append(")");
		return sb.toString();
	}

	/**
	 * Parse and validate extra TLD tokens from a CSV-ish string.
	 * Dots stripped; lowercased; invalid / duplicate tokens dropped.
	 */
	public static String[] parseExtraTlds(final String csv) {
		if (csv == null || csv.length() == 0) {
			return new String[0];
		}
		LinkedHashSet<String> out = new LinkedHashSet<String>();
		String[] parts = csv.split("[,;\\s]+");
		for (int i = 0; i < parts.length && out.size() < MAX_EXTRA_TLDS; i++) {
			String t = normalizeTldToken(parts[i]);
			if (t != null) {
				out.add(t);
			}
		}
		return out.toArray(new String[out.size()]);
	}

	/** @return lowercased TLD, or null if invalid */
	public static String normalizeTldToken(final String raw) {
		if (raw == null) {
			return null;
		}
		String t = raw.trim();
		while (t.startsWith(".")) {
			t = t.substring(1);
		}
		if (t.length() < 2 || t.length() > 24) {
			return null;
		}
		for (int i = 0; i < t.length(); i++) {
			char c = t.charAt(i);
			boolean ok = (c >= 'a' && c <= 'z')
					|| (c >= 'A' && c <= 'Z')
					|| (c >= '0' && c <= '9');
			if (!ok) {
				return null;
			}
		}
		return t.toLowerCase(Locale.US);
	}

	private static String tldAlternation(final String extraTldsCsv) {
		LinkedHashSet<String> all = new LinkedHashSet<String>();
		for (String t : BUILT_IN_TLDS) {
			all.add(t);
		}
		for (String t : parseExtraTlds(extraTldsCsv)) {
			all.add(t);
		}
		if (all.isEmpty()) {
			return "";
		}
		ArrayList<String> quoted = new ArrayList<String>(all.size());
		for (String t : all) {
			quoted.add(Pattern.quote(t));
		}
		StringBuilder alt = new StringBuilder();
		for (int i = 0; i < quoted.size(); i++) {
			if (i > 0) {
				alt.append('|');
			}
			alt.append(quoted.get(i));
		}
		return alt.toString();
	}
}
