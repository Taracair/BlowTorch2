package com.resurrection.blowtorch2.lib.trigger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.resurrection.blowtorch2.lib.alias.AliasData;

/**
 * Paste an alias body into a trigger pattern: the whole pattern is the alias
 * name, or {@code $alias{name}} inside a longer one. Refused references stay as
 * written. One expansion level. Exact name match only — {@code ^Ch$} is literal.
 */
public final class TriggerAliasReference {

	/** {@code $alias{name}}. Braces are required, so {@code $1} is never touched. */
	private static final Pattern REFERENCE = Pattern.compile("\\$alias\\{([^{}]*)\\}");

	/** A typed capture, {@code $1} .. {@code $9}, which only an alias can fill. */
	private static final Pattern TYPED_CAPTURE = Pattern.compile("\\$[1-9]");

	private TriggerAliasReference() {
	}

	/** @return true when {@code pattern} contains a {@code $alias&#123;…&#125;}. */
	public static boolean isReferencedIn(final String pattern) {
		return pattern != null && REFERENCE.matcher(pattern).find();
	}

	/**
	 * The alias a whole pattern names, or null.
	 *
	 * <p>Exact match on the trimmed pattern. Anything else -- a name with a
	 * word beside it, an anchor around it -- is a pattern of its own and is
	 * left alone, which is also the way out for a player who wants the literal
	 * text of a name.
	 *
	 * @param pattern The trigger's pattern as the player wrote it.
	 * @param bodies Name to body, from {@link #bodies}.
	 * @return The name, whether or not its body can be used; null when the
	 *     pattern is not a name at all.
	 */
	public static String wholePatternAlias(final String pattern, final Map<String, String> bodies) {
		if (pattern == null || bodies == null) {
			return null;
		}
		String name = pattern.trim();
		if (name.length() == 0) {
			return null;
		}
		return bodies.containsKey(name) ? name : null;
	}

	/**
	 * The alias bodies a trigger pattern can name, keyed by the name a player
	 * would write.
	 *
	 * <p>Aliases are stored under their {@code pre} text, and a {@code pre} may
	 * carry the {@code ^} and {@code $} anchors the alias editor's checkboxes
	 * add. Those are part of how the alias matches a typed line, not part of
	 * its name, so they are stripped: an alias shown as {@code ^gfbb} is
	 * written {@code $alias&#123;gfbb&#125;}.
	 *
	 * <p>The alias's enabled flag is not consulted. Here the alias is being
	 * used as a piece of named text, and disabling it stops it expanding what
	 * you type -- it should not also silently stop a trigger matching.
	 *
	 * @param aliases The alias map as the settings hold it. May be null.
	 * @return Name to body. Never null; empty when there are no aliases.
	 */
	public static Map<String, String> bodies(final Map<String, AliasData> aliases) {
		Map<String, String> out = new HashMap<String, String>();
		if (aliases == null) {
			return out;
		}
		for (Map.Entry<String, AliasData> e : aliases.entrySet()) {
			AliasData a = e.getValue();
			if (a == null) {
				continue;
			}
			String name = a.getPre() != null ? a.getPre() : e.getKey();
			if (name == null) {
				continue;
			}
			if (name.startsWith("^")) {
				name = name.substring(1);
			}
			if (name.endsWith("$")) {
				name = name.substring(0, name.length() - 1);
			}
			if (name.length() == 0) {
				continue;
			}
			out.put(name, a.getPost() != null ? a.getPost() : "");
		}
		return out;
	}

