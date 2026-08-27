package com.resurrection.blowtorch2.lib.service.function;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.resurrection.blowtorch2.lib.service.Connection;
import com.resurrection.blowtorch2.lib.util.SessionLogSearch;

/**
 * Special command for in-window scrollback search, and for session-log files
 * older than N days.
 *
 * <pre>
 * .search phrase
 * .search 'phrase with spaces'
 * .search "phrase"
 * .search next
 * .search prev
 * .search close
 * .search logs
 * .search logs 7 phrase
 * .search logs 7 'multi word'
 * </pre>
 *
 * Buttons can use the same forms, or {@code /search 'phrase'} (also accepted).
 * {@code .search 'logs'} (quoted) still searches the word in the window.
 */
public class SearchCommand extends SpecialCommand {

	private static final Pattern QUOTED = Pattern.compile("^\\s*([\"'])(.*)\\1\\s*$");

	/**
	 * Carries “search logs older than N days” through
	 * {@code openScrollbackSearch(String)} without a new AIDL method. The two
	 * processes do not share statics.
	 */
	public static final String LOGS_BINDER_MARK = "\u0001BTLOGS\u0001";

	public static final int DEFAULT_LOG_DAYS = SessionLogSearch.DEFAULT_DAYS;

	public SearchCommand() {
		this.commandName = "search";
	}

	public enum Kind {
		EMPTY,
		NEXT,
		PREV,
		CLOSE,
		OPEN_LOGS,
		WINDOW,
		LOGS
	}

	public static final class Parsed {
		public final Kind kind;
		public final int days;
		public final String query;

		private Parsed(Kind kind, int days, String query) {
			this.kind = kind;
			this.days = days;
			this.query = query == null ? "" : query;
		}

		public static Parsed empty() {
			return new Parsed(Kind.EMPTY, DEFAULT_LOG_DAYS, "");
		}

		public static Parsed next() {
			return new Parsed(Kind.NEXT, DEFAULT_LOG_DAYS, "");
		}

		public static Parsed prev() {
			return new Parsed(Kind.PREV, DEFAULT_LOG_DAYS, "");
		}

		public static Parsed close() {
			return new Parsed(Kind.CLOSE, DEFAULT_LOG_DAYS, "");
		}

		public static Parsed openLogs() {
			return new Parsed(Kind.OPEN_LOGS, DEFAULT_LOG_DAYS, "");
		}

		public static Parsed window(String query) {
			return new Parsed(Kind.WINDOW, DEFAULT_LOG_DAYS, query);
		}

		public static Parsed logs(int days, String query) {
			return new Parsed(Kind.LOGS, SessionLogSearch.clampDays(days), query);
		}
	}

	@Override
	public Object execute(Object o, Connection c) {
		Parsed parsed = parse(o == null ? "" : o.toString());
		switch (parsed.kind) {
		case EMPTY:
			c.getService().doScrollbackSearch("");
			return null;
		case NEXT:
			c.getService().doScrollbackSearchNav(1);
			return null;
		case PREV:
			c.getService().doScrollbackSearchNav(-1);
			return null;
		case CLOSE:
			c.getService().doScrollbackSearchNav(0);
			return null;
		case OPEN_LOGS:
			c.getService().doOpenLogHistory();
			return null;
		case LOGS:
			c.getService().doScrollbackSearch(encodeLogsBinder(parsed.days, parsed.query));
			return null;
		case WINDOW:
		default:
			break;
		}

		if (parsed.query.length() == 0) {
			c.sendDataToWindow(getErrorMessage(
					"Search special command usage:",
					".search phrase   — find in scrollback\n"
							+ ".search 'multi word'\n"
							+ ".search next | prev | close\n"
							+ ".search logs              — browse session log files\n"
							+ ".search logs 7 goblin     — window + files older than 7 days\n"
							+ ".search logs 0 goblin     — window + every saved file for this world\n"
							+ "Also: /search 'phrase' from a button."));
			return null;
		}
		c.getService().doScrollbackSearch(parsed.query);
		return null;
	}

