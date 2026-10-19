package com.resurrection.blowtorch2.lib.button;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pure parse of the text after {@code .icon}. No folder, no buttons.
 */
public final class IconCommandParser {

	public static final String ACTION_STATUS = "status";
	public static final String ACTION_HELP = "help";
	public static final String ACTION_FOLDER = "folder";
	public static final String ACTION_LIST = "list";
	public static final String ACTION_LIST_SET = "listset";
	public static final String ACTION_LIST_FILES = "listfiles";
	public static final String ACTION_SET = "set";

	public static final String SLOT_MAIN = "main";
	public static final String SLOT_HELD = "held";
	public static final String SLOT_FLIP = "flip";

	private IconCommandParser() {
	}

	public static final class Result {
		public String error;
		public String action;
		public String label;
		public String slot;
		/** Icon token as typed. Null when {@link #clear} is set. */
		public String token;
		public boolean clear;
		/** Set id for {@link #ACTION_LIST_SET}, as typed. */
		public String listName;
	}

	public static String usage() {
		return ".icon                         — the icon folder\n"
				+ ".icon folder                  — choose that folder\n"
				+ ".icon list                    — built-in sets\n"
				+ ".icon list <set>              — names in that set\n"
				+ ".icon list files              — files in the folder\n"
				+ ".icon set <label> <icon>      — main picture\n"
				+ ".icon set <label> held <icon> — picture under your finger\n"
				+ ".icon set <label> held darken — darken the main picture\n"
				+ ".icon set <label> flip <icon> — picture after a flip\n"
				+ ".icon set <label> flip same   — keep the main picture\n"
				+ ".icon set <label> off         — colored pad again\n"
				+ ".icon help                    — this text\n"
				+ "A label with a space is quoted: .icon set \"look north\" sword.png\n"
				+ "No label, or two the same: Name on Advanced, or the id beside it: .icon set b12 sword.png";
	}

	/** Blank is status. Unknown shape sets {@link Result#error} to {@link #usage()}. */
	public static Result parse(final String arg) {
		String line = stripPrefix(arg);
		List<String> tokens = tokenize(line);
		if (tokens == null) {
			return error("Close the quote around the button label.");
		}
		if (tokens.isEmpty()) {
			return action(ACTION_STATUS);
		}
		String verb = tokens.get(0).toLowerCase(Locale.US);
		if ("help".equals(verb)) {
			if (tokens.size() != 1) {
				return error(usage());
			}
			return action(ACTION_HELP);
		}
		if ("folder".equals(verb)) {
			if (tokens.size() != 1) {
				return error(usage());
			}
			return action(ACTION_FOLDER);
		}
		if ("list".equals(verb)) {
			return parseList(tokens);
		}
		if ("set".equals(verb)) {
			return parseSet(tokens);
		}
		return error(usage());
	}

	private static Result parseList(final List<String> tokens) {
		if (tokens.size() == 1) {
			return action(ACTION_LIST);
		}
		if (tokens.size() != 2) {
			return error(usage());
		}
		String which = tokens.get(1);
		if ("files".equals(which.toLowerCase(Locale.US))) {
			return action(ACTION_LIST_FILES);
		}
		if (which.length() == 0) {
			return error(usage());
		}
		Result result = action(ACTION_LIST_SET);
		result.listName = which;
		return result;
	}

	private static Result parseSet(final List<String> tokens) {
		if (tokens.size() < 3) {
			return error(usage());
		}
		String label = tokens.get(1);
		if (label.length() == 0) {
			return error("Name the button.");
		}
		int left = tokens.size() - 2;
		String a = tokens.get(2);
		String aFold = a.toLowerCase(Locale.US);
		if (left == 1 && "off".equals(aFold)) {
			Result result = set(label, SLOT_MAIN);
			result.clear = true;
			return result;
		}
		if (left == 1) {
			Result result = set(label, SLOT_MAIN);
			result.token = a;
			return result;
		}
		if (left != 2) {
			return error(usage());
		}
		String b = tokens.get(3);
		String bFold = b.toLowerCase(Locale.US);
		if ("held".equals(aFold) && "darken".equals(bFold)) {
			Result result = set(label, SLOT_HELD);
			result.clear = true;
			return result;
		}
		if ("flip".equals(aFold) && "same".equals(bFold)) {
			Result result = set(label, SLOT_FLIP);
			result.clear = true;
			return result;
		}
		if ("held".equals(aFold) || "flip".equals(aFold)) {
			Result result = set(label, "held".equals(aFold) ? SLOT_HELD : SLOT_FLIP);
			result.token = b;
			return result;
		}
		return error(usage());
	}

	private static Result set(final String label, final String slot) {
		Result result = action(ACTION_SET);
		result.label = label;
		result.slot = slot;
		return result;
	}

	private static Result action(final String action) {
		Result result = new Result();
		result.action = action;
		return result;
	}

	private static Result error(final String message) {
		Result result = new Result();
		result.error = message;
		return result;
	}

	private static String stripPrefix(final String arg) {
		if (arg == null) {
			return "";
		}
		String line = arg.trim();
		if (line.length() >= 5 && line.substring(0, 5).toLowerCase(Locale.US).equals(".icon")) {
			line = line.substring(5).trim();
		}
		return line;
	}

	/** Null when a quote is never closed. */
	static List<String> tokenize(final String line) {
		List<String> out = new ArrayList<String>();
		int i = 0;
		int n = line.length();
		while (i < n) {
			while (i < n && line.charAt(i) <= ' ') {
				i++;
			}
			if (i >= n) {
				break;
			}
			char c = line.charAt(i);
			if (c == '"' || c == '\'') {
				int end = line.indexOf(c, i + 1);
				if (end < 0) {
					return null;
				}
				out.add(line.substring(i + 1, end));
				i = end + 1;
			} else {
				int j = i;
				while (j < n && line.charAt(j) > ' ') {
					j++;
				}
				out.add(line.substring(i, j));
				i = j;
			}
		}
		return out;
	}
}
