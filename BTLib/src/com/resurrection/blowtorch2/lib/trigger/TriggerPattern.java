package com.resurrection.blowtorch2.lib.trigger;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * The combined regular expression that matches every enabled trigger at once,
 * and the map from capture-group number back to the trigger that owns it.
 *
 * <p>The counterpart of {@link com.resurrection.blowtorch2.lib.alias.AliasPattern}
 * for triggers. That class was extracted from {@code Plugin.buildAliases} because
 * one joined alternation over player-supplied patterns is the trickiest thing in
 * the system: a pattern may declare its own groups, so a trigger's outer group
 * number depends on how many groups every trigger before it declared. Get it
 * wrong and a match is attributed to the wrong trigger, which runs the wrong
 * responders. {@code Connection.buildTriggerSystem} was the identical construct
 * and kept building the alternation by hand, which cost it three things this
 * class fixes:
 *
 * <ul>
 * <li>It appended the <em>raw</em> {@code getPattern()} field, so the fallback
 *     {@link TriggerData#buildData} installs for a pattern that will not compile
 *     was bypassed and the raw broken pattern reached {@code Pattern.compile}.
 * <li>It hand-built the {@code \Q...\E} span, the exact thing {@code TriggerData}
 *     moved to {@code Pattern.quote} because a literal trigger containing
 *     {@code \E} ended the quoted span early.
 * <li>It compiled the join unguarded, out of methods reachable from the binder,
 *     so a {@code PatternSyntaxException} travelled back and killed the UI
 *     process.
 * </ul>
 *
 * <p>Building from {@link TriggerData#getCompiledPattern()} means there is
 * exactly one sanitisation point, in {@code TriggerData}, and every alternative
 * here is known to compile on its own before it is joined.
 *
 * <p>This is a builder rather than a static factory because a connection draws
 * its triggers from several sources -- its own settings and every enabled plugin
 * -- and the group numbering has to run continuously across all of them. The
 * caller also has to remember which plugin owns which group, which is why
 * {@link #add} hands the assigned number back instead of keeping an owner map
 * of its own; a plugin is a service-side object and has no business in here.
 */
public final class TriggerPattern {

	private final StringBuilder joined = new StringBuilder();
	private final Map<Integer, TriggerData> groupToTrigger = new HashMap<Integer, TriggerData>();
	private int currentGroup = 1;

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
		int assigned = currentGroup;
		groupToTrigger.put(Integer.valueOf(assigned), t);
		currentGroup += groups;
		return assigned;
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
