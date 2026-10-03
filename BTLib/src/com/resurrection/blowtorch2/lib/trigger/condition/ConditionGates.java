package com.resurrection.blowtorch2.lib.trigger.condition;

/**
 * Timer and foreground leaves. The live timer and the window flag stay on
 * {@link com.resurrection.blowtorch2.lib.service.Connection}; this only
 * combines the numbers.
 */
public final class ConditionGates {

	private ConditionGates() {
	}

	public static boolean timer(final ConditionType type, final boolean exists,
			final boolean running, final int remainingSeconds, final String expected) {
		if (type == null) {
			return true;
		}
		switch (type) {
		case TIMER_EXISTS:
			return exists;
		case TIMER_RUNNING:
			return exists && running;
		case TIMER_REMAINING_BELOW:
		case TIMER_REMAINING_ABOVE:
			if (!exists || remainingSeconds < 0) {
				return false;
			}
			Double want = ConditionEvaluator.parseFinite(expected);
			if (want == null) {
				return false;
			}
			if (type == ConditionType.TIMER_REMAINING_BELOW) {
				return remainingSeconds < want.doubleValue();
			}
			return remainingSeconds > want.doubleValue();
		default:
			return true;
		}
	}

	/** Recents is not in front: {@code inFront} is already that answer. */
	public static boolean foreground(final ConditionType type, final boolean inFront) {
		if (type == ConditionType.UI_IN_FRONT) {
			return inFront;
		}
		if (type == ConditionType.UI_IN_BACKGROUND) {
			return !inFront;
		}
		return true;
	}
}
