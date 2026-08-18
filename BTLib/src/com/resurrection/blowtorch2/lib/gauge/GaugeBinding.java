/*
 * Copyright (C) BlowTorch contributors
 */
package com.resurrection.blowtorch2.lib.gauge;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Pure number extraction for gauge sources. No Android, no Connection, no GMCP
 * types — callers pass module name + body JSON string.
 */
public final class GaugeBinding {

	private GaugeBinding() {
	}

	/**
	 * Parse a number from a string. Accepts int, float, and {@code "80/100"}
	 * (uses 80). Null / blank / unparseable → null.
	 */
	public static Double parseNumber(final String raw) {
		if (raw == null) {
			return null;
		}
		String s = raw.trim();
		if (s.length() == 0) {
			return null;
		}
		int slash = s.indexOf('/');
		if (slash >= 0) {
			return parsePlainNumber(s.substring(0, slash));
		}
		return parsePlainNumber(s);
	}

	/**
	 * Parse {@code value/max} (e.g. {@code "80/100"}). Null if there is no slash
	 * or either side is not a number.
	 *
	 * @return {@code double[2]} of value, max, or null
	 */
	public static double[] parsePair(final String raw) {
		if (raw == null) {
			return null;
		}
		String s = raw.trim();
		int slash = s.indexOf('/');
		if (slash < 0) {
			return null;
		}
		Double v = parsePlainNumber(s.substring(0, slash));
		Double m = parsePlainNumber(s.substring(slash + 1));
		if (v == null || m == null) {
			return null;
		}
		return new double[] { v.doubleValue(), m.doubleValue() };
	}

	/**
	 * Walk {@code bodyJson} for {@code dottedPath}. If {@code dottedPath} starts
	 * with {@code module} (case-insensitive, boundary at {@code .} or end), that
	 * prefix is stripped and the remaining keys walk the object. A path of just
	 * {@code hp} looks up the top-level key {@code hp}.
	 */
	public static Double numberFromGmcpJson(final String module, final String bodyJson,
			final String dottedPath) {
		if (bodyJson == null || dottedPath == null) {
			return null;
		}
		String path = dottedPath.trim();
		if (path.length() == 0) {
			return null;
		}
		String remaining = stripModulePrefix(module, path);
		if (remaining.length() == 0) {
			return null;
		}
		try {
			JSONObject root = new JSONObject(bodyJson);
			String[] keys = remaining.split("\\.");
			Object cur = root;
			for (int i = 0; i < keys.length; i++) {
				if (keys[i].length() == 0) {
					return null;
				}
				if (!(cur instanceof JSONObject)) {
					return null;
				}
				JSONObject jo = (JSONObject) cur;
				if (!jo.has(keys[i])) {
					return null;
				}
				cur = jo.get(keys[i]);
			}
			return numberFromObject(cur);
		} catch (JSONException e) {
			return null;
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Compile a regex. Invalid / blank → null (caller skips that widget).
	 */
	public static Pattern compileRegex(final String regex) {
		if (regex == null) {
			return null;
		}
		String s = regex.trim();
		if (s.length() == 0) {
			return null;
		}
		try {
			return Pattern.compile(s);
		} catch (PatternSyntaxException e) {
			return null;
		} catch (RuntimeException e) {
			return null;
		}
	}

	/**
	 * Numbers from a finished, decolorized game line. {@code valueRegex} group 1
	 * is the current value ({@link #parseNumber}; non-numeric ignored). If
	 * {@code maxRegex} is non-empty, its group 1 is max. If {@code maxRegex} is
	 * empty and {@code valueRegex} has two capturing groups, group 2 may be max.
	 *
	 * <p>Uses a <em>local</em> Matcher (never store one on the widget). Invalid
	 * patterns return null rather than throwing.
	 *
	 * @return {@code double[]{value}} or {@code double[]{value, max}}, or null
	 */
	public static double[] numbersFromRegexLine(final String line, final String valueRegex,
			final String maxRegex) {
		if (line == null || valueRegex == null) {
			return null;
		}
		Pattern valuePat = compileRegex(valueRegex);
		if (valuePat == null) {
			return null;
		}
		Matcher m = valuePat.matcher(line);
		if (!m.find() || m.groupCount() < 1) {
			return null;
		}
		Double v = parseNumber(m.group(1));
		if (v == null) {
			return null;
		}
		Double max = null;
		if (maxRegex != null && maxRegex.trim().length() > 0) {
			Pattern maxPat = compileRegex(maxRegex);
			if (maxPat != null) {
				Matcher mm = maxPat.matcher(line);
				if (mm.find() && mm.groupCount() >= 1) {
					max = parseNumber(mm.group(1));
				}
			}
		} else if (m.groupCount() >= 2) {
			max = parseNumber(m.group(2));
		}
		if (max != null) {
			return new double[] { v.doubleValue(), max.doubleValue() };
		}
		return new double[] { v.doubleValue() };
	}

	/** Integer / Long / Double / Float / String → number; else null. */
	public static Double numberFromObject(final Object o) {
		if (o == null || o == JSONObject.NULL) {
			return null;
		}
		if (o instanceof Number) {
			return Double.valueOf(((Number) o).doubleValue());
		}
		if (o instanceof String) {
			return parseNumber((String) o);
		}
		return null;
	}

	static String stripModulePrefix(final String module, final String dottedPath) {
		String path = dottedPath.trim();
		if (module == null) {
			return path;
		}
		String mod = module.trim();
		if (mod.length() == 0) {
			return path;
		}
		if (path.length() >= mod.length()
				&& path.regionMatches(true, 0, mod, 0, mod.length())) {
			if (path.length() == mod.length()) {
				return "";
			}
			if (path.charAt(mod.length()) == '.') {
				return path.substring(mod.length() + 1);
			}
		}
		return path;
	}

	private static Double parsePlainNumber(final String raw) {
		if (raw == null) {
			return null;
		}
		String s = raw.trim();
		if (s.length() == 0) {
			return null;
		}
		try {
			return Double.valueOf(s);
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
