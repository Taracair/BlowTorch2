package com.resurrection.blowtorch2.lib.service.sensor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The states of the phone a player can gate a trigger on, in words.
 *
 * <p><b>Why a list and not a text box.</b> The condition editor can already test
 * a session variable, and the device state is written into session variables —
 * so the machinery was there, but using it meant knowing to type
 * {@code device.facing} and then knowing that the answer is spelled {@code down}
 * rather than {@code no} or {@code false}. That is a memory test, not a feature.
 * This turns it into a list: "Phone is face down".
 *
 * <p><b>These names are ours, not the phone's.</b> Sensor hardware differs
 * wildly between models, but {@code device.facing} means the same thing on every
 * one of them, because BlowTorch is what writes it. So a picker is safe here in
 * a way that a list of raw sensor names never would be: what differs between
 * phones is only whether a value ever gets set, and the entry says which sensor
 * it needs.
 *
 * <p>No Android types — the UI and the manual both read this list.
 */
public final class DeviceConditions {

	/** One choice in the picker. */
	public static final class Choice {
		private final String label;
		private final String variable;
		private final String value;
		private final String needs;

		Choice(final String label, final String variable, final String value,
				final String needs) {
			this.label = label;
			this.variable = variable;
			this.value = value;
			this.needs = needs;
		}

		/** What the player reads. */
		public String getLabel() {
			return label;
		}

		/** The session variable this writes into the condition. */
		public String getVariable() {
			return variable;
		}

		/** The value it is compared against. */
		public String getValue() {
			return value;
		}

		/** What the phone needs for this ever to be true, in words. */
		public String getNeeds() {
			return needs;
		}
	}

	private static final List<Choice> ALL;

	static {
		List<Choice> all = new ArrayList<Choice>();
		all.add(new Choice("Phone is face down", DeviceState.KEY_FACING,
				DeviceState.DOWN, "any phone with an accelerometer"));
		all.add(new Choice("Phone is face up", DeviceState.KEY_FACING,
				DeviceState.UP, "any phone with an accelerometer"));
		all.add(new Choice("Screen is on", DeviceState.KEY_SCREEN,
				DeviceState.ON, "every phone"));
		all.add(new Choice("Screen is off", DeviceState.KEY_SCREEN,
				DeviceState.OFF, "every phone"));
		all.add(new Choice("Headphones are plugged in", DeviceState.KEY_HEADPHONES,
				DeviceState.YES, "every phone"));
		all.add(new Choice("Headphones are not plugged in", DeviceState.KEY_HEADPHONES,
				DeviceState.NO, "every phone"));
		all.add(new Choice("Phone is charging", DeviceState.KEY_CHARGING,
				DeviceState.YES, "every phone"));
		all.add(new Choice("Phone is not charging", DeviceState.KEY_CHARGING,
				DeviceState.NO, "every phone"));
		all.add(new Choice("Something is over the screen (in a pocket)",
				DeviceState.KEY_COVERED, DeviceState.YES,
				"a proximity sensor — .sensor caps says if this one has it"));
		all.add(new Choice("Nothing is over the screen", DeviceState.KEY_COVERED,
				DeviceState.NO, "a proximity sensor"));
		ALL = Collections.unmodifiableList(all);
	}

	private DeviceConditions() {
	}

	public static List<Choice> all() {
		return ALL;
	}

	/** The choice matching a variable and value, or null — used to show the list
	 * with the player's existing condition already selected. */
	public static Choice match(final String variable, final String value) {
		if (variable == null || value == null) {
			return null;
		}
		for (Choice c : ALL) {
			if (c.getVariable().equals(variable) && c.getValue().equals(value)) {
				return c;
			}
		}
		return null;
	}

	/** Whether a variable name belongs to the device state at all. */
	public static boolean isDeviceVariable(final String variable) {
		return variable != null && variable.startsWith("device.");
	}

	/**
	 * The one warning that has to travel with every one of these.
	 *
	 * <p>A condition on a variable that is never set is <b>false</b>, so a
	 * trigger gated on the phone with the setting off does not fire and gives no
	 * clue why. Anywhere this list appears, this line appears with it.
	 */
	public static final String NEEDS_WATCHING =
			"These are only kept up to date while Options → Device → "
			+ "\"Device state as variables\" is on. With it off nothing sets them, "
			+ "and a condition on one is false — so the trigger simply never fires.";
}
