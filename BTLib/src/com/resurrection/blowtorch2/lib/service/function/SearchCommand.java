package com.resurrection.blowtorch2.lib.service.function;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.resurrection.blowtorch2.lib.service.Connection;

/**
 * Special command for in-window scrollback search.
 *
 * <pre>
 * .search phrase
 * .search 'phrase with spaces'
 * .search "phrase"
 * .search next
 * .search prev
 * .search close
 * </pre>
 *
 * Buttons can use the same forms, or {@code /search 'phrase'} (also accepted).
 */
public class SearchCommand extends SpecialCommand {

	private static final Pattern QUOTED = Pattern.compile("^\\s*([\"'])(.*)\\1\\s*$");

	public SearchCommand() {
		this.commandName = "search";
	}

	@Override
	public Object execute(Object o, Connection c) {
		String arg = o == null ? "" : o.toString().trim();
		if (arg.length() == 0) {
			c.getService().doScrollbackSearch("");
			return null;
		}

		String lower = arg.toLowerCase();
		if (lower.equals("next") || lower.equals("n")) {
			c.getService().doScrollbackSearchNav(1);
			return null;
		}
		if (lower.equals("prev") || lower.equals("previous") || lower.equals("p")) {
			c.getService().doScrollbackSearchNav(-1);
			return null;
		}
		if (lower.equals("close") || lower.equals("hide") || lower.equals("clear")) {
			c.getService().doScrollbackSearchNav(0);
			return null;
		}

		String query = stripQuotes(arg);
		if (query.length() == 0) {
			c.sendDataToWindow(getErrorMessage(
					"Search special command usage:",
					".search phrase   — find in scrollback\n"
							+ ".search 'multi word'\n"
							+ ".search next | prev | close\n"
							+ "Also: /search 'phrase' from a button."));
			return null;
		}
		c.getService().doScrollbackSearch(query);
		return null;
	}

	/** Normalize button-style {@code /search ...} into an argument string for {@link #execute}. */
	public static String argumentFromSlashCommand(String raw) {
		if (raw == null) {
			return "";
		}
		String s = raw.trim();
		if (s.regionMatches(true, 0, "/search", 0, 7)) {
			s = s.substring(7).trim();
		}
		return stripQuotes(s);
	}

	public static String stripQuotes(String arg) {
		if (arg == null) {
			return "";
		}
		Matcher m = QUOTED.matcher(arg.trim());
		if (m.matches()) {
			return m.group(2);
		}
		return arg.trim();
	}
}
