package com.resurrection.blowtorch2.lib.service.sensor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Picker labels for {@code device.*} conditions. Values are ours, not sensor
 * names; a missing sensor leaves the variable unset.
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
		all.add(new Choice("It is dark around the phone", DeviceState.KEY_LIGHT,
				DeviceState.DARK, "a light sensor, and calibrating what dark means"));
		all.add(new Choice("It is bright around the phone", DeviceState.KEY_LIGHT,
				DeviceState.BRIGHT, "a light sensor, and calibrating what bright means"));
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
