package com.resurrection.blowtorch2.lib.trigger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.resurrection.blowtorch2.lib.alias.AliasData;

/**
 * An alias named inside a trigger's pattern: the {@code $alias{name}} form.
 *
 * <p>An alias and a trigger face opposite ways. An alias expands a line the
 * player <em>types</em>; a trigger matches a line the game <em>sends</em>. So
 * writing an alias's name in the pattern field, which is what the maintainer
 * tried, produces a trigger that sits waiting for the game to print the letters
 * {@code _tappable1}. This class is the bridge that was missing, and it is
 * deliberately a narrow one: the alias's body is pasted into the pattern as
 * text, once, when the trigger system is built. Nothing about the alias's own
 * behaviour comes along.
 *
 * <p>The reference is explicit rather than "a pattern that happens to be an
 * alias name". A profile here has aliases called {@code Ch}, {@code c0rpse} and
 * {@code 4cont}; making a bare name expand would silently change what an
 * existing trigger of that pattern watches for, and would make it impossible to
 * write a trigger on that literal text at all.
 *
 * <p>Three kinds of reference are refused, and a refused reference is left in
 * the pattern exactly as written -- the same choice
 * {@code VariableSubstitution} makes for an unset variable, and for the same
 * reason: a trigger that visibly watches for {@code $alias{x}} and never fires
 * is easier to understand than one that quietly watches for something else.
 *
 * <ul>
 * <li>No alias of that name.
 * <li>A body that is several commands ({@code a;b}) or wants typed captures
 *     ({@code get $1 from bag}). Neither is one piece of text the game could
 *     print.
 * <li>A body naming another alias. One level only, so a pair of aliases
 *     naming each other cannot loop.
 * </ul>
 */
public final class TriggerAliasReference {

	/** {@code $alias{name}}. Braces are required, so {@code $1} is never touched. */
	private static final Pattern REFERENCE = Pattern.compile("\\$alias\\{([^{}]*)\\}");

	/** A typed capture, {@code $1} .. {@code $9}, which only an alias can fill. */
	private static final Pattern TYPED_CAPTURE = Pattern.compile("\\$[1-9]");

	private TriggerAliasReference() {
	}

	/** @return true when {@code pattern} names at least one alias. */
	public static boolean isReferencedIn(final String pattern) {
		return pattern != null && REFERENCE.matcher(pattern).find();
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
		Matcher m = REFERENCE.matcher(pattern);
		while (m.find()) {
			String name = m.group(1);
			String raw = bodies == null ? null : bodies.get(name);
			if (raw == null) {
				out.add("No alias called «" + name + "», so $alias{" + name
						+ "} is left as written and this trigger will not fire.");
			} else if (raw.indexOf(';') >= 0) {
				out.add("Alias «" + name + "» is several commands (" + raw
						+ "), which is not one piece of text the game can print."
						+ " $alias{" + name + "} is left as written.");
			} else if (TYPED_CAPTURE.matcher(raw).find()) {
				out.add("Alias «" + name + "» uses $1-style captures from what you type ("
						+ raw + "), which a trigger has nothing to fill from."
						+ " $alias{" + name + "} is left as written.");
			} else if (isReferencedIn(raw)) {
				out.add("Alias «" + name + "» names another alias (" + raw
						+ "), which is one level too deep. $alias{" + name
						+ "} is left as written.");
			} else {
				out.add("Reads alias «" + name + "» → watches for: " + raw);
			}
		}
		return out;
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