	/**
	 * Parse a {@code .search} argument. Existing forms are unchanged;
	 * {@code logs} as the first unquoted token is the new family.
	 */
	public static Parsed parse(String raw) {
		String arg = raw == null ? "" : raw.trim();
		if (arg.length() == 0) {
			return Parsed.empty();
		}

		String lower = arg.toLowerCase(Locale.US);
		if (lower.equals("next") || lower.equals("n")) {
			return Parsed.next();
		}
		if (lower.equals("prev") || lower.equals("previous") || lower.equals("p")) {
			return Parsed.prev();
		}
		if (lower.equals("close") || lower.equals("hide") || lower.equals("clear")) {
			return Parsed.close();
		}

		// Quoted whole argument: .search 'logs' still finds the word in the window.
		if (QUOTED.matcher(arg).matches()) {
			String q = stripQuotes(arg);
			if (q.length() == 0) {
				return Parsed.window("");
			}
			return Parsed.window(q);
		}

		if (startsWithLogsKeyword(arg)) {
			String rest = arg.length() == 4 ? "" : arg.substring(4).trim();
			if (rest.length() == 0) {
				return Parsed.openLogs();
			}
			int sp = 0;
			while (sp < rest.length() && !Character.isWhitespace(rest.charAt(sp))) {
				sp++;
			}
			String token = rest.substring(0, sp);
			String after = rest.substring(sp).trim();
			Integer days = parseDaysToken(token);
			if (days != null) {
				if (after.length() == 0) {
					return Parsed.openLogs();
				}
				String phrase = stripQuotes(after);
				if (phrase.length() == 0) {
					return Parsed.openLogs();
				}
				return Parsed.logs(days.intValue(), phrase);
			}
			String phrase = stripQuotes(rest);
			if (phrase.length() == 0) {
				return Parsed.openLogs();
			}
			return Parsed.logs(DEFAULT_LOG_DAYS, phrase);
		}

		return Parsed.window(stripQuotes(arg));
	}

	public static String encodeLogsBinder(int days, String phrase) {
		return LOGS_BINDER_MARK + SessionLogSearch.clampDays(days) + "\u0001"
				+ (phrase == null ? "" : phrase);
	}

	/**
	 * Decode a string arriving on {@code openScrollbackSearch}. Only the
	 * binder mark is special; a player-typed query is returned as WINDOW.
	 */
	public static Parsed decodeIncomingQuery(String raw) {
		if (raw == null || !raw.startsWith(LOGS_BINDER_MARK)) {
			return Parsed.window(raw == null ? "" : raw);
		}
		String rest = raw.substring(LOGS_BINDER_MARK.length());
		int sep = rest.indexOf('\u0001');
		if (sep < 0) {
			return Parsed.window(raw);
		}
		Integer days = parseDaysToken(rest.substring(0, sep));
		String phrase = rest.substring(sep + 1);
		if (days == null || phrase.length() == 0) {
			return Parsed.openLogs();
		}
		return Parsed.logs(days.intValue(), phrase);
	}

	private static boolean startsWithLogsKeyword(String arg) {
		if (arg.length() < 4) {
			return false;
		}
		if (!arg.regionMatches(true, 0, "logs", 0, 4)) {
			return false;
		}
		return arg.length() == 4 || Character.isWhitespace(arg.charAt(4));
	}

	private static Integer parseDaysToken(String token) {
		if (token == null || token.length() == 0) {
			return null;
		}
		for (int i = 0; i < token.length(); i++) {
			if (!Character.isDigit(token.charAt(i))) {
				return null;
			}
		}
		try {
			return Integer.valueOf(Integer.parseInt(token));
		} catch (NumberFormatException e) {
			return null;
		}
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
		return s;
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
