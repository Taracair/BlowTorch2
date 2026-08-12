package com.resurrection.blowtorch2.lib.service;

/**
 * Softens the first letter of a command for case-sensitive MUDs.
 *
 * <p>Phone IMEs auto-capitalize the start of a "message" field; worlds that
 * treat {@code Look} as unknown while {@code look} works need only that one
 * letter lowered. Mid-line capitals (names, {@code say Hello}) stay put.
 */
public final class CommandCase {

	private CommandCase() {
	}

	/**
	 * Lowercase the first letter of {@code segment} when it is an uppercase
	 * letter. Empty / null / non-letter starts are unchanged.
	 *
	 * @param segment one outbound command piece (after semicolon split)
	 * @return the softened segment, or the input when nothing to change
	 */
	public static String softenFirstLetter(final String segment) {
		if (segment == null || segment.isEmpty()) {
			return segment;
		}
		final char first = segment.charAt(0);
		if (!Character.isLetter(first) || !Character.isUpperCase(first)) {
			return segment;
		}
		return Character.toLowerCase(first) + segment.substring(1);
	}

	/**
	 * Soften when the option is on and the password mask is not held.
	 *
	 * <p>Leading {@code \} is a one-shot bypass: {@code \Look} sends {@code Look}
	 * with the capital kept. The backslash is stripped and never reaches the MUD.
	 *
	 * @param segment outbound piece
	 * @param enabled Options → Input → Lowercase start of sent commands
	 * @param telnetLocalEcho true while the input bar shows typed text
	 */
	public static String softenForSend(final String segment, final boolean enabled,
			final boolean telnetLocalEcho) {
		if (!enabled || !telnetLocalEcho || segment == null || segment.isEmpty()) {
			return segment;
		}
		if (segment.charAt(0) == '\\' && segment.length() > 1) {
			return segment.substring(1);
		}
		return softenFirstLetter(segment);
	}
}
