package com.resurrection.blowtorch2.lib.timer;

import java.util.Map;

import com.resurrection.blowtorch2.lib.responder.script.ScriptResponder;

/**
 * The timer the editor's New button stores: stopped, not repeating, no
 * conditions. The Repeat box is unchecked, so this is not
 * {@link TimerData}'s constructor default of repeating. Whole seconds.
 */
public final class TimerCreate {

	private TimerCreate() {
	}

	/**
	 * @return the second count when {@code seconds} is an exact {@code int},
	 *     including zero and negatives. Null when it is not a whole number
	 *     or does not fit in an {@code int}. {@link #rejection} refuses a
	 *     count below 1.
	 */
	public static Integer wholeSeconds(double seconds) {
		if (Double.isNaN(seconds) || Double.isInfinite(seconds)) {
			return null;
		}
		if (seconds != Math.rint(seconds)) {
			return null;
		}
		if (seconds < Integer.MIN_VALUE || seconds > Integer.MAX_VALUE) {
			return null;
		}
		return Integer.valueOf((int) seconds);
	}

	/**
	 * Why this timer cannot be stored, or null when it can.
	 * {@code function} null means no script action. An empty name is the
	 * editor's blank check. {@code seconds} null is not a whole second count.
	 */
	public static String rejection(String name, Integer seconds, String function,
			Map<String, TimerData> existing) {
		if (name == null || name.length() == 0) {
			return "name is empty";
		}
		if (seconds == null) {
			return "seconds must be a whole number";
		}
		if (seconds.intValue() <= 0) {
			return "duration must be more than zero seconds";
		}
		if (function != null && function.length() == 0) {
			return "function name is empty";
		}
		if (existing != null && existing.containsKey(name)) {
			return "\"" + name + "\" exists already";
		}
		return null;
	}

	/** {@code function} null stores no script action. */
	public static TimerData create(String name, int seconds, String function) {
		TimerData timer = new TimerData();
		timer.setName(name);
		timer.setSeconds(Integer.valueOf(seconds));
		timer.setRemainingTime(seconds);
		timer.setRepeat(false);
		timer.setPlaying(false);
		if (function != null) {
			ScriptResponder script = new ScriptResponder();
			script.setFunction(function);
			timer.getResponders().add(script);
		}
		return timer;
	}
}
