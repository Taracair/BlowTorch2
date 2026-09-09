package com.resurrection.blowtorch2.lib.trigger;

import java.util.Collections;

import com.resurrection.blowtorch2.lib.trigger.style.StyleLineModel;

/**
 * Look-ahead: would this other trigger hit the same {@code \\n} line as
 * {@code matchStart}? Pattern and Match style — not its conditions, not
 * its actions. Also-contains from an older profile is still checked.
 */
public final class TriggerWouldMatch {

	private TriggerWouldMatch() {
	}

	public static boolean onSameLine(final TriggerData other, final CharSequence chunk,
			final int matchStart, final StyleLineModel[] models, final int[] lineStarts,
			final int[] strippedLens) {
		if (other == null || !other.isEnabled() || chunk == null) {
			return false;
		}
		TriggerCascade cascade = TriggerCascade.compile(Collections.singletonList(other));
		if (cascade.isEmpty()) {
			return false;
		}
		cascade.reset(chunk);
		cascade.attachStyle(models, lineStarts, strippedLens);
		int from = AlsoContainsGate.lineStart(chunk, matchStart);
		int to = AlsoContainsGate.lineEnd(chunk, matchStart);
		TriggerCascade.Hit h;
		while ((h = cascade.nextHit()) != null) {
			if (h.start >= from && h.start < to) {
				return true;
			}
		}
		return false;
	}
}
