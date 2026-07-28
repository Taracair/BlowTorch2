package com.resurrection.blowtorch2.lib.responder;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Substitutes {@code $1}, {@code $2}, … in responder text with the pieces a
 * trigger or alias captured.
 *
 * <p>Every responder runs through here — Ack, Toast, Notification, Set Variable,
 * Replace — and so do the anchored alias forms, which makes it one of the most
 * used functions in the app. It had no tests, because it lived as an instance
 * method on an Android {@code Parcelable}.
 *
 * <p>It also kept its {@link Matcher} and output buffer in instance fields
 * shared by every call on that responder. Triggers fire on the connection
 * thread and timers on a timer thread, so that was a data race waiting for the
 * right pair of events. Everything here is local, so there is nothing to share.
 */
public final class CaptureSubstitution {

	/** A {@code $} followed by at least one digit. */
	private static final Pattern REFERENCE = Pattern.compile("\\$(\\d+)");

	private CaptureSubstitution() {
	}

	/**
	 * Replace capture references in {@code input}.
	 *
	 * <p>A reference with no matching capture is left as written, so a player who
	 * types a literal {@code $5} in an alias still sees {@code $5} rather than
	 * having it silently vanish.
	 *
	 * @param input Text containing {@code $n} references; null becomes "".
	 * @param map Capture number (as a string) to captured text.
	 * @return The substituted text, or {@code input} unchanged when there is
	 *     nothing to do or the substitution fails.
	 */
	public static String apply(final String input, final Map<String, String> map) {
		if (input == null) {
			return "";
		}
		if (input.length() == 0 || map == null || map.isEmpty()) {
			return input;
		}
		StringBuffer output = new StringBuffer();
		Matcher m = REFERENCE.matcher(input);
		boolean found = false;
		try {
			while (m.find()) {
				found = true;
				String wanted = m.group(1);
				String replacement;
				if (map.containsKey(wanted)) {
					replacement = map.get(wanted);
				} else {
					// Keep the literal "$N" when that capture is missing.
					replacement = m.group(0);
				}
				if (replacement == null) {
					replacement = "";
				}
				// Captured text may contain "$" -- prices, and anything a MUD
				// prints -- which Matcher would read as a group reference and
				// throw on.
				m.appendReplacement(output, Matcher.quoteReplacement(replacement));
			}
			if (!found) {
				return input;
			}
			m.appendTail(output);
			return output.toString();
		} catch (RuntimeException e) {
			// Callers catch too. Returning the original text keeps a malformed
			// reference from taking down the line being processed.
			return input;
		}
	}
}
