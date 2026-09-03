package com.resurrection.blowtorch2.lib.responder;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code ${name}} after {@code $1}. Braces required so {@code $1} is never
 * touched. Unset left as written.
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
