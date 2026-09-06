package com.resurrection.blowtorch2.lib.trigger;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.resurrection.blowtorch2.lib.trigger.style.StyleLineModel;
import com.resurrection.blowtorch2.lib.trigger.style.StyleMatchSpec;
import com.resurrection.blowtorch2.lib.trigger.style.StyleMatcher;

/**
 * Match enabled line-triggers in list order, not as one leftmost-first join.
 * {@code MULTILINE} not {@code DOTALL}; each entry has its own {@link Matcher}
 * — do not share {@code TriggerData.getMatcher()} with the UI. Pull-based:
 * {@link #nextHit()} because a responder can rebuild mid-chunk. Does not read
 * {@code keepEvaluating}.
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
		final boolean styleOnly;

		Entry(final TriggerData trigger, final Matcher matcher, final boolean styleOnly) {
			this.trigger = trigger;
			this.matcher = matcher;
			this.styleOnly = styleOnly;
		}
	}

	private final Entry[] entries;
	private int idx;
	private boolean stopped;
	private StyleLineModel[] styleModels;
	private int[] styleLineStarts;
	private int[] styleStrippedLens;
	private int styleRunLine;
	private int styleRunIdx;

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
			if (t.isStyleOnly()) {
				built.add(new Entry(t, null, true));
				continue;
			}
			if (t.isBlankPattern()) {
				continue;
			}
			Pattern sanitised = t.getCompiledPattern();
			if (sanitised == null) {
				continue;
			}
			try {
				Pattern dispatch = Pattern.compile(sanitised.pattern(), Pattern.MULTILINE);
				built.add(new Entry(t, dispatch.matcher(""), false));
			} catch (PatternSyntaxException bad) {
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

	public boolean hasStyleWork() {
		for (int i = 0; i < entries.length; i++) {
			if (entries[i].styleOnly || entries[i].trigger.getStyleMatch().isActive()) {
				return true;
			}
		}
		return false;
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
		styleRunLine = 0;
		styleRunIdx = 0;
		for (int i = 0; i < entries.length; i++) {
			if (entries[i].matcher != null) {
				entries[i].matcher.reset(src);
			}
		}
	}

	public void attachStyle(final StyleLineModel[] models, final int[] lineStarts) {
		attachStyle(models, lineStarts, null);
	}

	/**
	 * @param strippedLens per-line {@code ^.*$} lengths in the cascade string,
	 *     newest-first like {@code models}. When set, a line whose tree length
	 *     disagrees fails closed (CSI holdover vs strip).
	 */
	public void attachStyle(final StyleLineModel[] models, final int[] lineStarts,
			final int[] strippedLens) {
		styleModels = models;
		styleLineStarts = lineStarts;
		styleStrippedLens = strippedLens;
		styleRunLine = 0;
		styleRunIdx = 0;
	}

	private boolean lineLengthOk(final int line) {
		if (styleStrippedLens == null || styleModels == null
				|| line < 0 || line >= styleModels.length
				|| styleModels[line] == null) {
			return styleStrippedLens == null;
		}
		if (line >= styleStrippedLens.length) {
			return false;
		}
		return styleModels[line].matchLength() == styleStrippedLens[line];
	}

	/** No further hits, including more {@code find()}s of the current trigger. */
	public void stop() {
		stopped = true;
	}

	/**
	 * Next regex or style-run hit in list order, exhausting one trigger before
	 * moving on.
	 *
	 * @return The hit, or null when the walk is finished or {@link #stop()} was
	 *     called.
	 */
	public Hit nextHit() {
		if (stopped) {
			return null;
		}
		while (idx < entries.length) {
			Entry e = entries[idx];
			if (e.styleOnly) {
				Hit h = nextStyleRun(e.trigger);
				if (h != null) {
					return h;
				}
				idx++;
				styleRunLine = 0;
				styleRunIdx = 0;
				continue;
			}
			Matcher m = e.matcher;
			while (m.find()) {
				StyleMatchSpec spec = e.trigger.getStyleMatch();
				if (spec != null && spec.isActive()) {
					if (!spanMatches(m.start(), m.end(), spec, m.group(0))) {
						continue;
					}
				}
				int count = m.groupCount();
				String[] groups = new String[count + 1];
				for (int i = 0; i <= count; i++) {
					groups[i] = m.group(i);
				}
				return new Hit(e.trigger, m.start(), m.end(), groups);
			}
			idx++;
		}
		return null;
	}

	private Hit nextStyleRun(final TriggerData t) {
		if (styleModels == null || styleLineStarts == null) {
			return null;
		}
		StyleMatchSpec spec = t.getStyleMatch();
		while (styleRunLine < styleModels.length) {
			StyleLineModel model = styleModels[styleRunLine];
			if (model == null) {
				styleRunLine++;
				styleRunIdx = 0;
				continue;
			}
			int base = styleRunLine < styleLineStarts.length
					? styleLineStarts[styleRunLine] : -1;
			while (styleRunIdx < model.runs.size()) {
				StyleLineModel.Run run = model.runs.get(styleRunIdx++);
				if (base < 0) {
					continue;
				}
				if (!lineLengthOk(styleRunLine)) {
					continue;
				}
				if (!StyleMatcher.matches(run.snapshot, spec, run.text)) {
					continue;
				}
				// No regex groups; $1 is the run so Ack $1 matches alias habit.
				return new Hit(t, base + run.start, base + run.end,
						new String[] { run.text, run.text });
			}
			styleRunLine++;
			styleRunIdx = 0;
		}
		return null;
	}

	private boolean spanMatches(final int start, final int end,
			final StyleMatchSpec spec, final String matched) {
		if (styleModels == null || styleLineStarts == null) {
			return false;
		}
		boolean overlapped = false;
		for (int line = 0; line < styleModels.length; line++) {
			int ls = line < styleLineStarts.length ? styleLineStarts[line] : -1;
			if (ls < 0 || styleModels[line] == null) {
				continue;
			}
			int le = ls + styleModels[line].plain.length();
			int a = Math.max(start, ls);
			int b = Math.min(end, le);
			if (a >= b) {
				continue;
			}
			if (!lineLengthOk(line)) {
				return false;
			}
			overlapped = true;
			int col0 = a - ls;
			int col1 = b - ls;
			if (!StyleMatcher.matchesSpan(styleModels[line].byChar, col0, col1, spec,
					matched)) {
				return false;
			}
		}
		return overlapped;
	}
}
