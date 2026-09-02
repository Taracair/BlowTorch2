package com.resurrection.blowtorch2.lib.trigger;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Match every enabled line-trigger against a chunk, in list order, instead of
 * joining them into one alternation.
 *
 * <p>The combined regex ({@link TriggerPattern}) is leftmost-first: at column 0
 * a broad pattern consumes the line and a later, more specific one never
 * {@code find()}s. Players write two triggers for the same line on purpose —
 * rewrite the channel tag, gag the spam inside it — and both have to run.
 *
 * <p>Compiled {@code MULTILINE}, not {@code DOTALL}, same flags as the old join.
 * {@link TriggerData#getMatcher()} stays flags {@code 0} for the editor preview.
 * Each entry has its own {@link Matcher}; the connection thread must not share
 * {@code TriggerData.getMatcher()} with the UI (responders already learned that
 * the hard way).
 *
 * <p>Pull-based: {@link #nextHit()} rather than a precomputed list, because a
 * responder can rebuild the trigger set mid-chunk. {@link #stop()} aborts the
 * whole remaining walk; Keep going? off is not that — the caller skips later
 * hits on the same line and lets other lines continue. This class does not
 * read {@code keepEvaluating}, so a condition failure is not treated as a fire.
 */
public final class TriggerCascade {

	public static final TriggerCascade EMPTY = new TriggerCascade(new Entry[0]);

	/** One match, groups snapshotted so the next {@code find()} cannot clobber them. */
	public static final class Hit {
		public final TriggerData trigger;
		/** Inclusive, {@link Matcher#start()}. */
		public final int start;
		/** Exclusive, {@link Matcher#end()}. */
		public final int end;
		/** {@code groups[0]} is the whole match; {@code groups[1]} is {@code $1}. */
		public final String[] groups;

		Hit(final TriggerData trigger, final int start, final int end, final String[] groups) {
			this.trigger = trigger;
			this.start = start;
			this.end = end;
			this.groups = groups;
		}

		public String matched() {
			return groups.length > 0 ? groups[0] : "";
		}
	}

	private static final class Entry {
		final TriggerData trigger;
		final Matcher matcher;

		Entry(final TriggerData trigger, final Matcher matcher) {
			this.trigger = trigger;
			this.matcher = matcher;
		}
	}

	private final Entry[] entries;
	private int idx;
	private boolean stopped;

	private TriggerCascade(final Entry[] entries) {
		this.entries = entries;
	}

	/**
	 * Compile one {@code MULTILINE} matcher per trigger, in the caller's order.
	 *
	 * @param ordered Matchable triggers, main then plugins, already sorted.
	 * @return A cascade, possibly {@link #EMPTY}.
	 */
	public static TriggerCascade compile(final List<TriggerData> ordered) {
		if (ordered == null || ordered.isEmpty()) {
			return EMPTY;
		}
		ArrayList<Entry> built = new ArrayList<Entry>(ordered.size());
		for (int i = 0; i < ordered.size(); i++) {
			TriggerData t = ordered.get(i);
			if (t == null) {
				continue;
			}
			Pattern sanitised = t.getCompiledPattern();
			if (sanitised == null) {
				continue;
			}
			try {
				Pattern dispatch = Pattern.compile(sanitised.pattern(), Pattern.MULTILINE);
				built.add(new Entry(t, dispatch.matcher("")));
			} catch (PatternSyntaxException bad) {
				// Already compiled at flags 0; MULTILINE almost never throws.
				// Dropping the one trigger beats taking the whole set down.
				com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor(
						"TriggerCascade.compile: skipping trigger '" + t.getName()
						+ "' with pattern that will not compile MULTILINE", bad);
			}
		}
		if (built.isEmpty()) {
			return EMPTY;
		}
		return new TriggerCascade(built.toArray(new Entry[built.size()]));
	}

	public boolean isEmpty() {
		return entries.length == 0;
	}

	/**
	 * Start a walk over {@code text}. Does not honour {@code keepEvaluating};
	 * the caller skips later hits on the same line after a real fire.
	 *
	 * @param text The ANSI-stripped chunk, same string the old join saw.
	 */
	public void reset(final CharSequence text) {
		CharSequence src = text == null ? "" : text;
		idx = 0;
		stopped = false;
		for (int i = 0; i < entries.length; i++) {
			entries[i].matcher.reset(src);
		}
	}

	/** No further hits, including more {@code find()}s of the current trigger. */
	public void stop() {
		stopped = true;
	}

	/**
	 * Next regex hit in list order, exhausting one trigger before moving on.
	 *
	 * @return The hit, or null when the walk is finished or {@link #stop()} was
	 *     called.
	 */
	public Hit nextHit() {
		if (stopped) {
			return null;
		}
		while (idx < entries.length) {
			Matcher m = entries[idx].matcher;
			if (m.find()) {
				int count = m.groupCount();
				String[] groups = new String[count + 1];
				for (int i = 0; i <= count; i++) {
					groups[i] = m.group(i);
				}
				return new Hit(entries[idx].trigger, m.start(), m.end(), groups);
			}
			idx++;
		}
		return null;
	}
}