	/**
	 * Paste in the body of every alias the pattern names and can use.
	 *
	 * @param pattern The trigger's pattern as the player wrote it. May be null.
	 * @param bodies Name to body, from {@link #bodies}. May be null.
	 * @return The pattern with usable references replaced. The same string
	 *     object when there was nothing to replace, so a caller can tell that
	 *     nothing changed without comparing.
	 */
	public static String resolve(final String pattern, final Map<String, String> bodies) {
		if (pattern == null || pattern.length() == 0) {
			return pattern;
		}
		String whole = wholePatternAlias(pattern, bodies);
		if (whole != null) {
			String body = usableBody(whole, bodies);
			// A refused body leaves the pattern as the name, which is what it
			// was: the trigger then watches for those letters, and the editor
			// says why it could not do better.
			return body != null ? body : pattern;
		}
		Matcher m = REFERENCE.matcher(pattern);
		if (!m.find()) {
			return pattern;
		}
		StringBuffer out = new StringBuffer();
		boolean changed = false;
		do {
			String body = usableBody(m.group(1), bodies);
			if (body == null) {
				// Left as written, so the editor and the game window both show
				// the player the reference that did not resolve.
				m.appendReplacement(out, Matcher.quoteReplacement(m.group(0)));
			} else {
				m.appendReplacement(out, Matcher.quoteReplacement(body));
				changed = true;
			}
		} while (m.find());
		m.appendTail(out);
		return changed ? out.toString() : pattern;
	}

	/**
	 * What is wrong with each reference in the pattern, for the trigger editor.
	 *
	 * @param pattern The trigger's pattern as the player wrote it.
	 * @param bodies Name to body, from {@link #bodies}.
	 * @return One line per reference, resolved or not; empty when the pattern
	 *     names no alias.
	 */
	public static List<String> explain(final String pattern, final Map<String, String> bodies) {
		List<String> out = new ArrayList<String>();
		if (pattern == null) {
			return out;
		}
		String whole = wholePatternAlias(pattern, bodies);
		if (whole != null) {
			out.add(describe(whole, bodies.get(whole), "the pattern stays the name «"
					+ whole + "», which is text the game is unlikely to print"));
			return out;
		}
		Matcher m = REFERENCE.matcher(pattern);
		while (m.find()) {
			String name = m.group(1);
			String raw = bodies == null ? null : bodies.get(name);
			if (raw == null) {
				out.add("No alias called «" + name + "», so $alias{" + name
						+ "} is left as written and this trigger will not fire.");
			} else {
				out.add(describe(name, raw, "$alias{" + name + "} is left as written"));
			}
		}
		return out;
	}

	/**
	 * One line for the editor about one alias: what it gives the trigger, or
	 * which of the four refusals applies and what happens instead.
	 *
	 * @param name The alias's name.
	 * @param body Its text. Never null here.
	 * @param consequence What the pattern does when the body is refused,
	 *     phrased for the form the player used.
	 * @return The line to show.
	 */
	private static String describe(final String name, final String body, final String consequence) {
		if (body == null || body.length() == 0) {
			return "Alias «" + name + "» has no text, so " + consequence + ".";
		}
		if (body.indexOf(';') >= 0) {
			return "Alias «" + name + "» is several commands (" + body
					+ "), which is not one piece of text the game can print, so "
					+ consequence + ".";
		}
		if (TYPED_CAPTURE.matcher(body).find()) {
			return "Alias «" + name + "» uses $1-style captures from what you type ("
					+ body + "), which a trigger has nothing to fill from, so "
					+ consequence + ".";
		}
		if (isReferencedIn(body)) {
			return "Alias «" + name + "» names another alias (" + body
					+ "), which is one level too deep, so " + consequence + ".";
		}
		return "Found alias «" + name + "»: watching for its text «" + body
				+ "» instead of the name.";
	}

	/**
	 * The body of a named alias, or null when it cannot stand in a pattern.
	 *
	 * @param name The name inside the braces.
	 * @param bodies Name to body.
	 * @return The body to paste in, or null to leave the reference alone.
	 */
	private static String usableBody(final String name, final Map<String, String> bodies) {
		if (name == null || bodies == null) {
			return null;
		}
		String body = bodies.get(name);
		if (body == null || body.length() == 0) {
			return null;
		}
		if (body.indexOf(';') >= 0) {
			return null;
		}
		if (TYPED_CAPTURE.matcher(body).find()) {
			return null;
		}
		if (isReferencedIn(body)) {
			return null;
		}
		return body;
	}
}
