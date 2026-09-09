package com.resurrection.blowtorch2.lib.trigger.condition;

import com.resurrection.blowtorch2.lib.trigger.TriggerData;
import com.resurrection.blowtorch2.lib.trigger.style.StyleLineModel;

/**
 * The current Pattern hit, so a {@link ConditionType#TRIGGER_MATCHED} leaf can
 * look at another trigger on the same {@code \\n} line. Null on timers and
 * device gestures (no line).
 */
public final class LineMatchContext {

	public final CharSequence chunk;
	public final int matchStart;
	public final TriggerData current;
	public final StyleLineModel[] models;
	public final int[] lineStarts;
	public final int[] strippedLens;

	public LineMatchContext(final CharSequence chunk, final int matchStart,
			final TriggerData current, final StyleLineModel[] models,
			final int[] lineStarts, final int[] strippedLens) {
		this.chunk = chunk;
		this.matchStart = matchStart;
		this.current = current;
		this.models = models;
		this.lineStarts = lineStarts;
		this.strippedLens = strippedLens;
	}
}
