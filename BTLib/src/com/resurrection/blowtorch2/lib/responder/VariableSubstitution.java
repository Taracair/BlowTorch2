package com.resurrection.blowtorch2.lib.responder;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Substitutes {@code ${name}} in alias and responder text with session
 * variables.
 *
 * <p>Variables could be set — by the Set Variable responder or Lua
 * {@code SetVariable} — and then only read back by a trigger condition or by
 * Lua. There was no way to put one into text that gets sent to the game, which
 * is the obvious thing to want: a trigger captures a target's name, and an
 * alias uses it.
 *
 * <p>Braces are required, so this cannot collide with the numeric {@code $1}
 * captures handled by {@link CaptureSubstitution}, and a bare dollar in game
 * text is never mistaken for a variable.
 */
public final class VariableSubstitution {

	/** {@code ${name}} where name is letters, digits or underscore. */
	private static final Pattern REFERENCE = Pattern.compile("\\$\\{(\\w+)\\}");

	private VariableSubstitution() {
	}

	/**
	 * Replace variable references in {@code input}.
	 *
	 * <p>An unset variable is left exactly as written rather than becoming
	 * empty. Sending "kill" because {@code ${target}} silently vanished is worse
	 * than sending something visibly wrong, which tells the player what happened.
	 *
	 * @param input Text possibly containing {@code ${name}}; null becomes "".
	 * @param variables Variable name to value; null or empty means no change.
	 * @return The substituted text, or {@code input} when there is nothing to do.
	 */
	public static String apply(final String input, final Map<String, String> variables) {
		if (input == null) {
			return "";
		}
		if (input.length() == 0 || variables == null || variables.isEmpty()) {
			return input;
		}
		Matcher m = REFERENCE.matcher(input);
		StringBuffer out = new StringBuffer();
		boolean found = false;
		try {
			while (m.find()) {
				found = true;
				String name = m.group(1);
				String value = variables.containsKey(name) ? variables.get(name) : m.group(0);
				if (value == null) {
					value = "";
				}
				// A variable holding game text may contain "$".
				m.appendReplacement(out, Matcher.quoteReplacement(value));
			}
			if (!found) {
				return input;
			}
			m.appendTail(out);
			return out.toString();
		} catch (RuntimeException e) {
			return input;
		}
	}
}
