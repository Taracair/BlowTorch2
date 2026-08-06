package com.resurrection.blowtorch2.lib.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The multiplier prefix: {@code #5 north} sends {@code north} five times.
 *
 * <p>This runs on the command segments that come out of the semicolon split,
 * <b>before</b> alias replacement, so {@code #3 kk troll} is three of whatever
 * {@code kk troll} expands to rather than one expansion repeated as text. That
 * is the order a player expects: the multiplier counts the thing they typed.
 *
 * <p>There is no timing here. Five copies go into the outgoing batch at once,
 * exactly as if the player had typed {@code north;north;north;north;north}. A
 * repeat that paces itself would need a queue on the connection thread and a
 * way to cancel it; a burst needs neither, and every MUD already has to cope
 * with a pasted block of commands.
 *
 * <p><b>Escape.</b> Some worlds use {@code #} for their own commands, so a
 * doubled hash sends one literal hash and no repeat: {@code ##5 north} reaches
 * the game as {@code #5 north}. This mirrors {@code ..} for dot commands rather
 * than inventing a second convention.
 */
public final class CommandRepeat {

	/**
	 * The most copies one segment may produce. A slip of the thumb turning
	 * {@code #5} into {@code #500} is a flood the world may well read as an
	 * attack, so past this the segment is refused outright rather than clamped
	 * — silently sending 100 of the 500 asked for would be its own surprise.
	 */
	public static final int MAX_REPEAT = 100;

	/** {@code #<digits> <rest>}; the rest must be non-blank to be a command. */
	private static final Pattern REPEAT =
			Pattern.compile("^\\s*#(\\d+)\\s+(\\S.*)$");

	/** A segment that starts with two hashes: send one hash, do not repeat. */
	private static final Pattern ESCAPED = Pattern.compile("^\\s*##");

	/** The expanded segments, plus anything the player needs told. */
	public static final class Result {
		private final List<String> segments;
		private final String warning;

		Result(final List<String> segments, final String warning) {
			this.segments = segments;
			this.warning = warning;
		}

		public List<String> segments() {
			return segments;
		}

		/**
		 * Null when nothing was refused. Otherwise the message to put in the
		 * window — a refused repeat has to say so, or the player sees one
		 * command go out and assumes the feature is broken.
		 */
		public String warning() {
			return warning;
		}
	}

	private CommandRepeat() {
	}

	/**
	 * Expand every {@code #N cmd} segment in a batch.
	 *
	 * @param segments the semicolon-split command segments; not modified.
	 * @return the same list with multipliers expanded, never null.
	 */
	public static Result expand(final List<String> segments) {
		if (segments == null || segments.isEmpty()) {
			return new Result(segments, null);
		}
		// Nothing to do for the overwhelmingly common batch, and this keeps the
		// allocation off the send path of every ordinary typed line.
		boolean interesting = false;
		for (int i = 0; i < segments.size(); i++) {
			String s = segments.get(i);
			if (s != null && s.indexOf('#') > -1) {
				interesting = true;
				break;
			}
		}
		if (!interesting) {
			return new Result(segments, null);
		}

		List<String> out = new ArrayList<String>(segments.size());
		StringBuilder refused = null;
		for (int i = 0; i < segments.size(); i++) {
			String segment = segments.get(i);
			if (segment == null) {
				out.add(segment);
				continue;
			}
			// A ~ segment is half of a holdover chain being reassembled further
			// down processOutputData. Multiplying half a command would produce
			// nonsense, so these are passed through untouched.
			if (segment.endsWith("~")) {
				out.add(segment);
				continue;
			}
			if (ESCAPED.matcher(segment).find()) {
				out.add(segment.replaceFirst("##", "#"));
				continue;
			}
			Matcher m = REPEAT.matcher(segment);
			if (!m.matches()) {
				out.add(segment);
				continue;
			}
			int count = parseCount(m.group(1));
			String body = m.group(2);
			if (count < 1 || count > MAX_REPEAT) {
				// Left exactly as typed, so the player sees the world reject it
				// rather than seeing us quietly do something else.
				out.add(segment);
				if (refused == null) {
					refused = new StringBuilder();
				}
				refused.append("Repeat refused: ").append(segment.trim())
						.append(" (allowed 1-").append(MAX_REPEAT).append(")\n");
				continue;
			}
			for (int n = 0; n < count; n++) {
				out.add(body);
			}
		}
		return new Result(out, refused == null ? null : refused.toString());
	}

	/**
	 * {@code #99999999999999 x} is digits that do not fit an int. Anything that
	 * overflows is out of range by definition, so it reports as such.
	 */
	private static int parseCount(final String digits) {
		try {
			return Integer.parseInt(digits);
		} catch (NumberFormatException tooBig) {
			return MAX_REPEAT + 1;
		}
	}
}
