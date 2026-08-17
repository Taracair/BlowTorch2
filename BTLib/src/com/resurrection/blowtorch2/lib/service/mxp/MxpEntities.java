package com.resurrection.blowtorch2.lib.service.mxp;

import java.util.HashMap;

/**
 * Built-in HTML entities plus MUD-defined ones. Names are case-sensitive.
 * Numeric {@code &#nnn;} below 32 is ignored per the MXP spec.
 */
public final class MxpEntities {

	private final HashMap<String, Entity> defined = new HashMap<String, Entity>();

	public MxpEntities() {
		seedHtml();
	}

	public void reset() {
		defined.clear();
		seedHtml();
	}

	public void define(final String name, final String value, final boolean publish) {
		if (name == null || name.length() == 0) {
			return;
		}
		defined.put(name, new Entity(value == null ? "" : value, publish));
	}

	public void delete(final String name) {
		if (name != null) {
			defined.remove(name);
		}
	}

	public void addToList(final String name, final String item) {
		Entity e = defined.get(name);
		String cur = e == null ? "" : e.value;
		if (cur.length() == 0) {
			define(name, item, e == null || e.publish);
			return;
		}
		define(name, cur + "|" + item, e.publish);
	}

	public void removeFromList(final String name, final String item) {
		Entity e = defined.get(name);
		if (e == null || item == null) {
			return;
		}
		String[] parts = e.value.split("\\|", -1);
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < parts.length; i++) {
			if (parts[i].equals(item)) {
				continue;
			}
			if (sb.length() > 0) {
				sb.append('|');
			}
			sb.append(parts[i]);
		}
		define(name, sb.toString(), e.publish);
	}

	/**
	 * @return replacement, empty string for ignored numeric, or null to leave
	 *         the {@code &...;} as-is (malformed / unknown).
	 */
	public String expand(final String name) {
		if (name == null || name.length() == 0) {
			return null;
		}
		if (name.charAt(0) == '#') {
			return numeric(name.substring(1));
		}
		Entity e = defined.get(name);
		return e == null ? null : e.value;
	}

	public String get(final String name) {
		Entity e = defined.get(name);
		return e == null ? null : e.value;
	}

	private static String numeric(final String body) {
		if (body.length() == 0) {
			return null;
		}
		try {
			int n;
			if (body.charAt(0) == 'x' || body.charAt(0) == 'X') {
				n = Integer.parseInt(body.substring(1), 16);
			} else {
				n = Integer.parseInt(body);
			}
			if (n < 32) {
				return "";
			}
			if (n > 0x10FFFF) {
				return null;
			}
			return new String(Character.toChars(n));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private void seedHtml() {
		put("lt", "<");
		put("gt", ">");
		put("amp", "&");
		put("quot", "\"");
		put("apos", "'");
		put("nbsp", "\u00A0");
		put("copy", "\u00A9");
		put("reg", "\u00AE");
		put("trade", "\u2122");
		put("ndash", "\u2013");
		put("mdash", "\u2014");
		put("lsquo", "\u2018");
		put("rsquo", "\u2019");
		put("ldquo", "\u201C");
		put("rdquo", "\u201D");
		put("bull", "\u2022");
		put("hellip", "\u2026");
		put("times", "\u00D7");
		put("divide", "\u00F7");
		put("deg", "\u00B0");
		put("plusmn", "\u00B1");
		put("laquo", "\u00AB");
		put("raquo", "\u00BB");
		put("iexcl", "\u00A1");
		put("cent", "\u00A2");
		put("pound", "\u00A3");
		put("yen", "\u00A5");
		put("sect", "\u00A7");
		put("uml", "\u00A8");
		put("not", "\u00AC");
		put("shy", "\u00AD");
		put("macr", "\u00AF");
		put("acute", "\u00B4");
		put("micro", "\u00B5");
		put("para", "\u00B6");
		put("middot", "\u00B7");
		put("cedil", "\u00B8");
		put("sup1", "\u00B9");
		put("sup2", "\u00B2");
		put("sup3", "\u00B3");
		put("frac14", "\u00BC");
		put("frac12", "\u00BD");
		put("frac34", "\u00BE");
		put("iquest", "\u00BF");
		put("Agrave", "\u00C0");
		put("Aacute", "\u00C1");
		put("Acirc", "\u00C2");
		put("Atilde", "\u00C3");
		put("Auml", "\u00C4");
		put("Aring", "\u00C5");
		put("AElig", "\u00C6");
		put("Ccedil", "\u00C7");
		put("Egrave", "\u00C8");
		put("Eacute", "\u00C9");
		put("Ecirc", "\u00CA");
		put("Euml", "\u00CB");
		put("Igrave", "\u00CC");
		put("Iacute", "\u00CD");
		put("Icirc", "\u00CE");
		put("Iuml", "\u00CF");
		put("ETH", "\u00D0");
		put("Ntilde", "\u00D1");
		put("Ograve", "\u00D2");
		put("Oacute", "\u00D3");
		put("Ocirc", "\u00D4");
		put("Otilde", "\u00D5");
		put("Ouml", "\u00D6");
		put("Oslash", "\u00D8");
		put("Ugrave", "\u00D9");
		put("Uacute", "\u00DA");
		put("Ucirc", "\u00DB");
		put("Uuml", "\u00DC");
		put("Yacute", "\u00DD");
		put("THORN", "\u00DE");
		put("szlig", "\u00DF");
		put("agrave", "\u00E0");
		put("aacute", "\u00E1");
		put("acirc", "\u00E2");
		put("atilde", "\u00E3");
		put("auml", "\u00E4");
		put("aring", "\u00E5");
		put("aelig", "\u00E6");
		put("ccedil", "\u00E7");
		put("egrave", "\u00E8");
		put("eacute", "\u00E9");
		put("ecirc", "\u00EA");
		put("euml", "\u00EB");
		put("igrave", "\u00EC");
		put("iacute", "\u00ED");
		put("icirc", "\u00EE");
		put("iuml", "\u00EF");
		put("eth", "\u00F0");
		put("ntilde", "\u00F1");
		put("ograve", "\u00F2");
		put("oacute", "\u00F3");
		put("ocirc", "\u00F4");
		put("otilde", "\u00F5");
		put("ouml", "\u00F6");
		put("oslash", "\u00F8");
		put("ugrave", "\u00F9");
		put("uacute", "\u00FA");
		put("ucirc", "\u00FB");
		put("uuml", "\u00FC");
		put("yacute", "\u00FD");
		put("thorn", "\u00FE");
		put("yuml", "\u00FF");
	}

	private void put(final String name, final String value) {
		defined.put(name, new Entity(value, false));
	}

	private static final class Entity {
		final String value;
		final boolean publish;

		Entity(final String value, final boolean publish) {
			this.value = value;
			this.publish = publish;
		}
	}
}
