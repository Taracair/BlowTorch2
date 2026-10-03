package com.resurrection.blowtorch2.lib.window;

import java.util.Locale;

/**
 * When the input bar may draw a hyphen inside a long word, and which
 * dictionary to ask. The hyphen is not a character: send, copy, history
 * and suggestions still see the whole word.
 */
public final class InputHyphenation {

	private InputHyphenation() {
	}

	/** English patterns. Default. */
	public static final int LANG_EN = 0;
	/** Polish patterns. */
	public static final int LANG_PL = 1;
	/** Whatever language the phone is using. */
	public static final int LANG_PHONE = 2;

	public enum Frequency {
		NONE, NORMAL, FULL
	}

	public enum Action {
		STATUS, ENABLED, LANG, FULL, BAD
	}

	public static final class Parsed {
		public final Action action;
		public final boolean on;
		public final int lang;

		Parsed(final Action action, final boolean on, final int lang) {
			this.action = action;
			this.on = on;
			this.lang = lang;
		}
	}

	public static int clampLang(final int lang) {
		if (lang == LANG_PL || lang == LANG_PHONE) {
			return lang;
		}
		return LANG_EN;
	}

	/**
	 * Letters required on the line before a drawn hyphen. Sparing needs a
	 * real piece; full allows a short one.
	 */
	public static int minLead(final Frequency frequency) {
		return frequency == Frequency.FULL ? 2 : 5;
	}

	/** Letters left on the next line after a drawn hyphen. */
	public static int minTail(final Frequency frequency) {
		return frequency == Frequency.FULL ? 2 : 3;
	}

	/**
	 * A password line and a single-line bar do not break. Otherwise the
	 * player picks sparing ({@link Frequency#NORMAL}) or denser
	 * ({@link Frequency#FULL}).
	 */
	public static Frequency frequency(final boolean enabled, final boolean grow,
			final boolean password, final boolean full) {
		if (!enabled || !grow || password) {
			return Frequency.NONE;
		}
		return full ? Frequency.FULL : Frequency.NORMAL;
	}

	/**
	 * Language to force on the field, or null to leave the phone locale.
	 * One dictionary for the whole line.
	 */
	public static String localeTag(final Frequency frequency, final int lang) {
		if (frequency == Frequency.NONE) {
			return null;
		}
		int clamped = clampLang(lang);
		if (clamped == LANG_PL) {
			return "pl";
		}
		if (clamped == LANG_PHONE) {
			return null;
		}
		return "en";
	}

	/**
	 * Hyphenation is ignored when the break strategy is simple. Only then
	 * does the field need the high-quality strategy, and only while a
	 * dictionary is in use.
	 */
	public static boolean forceHighQualityBreak(final Frequency frequency,
			final boolean strategyIsSimple) {
		return frequency != Frequency.NONE && strategyIsSimple;
	}

	public static Parsed parse(final String raw) {
		String arg = raw == null ? "" : raw.trim().toLowerCase(Locale.US);
		if (arg.length() == 0) {
			return new Parsed(Action.STATUS, false, LANG_EN);
		}
		String[] parts = arg.split("\\s+");
		if (parts.length == 1) {
			Boolean on = onOff(parts[0]);
			if (on != null) {
				return new Parsed(Action.ENABLED, on.booleanValue(), LANG_EN);
			}
			Integer lang = langToken(parts[0]);
			if (lang != null) {
				return new Parsed(Action.LANG, false, lang.intValue());
			}
			return new Parsed(Action.BAD, false, LANG_EN);
		}
		if (parts.length == 2 && "lang".equals(parts[0])) {
			Integer lang = langToken(parts[1]);
			if (lang == null) {
				return new Parsed(Action.BAD, false, LANG_EN);
			}
			return new Parsed(Action.LANG, false, lang.intValue());
		}
		if (parts.length == 2 && "full".equals(parts[0])) {
			Boolean on = onOff(parts[1]);
			if (on == null) {
				return new Parsed(Action.BAD, false, LANG_EN);
			}
			return new Parsed(Action.FULL, on.booleanValue(), LANG_EN);
		}
		return new Parsed(Action.BAD, false, LANG_EN);
	}

	public static String languageName(final int lang) {
		switch (clampLang(lang)) {
		case LANG_PL:
			return "Polish";
		case LANG_PHONE:
			return "the phone language";
		default:
			return "English";
		}
	}

	private static Integer langToken(final String token) {
		if ("en".equals(token) || "english".equals(token)) {
			return Integer.valueOf(LANG_EN);
		}
		if ("pl".equals(token) || "polish".equals(token)) {
			return Integer.valueOf(LANG_PL);
		}
		if ("phone".equals(token) || "device".equals(token)) {
			return Integer.valueOf(LANG_PHONE);
		}
		return null;
	}

	private static Boolean onOff(final String token) {
		if (token == null) {
			return null;
		}
		if ("on".equals(token) || "true".equals(token) || "1".equals(token)
				|| "yes".equals(token)) {
			return Boolean.TRUE;
		}
		if ("off".equals(token) || "false".equals(token) || "0".equals(token)
				|| "no".equals(token)) {
			return Boolean.FALSE;
		}
		return null;
	}
}
