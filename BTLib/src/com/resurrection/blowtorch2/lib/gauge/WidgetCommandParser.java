/*
 * Copyright (C) BlowTorch contributors
 */
package com.resurrection.blowtorch2.lib.gauge;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Pure parser for {@code .widget} arguments. No Connection, no Android, no
 * store mutation — {@link #parse(String)} only fills a {@link Result}.
 *
 * <p>Shape and source names are the same strings the gauge model persists
 * ({@code hbar}/{@code vbar}/{@code ring}/{@code timer}, {@code manual}/
 * {@code gmcp}/{@code mcp}/{@code var}/{@code timer}/{@code regex}). Aliases such as
 * {@code bar}, {@code circle} or {@code countdown} are folded to those
 * canonical names here so a later {@code WidgetCommand} can apply the result
 * without a second synonym table.
 */
public final class WidgetCommandParser {

	public static final String ACTION_LIST = "list";
	public static final String ACTION_ADD = "add";
	public static final String ACTION_REMOVE = "remove";
	public static final String ACTION_SHOW = "show";
	public static final String ACTION_HIDE = "hide";
	public static final String ACTION_SHAPE = "shape";
	public static final String ACTION_COLOR = "color";
	public static final String ACTION_TRACK = "track";
	public static final String ACTION_OPACITY = "opacity";
	public static final String ACTION_SIZE = "size";
	public static final String ACTION_MOVE = "move";
	public static final String ACTION_LABEL = "label";
	public static final String ACTION_VALUE = "value";
	public static final String ACTION_CAPTION = "caption";
	public static final String ACTION_SOURCE = "source";
	public static final String ACTION_SET = "set";
	public static final String ACTION_TAP = "tap";
	public static final String ACTION_SWIPE = "swipe";
	public static final String ACTION_HOLD = "hold";
	public static final String ACTION_WARN = "warn";
	public static final String ACTION_IME = "ime";
	public static final String ACTION_HELP = "help";

	public static final String SHAPE_HBAR = GaugeWidget.Shape.HBAR.toJsonValue();
	public static final String SHAPE_VBAR = GaugeWidget.Shape.VBAR.toJsonValue();
	public static final String SHAPE_RING = GaugeWidget.Shape.RING.toJsonValue();
	public static final String SHAPE_TIMER = GaugeWidget.Shape.TIMER.toJsonValue();

	public static final String SOURCE_MANUAL = GaugeWidget.Source.MANUAL.toJsonValue();
	public static final String SOURCE_GMCP = GaugeWidget.Source.GMCP.toJsonValue();
	public static final String SOURCE_MCP = GaugeWidget.Source.MCP.toJsonValue();
	public static final String SOURCE_VAR = GaugeWidget.Source.VAR.toJsonValue();
	public static final String SOURCE_TIMER = GaugeWidget.Source.TIMER.toJsonValue();
	public static final String SOURCE_REGEX = GaugeWidget.Source.REGEX.toJsonValue();

	public static final String IME_STAY = GaugeWidget.ImeMode.STAY.toJsonValue();
	public static final String IME_HIDE = GaugeWidget.ImeMode.HIDE.toJsonValue();
	public static final String IME_OVERLAY = GaugeWidget.ImeMode.OVERLAY.toJsonValue();

	public static final String SWIPE_UP = "up";
	public static final String SWIPE_DOWN = "down";
	public static final String SWIPE_LEFT = "left";
	public static final String SWIPE_RIGHT = "right";

	private static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9_]{1,24}$");

	private WidgetCommandParser() {
	}

	/**
	 * Parsed {@code .widget} line. {@link #error} non-null means print that
	 * text and do not mutate the store. {@link #note} is an optional status
	 * line when there is no mutation (help text; list dump is filled later).
	 */
	public static final class Result {
		public String error;
		public String note;
		public String action;
		public String id;
		/** Canonical {@code hbar}|{@code vbar}|{@code ring}|{@code timer}. */
		public String shape;
		/** Canonical {@code manual}|{@code gmcp}|{@code mcp}|{@code var}|{@code timer}|{@code regex}. */
		public String source;
		public String path;
		public String maxPath;
		/** Colour token as typed ({@code red}, {@code #CC2222}). */
		public String color;
		public Integer opacity;
		public Integer warnPct;
		public Integer x;
		public Integer y;
		public Integer w;
		public Integer h;
		/** Label text, or the command string for tap/swipe/hold. Empty = clear. */
		public String text;
		/** Canonical {@code up}|{@code down}|{@code left}|{@code right}. */
		public String swipeDir;
		public Double value;
		public Double max;
		/** show/hide/value on/off. */
		public Boolean flag;
		/** Canonical {@code stay}|{@code hide}|{@code overlay} for {@code .widget ime}. */
		public String ime;
	}

	/**
	 * Parse the argument after {@code .widget}, or a full line that still
	 * starts with {@code .widget}. Null or blank → {@link #ACTION_HELP}.
	 * Unknown verbs → {@link Result#error} containing {@link #usage()}.
	 */
	public static Result parse(final String arg) {
		final String line = stripCommandPrefix(arg);
		if (line.length() == 0) {
			return helpResult();
		}
		final String[] parts = line.split("\\s+");
		final String verb = parts[0].toLowerCase(Locale.US);
		if ("help".equals(verb) || "?".equals(verb) || "usage".equals(verb)) {
			return helpResult();
		}
		if ("list".equals(verb)) {
			return parseList(parts);
		}
		if ("add".equals(verb)) {
			return parseAdd(parts);
		}
		if ("remove".equals(verb) || "delete".equals(verb) || "rm".equals(verb)) {
			return parseRemove(parts);
		}
		if ("show".equals(verb)) {
			return parseShowHide(parts, true);
		}
		if ("hide".equals(verb)) {
			return parseShowHide(parts, false);
		}
		if ("shape".equals(verb)) {
			return parseShape(parts);
		}
		if ("color".equals(verb) || "colour".equals(verb)) {
			return parseColor(parts, ACTION_COLOR);
		}
		if ("track".equals(verb)) {
			return parseColor(parts, ACTION_TRACK);
		}
		if ("opacity".equals(verb)) {
			return parseOpacity(parts);
		}
		if ("size".equals(verb)) {
			return parseSize(parts);
		}
		if ("move".equals(verb)) {
			return parseMove(parts);
		}
		if ("label".equals(verb)) {
			return parseRestText(parts, ACTION_LABEL);
		}
		if ("value".equals(verb)) {
			return parseOnOffFlag(parts, ACTION_VALUE, "value");
		}
		if ("caption".equals(verb) || "nametag".equals(verb)) {
			return parseOnOffFlag(parts, ACTION_CAPTION, "caption");
		}
		if ("source".equals(verb) || "bind".equals(verb)) {
			return parseSource(parts, line);
		}
		if ("set".equals(verb)) {
			return parseSet(parts);
		}
		if ("tap".equals(verb)) {
			return parseRestText(parts, ACTION_TAP);
		}
		if ("swipe".equals(verb)) {
			return parseSwipe(parts);
		}
		if ("hold".equals(verb)) {
			return parseRestText(parts, ACTION_HOLD);
		}
		if ("warn".equals(verb)) {
			return parseWarn(parts);
		}
		if ("ime".equals(verb)) {
			return parseIme(parts);
		}
		return fail("Unknown subcommand '" + parts[0] + "'.");
	}

	public static String usage() {
		return "Usage: .widget list\n"
				+ "       .widget add <id> [hbar|vbar|ring|timer|bar|vertical|circle|countdown]\n"
				+ "       .widget remove|delete|rm <id>\n"
				+ "       .widget show|hide <id>\n"
				+ "       .widget shape <id> hbar|vbar|ring|timer\n"
				+ "       .widget color <id> <name|#RRGGBB>\n"
				+ "       .widget track <id> <name|#RRGGBB>\n"
				+ "       .widget opacity <id> <percent>\n"
				+ "       .widget size <id> <w> <h>\n"
				+ "       .widget move <id> <x> <y>\n"
				+ "       .widget label <id> [text]\n"
				+ "       .widget value <id> on|off\n"
				+ "       .widget caption|nametag <id> on|off\n"
				+ "       .widget source|bind <id> manual\n"
				+ "       .widget source|bind <id> gmcp|mcp|var <path> [maxPath]\n"
				+ "       .widget source|bind <id> timer <timerName>\n"
				+ "       .widget source|bind <id> regex <valueRegex> [maxRegex]\n"
				+ "           (quote regexes with \"...\" or '...' if they contain spaces;\n"
				+ "            group 1 is the number; two groups in valueRegex may be value/max)\n"
				+ "       .widget set <id> <value> [<max>]\n"
				+ "       .widget set <id> <value>/<max>\n"
				+ "       .widget tap <id> [command]\n"
				+ "       .widget swipe <id> up|down|left|right [command]\n"
				+ "       .widget hold <id> [command]\n"
				+ "           (stored; long-press enters edit and does not fire hold)\n"
				+ "       .widget warn <id> <percent> [color]\n"
				+ "       .widget warn <id> off\n"
				+ "       .widget ime <id> stay|hide|overlay\n";
	}

	private static Result parseList(final String[] parts) {
		if (parts.length != 1) {
			return fail("list takes no arguments.");
		}
		final Result r = new Result();
		r.action = ACTION_LIST;
		return r;
	}

	private static Result parseAdd(final String[] parts) {
		final Result r = start(parts, ACTION_ADD);
		if (r.error != null) {
			return r;
		}
		if (parts.length > 3) {
			return fail("add takes an id and an optional shape.");
		}
		if (parts.length == 3) {
			final String shape = normalizeShape(parts[2]);
			if (shape == null) {
				return fail("Unknown shape '" + parts[2]
						+ "' (hbar, vbar, ring, timer).");
			}
			r.shape = shape;
		} else {
			r.shape = SHAPE_HBAR;
		}
		return r;
	}

	private static Result parseRemove(final String[] parts) {
		final Result r = start(parts, ACTION_REMOVE);
		if (r.error != null) {
			return r;
		}
		if (parts.length != 2) {
			return fail("remove takes only an id.");
		}
		return r;
	}

	private static Result parseShowHide(final String[] parts,
			final boolean show) {
		final Result r = start(parts, show ? ACTION_SHOW : ACTION_HIDE);
		if (r.error != null) {
			return r;
		}
		if (parts.length != 2) {
			return fail((show ? "show" : "hide") + " takes only an id.");
		}
		r.flag = Boolean.valueOf(show);
		return r;
	}

	private static Result parseShape(final String[] parts) {
		final Result r = start(parts, ACTION_SHAPE);
		if (r.error != null) {
			return r;
		}
		if (parts.length != 3) {
			return fail("shape takes an id and a shape.");
		}
		final String shape = normalizeShape(parts[2]);
		if (shape == null) {
			return fail("Unknown shape '" + parts[2]
					+ "' (hbar, vbar, ring, timer).");
		}
		r.shape = shape;
		return r;
	}

	private static Result parseColor(final String[] parts,
			final String action) {
		final Result r = start(parts, action);
		if (r.error != null) {
			return r;
		}
		if (parts.length != 3) {
			return fail(action + " takes an id and a colour token.");
		}
		r.color = parts[2];
		return r;
	}

	private static Result parseOpacity(final String[] parts) {
		final Result r = start(parts, ACTION_OPACITY);
		if (r.error != null) {
			return r;
		}
		if (parts.length != 3) {
			return fail("opacity takes an id and a percent.");
		}
		final Integer n = parseInt(parts[2]);
		if (n == null) {
			return fail("opacity wants a number, not '" + parts[2] + "'.");
		}
		r.opacity = n;
		return r;
	}

	private static Result parseSize(final String[] parts) {
		final Result r = start(parts, ACTION_SIZE);
		if (r.error != null) {
			return r;
		}
		if (parts.length != 4) {
			return fail("size takes an id, width and height.");
		}
		final Integer w = parseInt(parts[2]);
		final Integer h = parseInt(parts[3]);
		if (w == null || h == null) {
			return fail("size wants two numbers (width height).");
		}
		r.w = w;
		r.h = h;
		return r;
	}

	private static Result parseMove(final String[] parts) {
		final Result r = start(parts, ACTION_MOVE);
		if (r.error != null) {
			return r;
		}
		if (parts.length != 4) {
			return fail("move takes an id, x and y.");
		}
		final Integer x = parseInt(parts[2]);
		final Integer y = parseInt(parts[3]);
		if (x == null || y == null) {
			return fail("move wants two numbers (x y).");
		}
		r.x = x;
		r.y = y;
		return r;
	}

	private static Result parseRestText(final String[] parts,
			final String action) {
		final Result r = start(parts, action);
		if (r.error != null) {
			return r;
		}
		r.text = joinFrom(parts, 2);
		return r;
	}

	private static Result parseOnOffFlag(final String[] parts,
			final String action, final String verb) {
		final Result r = start(parts, action);
		if (r.error != null) {
			return r;
		}
		if (parts.length != 3) {
			return fail(verb + " takes an id and on|off.");
		}
		final Boolean flag = parseOnOff(parts[2]);
		if (flag == null) {
			return fail(verb + " wants on or off, not '" + parts[2] + "'.");
		}
		r.flag = flag;
		return r;
	}

	private static Result parseSource(final String[] parts, final String line) {
		final Result r = start(parts, ACTION_SOURCE);
		if (r.error != null) {
			return r;
		}
		if (parts.length < 3) {
			return fail("source takes an id and manual|gmcp|mcp|var|timer|regex.");
		}
		final String source = normalizeSource(parts[2]);
		if (source == null) {
			return fail("Unknown source '" + parts[2]
					+ "' (manual, gmcp, mcp, var, timer, regex).");
		}
		r.source = source;
		if (SOURCE_MANUAL.equals(source)) {
			if (parts.length != 3) {
				return fail("source manual takes only an id.");
			}
			return r;
		}
		if (SOURCE_TIMER.equals(source)) {
			if (parts.length != 4) {
				return fail("source timer takes an id and a .timer name.");
			}
			r.path = parts[3];
			return r;
		}
		if (SOURCE_REGEX.equals(source)) {
			return parseRegexSource(r, remainderAfterTokens(line, 3));
		}
		if (parts.length < 4 || parts.length > 5) {
			return fail("source " + source
					+ " takes a path and an optional max path.");
		}
		r.path = parts[3];
		if (parts.length == 5) {
			r.maxPath = parts[4];
		}
		return r;
	}

	/**
	 * {@code .widget source <id> regex <valueRegex> [maxRegex]}. Quoted tokens
	 * ({@code "..."} or {@code '...'}) may contain spaces. Unquoted tokens are
	 * a single whitespace-separated word.
	 */
	private static Result parseRegexSource(final Result r, final String rest) {
		if (rest == null || rest.trim().length() == 0) {
			return fail("source regex takes a value regex and an optional max regex.");
		}
		final java.util.ArrayList<String> tokens = tokenizeQuoted(rest);
		if (tokens.size() < 1 || tokens.size() > 2) {
			return fail("source regex takes a value regex and an optional max regex.");
		}
		r.path = tokens.get(0);
		if (tokens.size() == 2) {
			r.maxPath = tokens.get(1);
		}
		return r;
	}

	private static Result parseSet(final String[] parts) {
		final Result r = start(parts, ACTION_SET);
		if (r.error != null) {
			return r;
		}
		if (parts.length == 3) {
			if (!fillValueMax(r, parts[2])) {
				return fail("set wants a number, or value/max.");
			}
			return r;
		}
		if (parts.length == 4) {
			final Double value = parseDouble(parts[2]);
			final Double max = parseDouble(parts[3]);
			if (value == null || max == null) {
				return fail("set wants two numbers (value max).");
			}
			r.value = value;
			r.max = max;
			return r;
		}
		return fail("set takes an id, a value, and an optional max.");
	}

	private static Result parseSwipe(final String[] parts) {
		final Result r = start(parts, ACTION_SWIPE);
		if (r.error != null) {
			return r;
		}
		if (parts.length < 3) {
			return fail("swipe takes an id and up|down|left|right.");
		}
		final String dir = normalizeSwipe(parts[2]);
		if (dir == null) {
			return fail("Unknown swipe direction '" + parts[2]
					+ "' (up, down, left, right).");
		}
		r.swipeDir = dir;
		r.text = joinFrom(parts, 3);
		return r;
	}

	private static Result parseWarn(final String[] parts) {
		final Result r = start(parts, ACTION_WARN);
		if (r.error != null) {
			return r;
		}
		if (parts.length < 3 || parts.length > 4) {
			return fail("warn takes an id and a percent, or off.");
		}
		if (parts.length == 3 && isOffWord(parts[2])) {
			r.warnPct = Integer.valueOf(0);
			r.flag = Boolean.FALSE;
			return r;
		}
		final Integer pct = parseInt(parts[2]);
		if (pct == null) {
			return fail("warn wants a percent, or off.");
		}
		r.warnPct = pct;
		if (parts.length == 4) {
			r.color = parts[3];
		}
		return r;
	}

	private static Result parseIme(final String[] parts) {
		final Result r = start(parts, ACTION_IME);
		if (r.error != null) {
			return r;
		}
		if (parts.length != 3) {
			return fail("ime takes an id and stay|hide|overlay.");
		}
		final String ime = normalizeIme(parts[2]);
		if (ime == null) {
			return fail("Unknown ime mode '" + parts[2]
					+ "' (stay, hide, overlay).");
		}
		r.ime = ime;
		return r;
	}

	/**
	 * Require a widget id in {@code parts[1]}. On failure {@link Result#error}
	 * is set; on success {@link Result#action} and {@link Result#id} are set.
	 */
	private static Result start(final String[] parts, final String action) {
		if (parts.length < 2) {
			return fail("Missing widget id.");
		}
		final String id = normalizeId(parts[1]);
		if (id == null) {
			return fail("Invalid widget id '" + parts[1] + "'.");
		}
		final Result r = new Result();
		r.action = action;
		r.id = id;
		return r;
	}

	private static Result helpResult() {
		final Result r = new Result();
		r.action = ACTION_HELP;
		r.note = usage();
		return r;
	}

	private static Result fail(final String reason) {
		final Result r = new Result();
		r.error = reason + "\n" + usage();
		return r;
	}

	/**
	 * Trim, and drop a leading {@code .widget} command token so tests can pass
	 * either the player line or the argument {@code SpecialCommand} would see.
	 */
	private static String stripCommandPrefix(final String arg) {
		if (arg == null) {
			return "";
		}
		String s = arg.trim();
		if (s.length() >= 7 && s.regionMatches(true, 0, ".widget", 0, 7)) {
			if (s.length() == 7) {
				return "";
			}
			if (Character.isWhitespace(s.charAt(7))) {
				s = s.substring(7).trim();
			}
		}
		return s;
	}

	/**
	 * Lowercase {@code [a-z0-9_]{1,24}}. Rejects the extra-text reserved names
	 * so {@code .widget add main} fails here rather than at apply time.
	 */
	static String normalizeId(final String raw) {
		if (raw == null) {
			return null;
		}
		final String n = raw.trim().toLowerCase(Locale.US);
		if (!ID_PATTERN.matcher(n).matches()) {
			return null;
		}
		if ("main".equals(n) || "maindisplay".equals(n)
				|| "button_window".equals(n)) {
			return null;
		}
		return n;
	}

	static String normalizeShape(final String raw) {
		if (raw == null) {
			return null;
		}
		final String s = raw.trim().toLowerCase(Locale.US);
		if ("hbar".equals(s) || "bar".equals(s) || "horizontal".equals(s)
				|| "horiz".equals(s)) {
			return GaugeWidget.Shape.HBAR.toJsonValue();
		}
		if ("vbar".equals(s) || "vertical".equals(s) || "vert".equals(s)) {
			return GaugeWidget.Shape.VBAR.toJsonValue();
		}
		if ("ring".equals(s) || "circle".equals(s) || "pie".equals(s)
				|| "zelda".equals(s)) {
			return GaugeWidget.Shape.RING.toJsonValue();
		}
		if ("timer".equals(s) || "countdown".equals(s)) {
			return GaugeWidget.Shape.TIMER.toJsonValue();
		}
		return null;
	}

	static String normalizeSource(final String raw) {
		if (raw == null) {
			return null;
		}
		final String s = raw.trim().toLowerCase(Locale.US);
		for (GaugeWidget.Source src : GaugeWidget.Source.values()) {
			if (src.toJsonValue().equals(s)) {
				return s;
			}
		}
		return null;
	}

	static String normalizeIme(final String raw) {
		if (raw == null) {
			return null;
		}
		final String s = raw.trim().toLowerCase(Locale.US);
		if ("stay".equals(s) || "game".equals(s)) {
			return GaugeWidget.ImeMode.STAY.toJsonValue();
		}
		if ("hide".equals(s) || "off".equals(s)) {
			return GaugeWidget.ImeMode.HIDE.toJsonValue();
		}
		if ("overlay".equals(s) || "over".equals(s) || "float".equals(s)
				|| "keyboard".equals(s)) {
			return GaugeWidget.ImeMode.OVERLAY.toJsonValue();
		}
		return null;
	}

	static String normalizeSwipe(final String raw) {
		if (raw == null) {
			return null;
		}
		final String s = raw.trim().toLowerCase(Locale.US);
		if (SWIPE_UP.equals(s) || SWIPE_DOWN.equals(s)
				|| SWIPE_LEFT.equals(s) || SWIPE_RIGHT.equals(s)) {
			return s;
		}
		return null;
	}

	static Boolean parseOnOff(final String token) {
		if (token == null) {
			return null;
		}
		final String s = token.trim().toLowerCase(Locale.US);
		if ("on".equals(s) || "true".equals(s) || "1".equals(s)
				|| "yes".equals(s)) {
			return Boolean.TRUE;
		}
		if ("off".equals(s) || "false".equals(s) || "0".equals(s)
				|| "no".equals(s)) {
			return Boolean.FALSE;
		}
		return null;
	}

	private static boolean isOffWord(final String token) {
		final Boolean v = parseOnOff(token);
		return v != null && !v.booleanValue();
	}

	static Integer parseInt(final String token) {
		if (token == null || token.length() == 0) {
			return null;
		}
		try {
			return Integer.valueOf(Integer.parseInt(token));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	static Double parseDouble(final String token) {
		if (token == null || token.length() == 0) {
			return null;
		}
		try {
			return Double.valueOf(Double.parseDouble(token));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static boolean fillValueMax(final Result r, final String token) {
		final int slash = token.indexOf('/');
		if (slash < 0) {
			final Double v = parseDouble(token);
			if (v == null) {
				return false;
			}
			r.value = v;
			return true;
		}
		final String a = token.substring(0, slash).trim();
		final String b = token.substring(slash + 1).trim();
		if (a.length() == 0 || b.length() == 0) {
			return false;
		}
		final Double v = parseDouble(a);
		final Double m = parseDouble(b);
		if (v == null || m == null) {
			return false;
		}
		r.value = v;
		r.max = m;
		return true;
	}

	private static String joinFrom(final String[] parts, final int from) {
		if (parts.length <= from) {
			return "";
		}
		final StringBuilder sb = new StringBuilder(parts[from]);
		for (int i = from + 1; i < parts.length; i++) {
			sb.append(' ').append(parts[i]);
		}
		return sb.toString();
	}

	/**
	 * Text after the first {@code tokenCount} whitespace-separated tokens of
	 * {@code line} (quotes are not special here — used to skip
	 * {@code source <id> regex}).
	 */
	static String remainderAfterTokens(final String line, final int tokenCount) {
		if (line == null || tokenCount < 0) {
			return "";
		}
		int i = 0;
		final int n = line.length();
		int seen = 0;
		while (i < n && seen < tokenCount) {
			while (i < n && Character.isWhitespace(line.charAt(i))) {
				i++;
			}
			if (i >= n) {
				break;
			}
			while (i < n && !Character.isWhitespace(line.charAt(i))) {
				i++;
			}
			seen++;
		}
		while (i < n && Character.isWhitespace(line.charAt(i))) {
			i++;
		}
		return i < n ? line.substring(i) : "";
	}

	/**
	 * Split {@code rest} into tokens. {@code "double"} or {@code 'single'}
	 * quoted spans may contain spaces (no escape sequences). Unquoted tokens
	 * end at whitespace. Unclosed quotes take the remainder of the string.
	 */
	static java.util.ArrayList<String> tokenizeQuoted(final String rest) {
		final java.util.ArrayList<String> out = new java.util.ArrayList<String>();
		if (rest == null) {
			return out;
		}
		int i = 0;
		final int n = rest.length();
		while (i < n) {
			while (i < n && Character.isWhitespace(rest.charAt(i))) {
				i++;
			}
			if (i >= n) {
				break;
			}
			char c = rest.charAt(i);
			if (c == '"' || c == '\'') {
				char q = c;
				i++;
				int start = i;
				while (i < n && rest.charAt(i) != q) {
					i++;
				}
				out.add(rest.substring(start, i));
				if (i < n) {
					i++;
				}
			} else {
				int start = i;
				while (i < n && !Character.isWhitespace(rest.charAt(i))) {
					i++;
				}
				out.add(rest.substring(start, i));
			}
		}
		return out;
	}
}
