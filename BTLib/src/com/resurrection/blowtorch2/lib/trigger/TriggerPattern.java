package com.resurrection.blowtorch2.lib.trigger;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Joined alternation of every enabled trigger, plus capture-group → trigger.
 * Alternatives come from {@link TriggerData#getCompiledPattern()} only — raw
 * {@code getPattern()} bypasses the compile fallback and can kill the UI via
 * the binder. Builder so group numbers continue across plugins; {@link #add}
 * returns the assigned group rather than owning a plugin map.
 */
public final class TriggerPattern {

	private final StringBuilder joined = new StringBuilder();
	private final Map<Integer, TriggerData> groupToTrigger = new HashMap<Integer, TriggerData>();
	private int currentGroup = 1;
	private final java.util.Set<String> namedGroups = new java.util.HashSet<String>();

	/** A named capture group's declaration, e.g. the {@code word} of {@code (?&lt;word&gt;\w+)}. */
	private static final Pattern NAMED_GROUP =
			Pattern.compile("\\(\\?<([a-zA-Z][a-zA-Z0-9]*)>");

	/** A {@code \Q...\E} span, where every character is data. Unterminated runs to the end. */
	private static final Pattern QUOTED_SPAN =
			Pattern.compile("\\\\Q.*?(?:\\\\E|\\z)", Pattern.DOTALL);

	/**
	 * Append one trigger as the next alternative.
	 *
	 * <p>Callers decide which triggers are eligible -- enabled, not an MCP or
	 * command-prefix pattern -- and pass only those.
	 *
	 * @param t The trigger to add.
	 * @return The capture-group number now standing for this trigger, or -1 when
	 *     it was skipped, in which case nothing was appended and the numbering
	 *     did not move.
	 */
	public int add(final TriggerData t) {
		if (t == null) {
			return -1;
		}
		Pattern sanitised = t.getCompiledPattern();
		if (sanitised == null) {
			return -1;
		}
		String one = "(" + sanitised.pattern() + ")";
		// Two triggers may each declare a named group of the same name. Both
		// compile alone; the join throws "Named capture group <name> is already
		// defined", out of buildTriggerSystem, which is reachable from the
		// binder -- the same shape of crash the per-alternative compile below
		// was guarded against. Refuse the second one here rather than guarding
		// compile(): the caller's group-to-owner map has to describe the
		// pattern that was actually built, and only refusing before the append
		// keeps that true.
		java.util.List<String> declared = namesIn(one);
		for (int i = 0; i < declared.size(); i++) {
			if (namedGroups.contains(declared.get(i))) {
				com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor(
						"TriggerPattern.add: skipping trigger '" + t.getName()
						+ "' -- named group '" + declared.get(i)
						+ "' is already used by another trigger", null);
				return -1;
			}
		}
		int groups;
		try {
			// Advance past this alternative's own groups, not just by one.
			groups = Pattern.compile(one).matcher("").groupCount();
		} catch (PatternSyntaxException bad) {
			// Should not happen -- the source came from an already-compiled
			// pattern -- but every trigger shares one joined regex, so dropping
			// the one that will not compile beats taking the whole set down.
			com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor(
					"TriggerPattern.add: skipping trigger '" + t.getName()
					+ "' with bad pattern '" + sanitised.pattern() + "'", bad);
			return -1;
		}
		if (joined.length() > 0) {
			joined.append("|");
		}
		joined.append(one);
		namedGroups.addAll(declared);
		int assigned = currentGroup;
		groupToTrigger.put(Integer.valueOf(assigned), t);
		currentGroup += groups;
		return assigned;
	}

	/**
	 * Every named capture group an alternative declares.
	 *
	 * <p>Read off the source rather than from the compiled pattern because
	 * {@code java.util.regex} exposes no way to enumerate a pattern's group
	 * names. Quoted spans are dropped first: a literal trigger reaches here as
	 * {@code Pattern.quote} produced it, so a player watching for the text
	 * {@code (?<who>x)} arrives as {@code \Q(?<who>x)\E} and declares nothing.
	 * Without that, one literal trigger would take the name away from a real
	 * regex trigger that wanted it.
	 *
	 * @param source One alternative's regex source.
	 * @return The names, in the order declared; empty when there are none.
	 */
	private static java.util.List<String> namesIn(final String source) {
		java.util.List<String> names = new java.util.ArrayList<String>();
		Matcher m = NAMED_GROUP.matcher(QUOTED_SPAN.matcher(source).replaceAll(""));
		while (m.find()) {
			names.add(m.group(1));
		}
		return names;
	}

	/** @return The combined regex source, or empty when nothing was added. */
	public String regex() {
		return joined.toString();
	}

	/** @return true when nothing was added, so there is nothing to match. */
	public boolean isEmpty() {
		return joined.length() == 0;
	}

	/**
	 * Compiled form of {@link #regex()}.
	 *
	 * <p>Deliberately not guarded. Every alternative compiled on its own, but
	 * that does not make the join safe -- two triggers declaring the same named
	 * group compile separately and throw together -- and the caller is the only
	 * one that can keep its own group-to-owner bookkeeping consistent with
	 * whatever it falls back to. Silently returning an empty pattern from here
	 * would leave the caller's maps describing a pattern that was never
	 * compiled, which turns a crash into the wrong trigger firing.
	 *
	 * @param flags Flags for {@code Pattern.compile}.
	 * @return The compiled combined pattern.
	 */
	public Pattern compile(final int flags) {
		return Pattern.compile(joined.toString(), flags);
	}

	/** @return The trigger owning capture group {@code group}, or null. */
	public TriggerData triggerForGroup(final int group) {
		return groupToTrigger.get(Integer.valueOf(group));
	}

	/**
	 * Which capture group actually matched, which identifies the trigger.
	 *
	 * <p>The lowest-numbered non-null group wins. A trigger's own inner groups
	 * sit after its outer one, so the outer group is always found first.
	 *
	 * @param m A matcher positioned on a successful match.
	 * @return The group number, or -1 if none matched.
	 */
	public static int matchedGroup(final Matcher m) {
		if (m == null) {
			return -1;
		}
		for (int i = 1; i <= m.groupCount(); i++) {
			if (m.group(i) != null) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * The trigger behind the current match.
	 *
	 * @param m A matcher positioned on a successful match.
	 * @return The trigger, or null when the match cannot be attributed to one,
	 *     which the caller must check.
	 */
	public TriggerData matchedTrigger(final Matcher m) {
		int group = matchedGroup(m);
		return group < 0 ? null : triggerForGroup(group);
	}
}
