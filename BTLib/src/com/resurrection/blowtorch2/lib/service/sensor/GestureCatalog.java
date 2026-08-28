package com.resurrection.blowtorch2.lib.service.sensor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The gestures a player can pick from, and what each one can be measured with.
 *
 * <p><b>The player picks a gesture, never a sensor.</b> "Wave your hand over the
 * screen" is a thing a person does; {@code TYPE_PROXIMITY} is a part number that
 * half the phones on the market do not have. So every gesture here carries an
 * ordered list of ways to measure it, and the device decides which one it can
 * honour — see {@code GestureAvailability}. A gesture no sensor on this phone
 * can provide is shown as unavailable with the reason, never silently offered.
 *
 * <p><b>Why the pattern looks like {@code !wave}.</b> A device gesture is stored
 * as an ordinary trigger, so every action a trigger has — send a command, run
 * Lua, play a sound, speak, set a variable, enable another trigger — works with
 * it for free, and any action added later does too. The reserved prefix is how
 * {@code Connection.isMatchableTrigger} keeps these out of the text-matching
 * pattern, exactly as {@code %} does for GMCP and {@code @} for MCP. The player
 * never types it: the editor offers a list.
 *
 * <p>Only a prefix <em>plus a known gesture name</em> is reserved. A literal
 * trigger watching for {@code !!!} keeps working as text, because {@code !!!} is
 * not a gesture in this catalogue.
 *
 * <p>No Android types: this half is testable, and the sensor numbers live on the
 * other side of {@code GestureAvailability}.
 */
public final class GestureCatalog {

	/** Marks a trigger pattern as a device gesture rather than game text. */
	public static final String PREFIX = "!";

	/** Ways to measure a gesture, named here and resolved to sensors elsewhere. */
	public static final String BY_PROXIMITY = "proximity";
	public static final String BY_LIGHT = "light";
	public static final String BY_LINEAR_ACCELERATION = "linear";
	public static final String BY_ACCELEROMETER = "accelerometer";
	public static final String BY_GRAVITY = "gravity";
	/**
	 * Not a sensor at all: something the system announces to every app, like a
	 * headphone jack or a charger. These work on every phone ever made, which
	 * makes them the portable half of this feature.
	 */
	public static final String BY_SYSTEM = "system";
	/** One-shot hardware gestures: they fire once and must be re-armed. */
	public static final String BY_PICKUP_SENSOR = "pickup-sensor";
	public static final String BY_SIGNIFICANT_MOTION = "significant-motion";
	public static final String BY_STATIONARY = "stationary";

	/**
	 * Headings the list screen groups by, in the order they are shown. A player
	 * looking for "the one where I put the phone down" scans four headings, not
	 * a long ungrouped list.
	 */
	public static final String GROUP_HAND = "A hand over the screen";
	public static final String GROUP_MOVEMENT = "Movement";
	public static final String GROUP_LIGHT = "Light";
	public static final String GROUP_SYSTEM = "Headphones, charger, screen and rotation";

	/** One gesture: what the player picks, and how it might be measured. */
	public static final class Gesture {
		private final String id;
		private final String group;
		private final String label;
		private final String help;
		private final List<String> providers;

		Gesture(final String id, final String group, final String label, final String help,
				final String... providers) {
			this.id = id;
			this.group = group;
			this.label = label;
			this.help = help;
			List<String> p = new ArrayList<String>();
			for (int i = 0; i < providers.length; i++) {
				p.add(providers[i]);
			}
			this.providers = Collections.unmodifiableList(p);
		}

		/** The heading this gesture is listed under. */
		public String getGroup() {
			return group;
		}

		/** Stable name, used in the trigger pattern and in {@code .sensor}. */
		public String getId() {
			return id;
		}

		/** What the player reads in the list. */
		public String getLabel() {
			return label;
		}

		/** One line saying when it fires. */
		public String getHelp() {
			return help;
		}

		/** Ways to measure it, best first. */
		public List<String> getProviders() {
			return providers;
		}

		/** The trigger pattern that stands for this gesture. */
		public String getPattern() {
			return PREFIX + id;
		}
	}

	private static final List<Gesture> ALL;

	static {
		// One line each, and each one says when the reading fires. What a player
		// might use it for is their business: the list has a row for each of these and
		// a suggested use on every row is a paragraph to scroll past.
		List<Gesture> all = new ArrayList<Gesture>();
		all.add(new Gesture("wave", GROUP_HAND, "Wave a hand over the screen",
				"A hand passes over the top of the screen and away again.",
				BY_PROXIMITY, BY_LIGHT));
		all.add(new Gesture("cover", GROUP_HAND, "Hold a hand over the screen",
				"A hand stays over the top of the screen for a moment. Told apart"
					+ " from a wave by how long it lasts.",
				BY_PROXIMITY));
		all.add(new Gesture("facedown", GROUP_MOVEMENT, "Put the phone face down",
				"The phone is laid screen-down on a flat surface.",
				BY_GRAVITY, BY_ACCELEROMETER));
		all.add(new Gesture("faceup", GROUP_MOVEMENT, "Turn the phone face up again",
				"The phone is turned screen-up after lying face down.",
				BY_GRAVITY, BY_ACCELEROMETER));
		all.add(new Gesture("shake", GROUP_MOVEMENT, "Shake the phone",
				"The phone is shaken. How hard that has to be is set by Calibrate"
					+ " shake, in Options \u2192 Device.",
				BY_LINEAR_ACCELERATION, BY_ACCELEROMETER));
		all.add(new Gesture("pickup", GROUP_MOVEMENT, "Pick the phone up",
				"The phone is lifted off a surface.",
				BY_PICKUP_SENSOR));
		all.add(new Gesture("moving", GROUP_MOVEMENT, "Start moving about",
				"You walk off with the phone. Fires once when movement begins, not"
					+ " while you fidget.",
				BY_SIGNIFICANT_MOTION));
		all.add(new Gesture("still", GROUP_MOVEMENT, "The phone goes still",
				"The phone has lain untouched for a while.",
				BY_STATIONARY));
		all.add(new Gesture("gotdark", GROUP_LIGHT, "It gets dark around you",
				"The light around the phone drops. What counts as dark is set by"
					+ " Calibrate light, in Options \u2192 Device.",
				BY_LIGHT));
		all.add(new Gesture("gotbright", GROUP_LIGHT, "It gets bright around you",
				"The light around the phone rises.",
				BY_LIGHT));
		// The system events. No sensor, no battery cost, and — unlike everything
		// above — no phone anywhere is missing them, so a profile built on these
		// works for whoever it is sent to.
		all.add(new Gesture("headphonesout", GROUP_SYSTEM, "Unplug the headphones",
				"The headphone jack comes out.", BY_SYSTEM));
		all.add(new Gesture("headphonesin", GROUP_SYSTEM, "Plug the headphones in",
				"Headphones are plugged in.", BY_SYSTEM));
		all.add(new Gesture("powerin", GROUP_SYSTEM, "Plug the charger in",
				"The charger is plugged in.", BY_SYSTEM));
		all.add(new Gesture("powerout", GROUP_SYSTEM, "Unplug the charger",
				"The charger is unplugged.", BY_SYSTEM));
		all.add(new Gesture("screenoff", GROUP_SYSTEM, "The screen goes off",
				"The screen turns off, whether you locked it or it timed out.",
				BY_SYSTEM));
		all.add(new Gesture("screenon", GROUP_SYSTEM, "The screen comes back",
				"The screen turns on.", BY_SYSTEM));
		all.add(new Gesture("landscape", GROUP_SYSTEM, "Turn the phone sideways",
				"The screen goes landscape (sideways).", BY_SYSTEM));
		all.add(new Gesture("portrait", GROUP_SYSTEM, "Turn the phone upright",
				"The screen goes portrait (upright).", BY_SYSTEM));
		ALL = Collections.unmodifiableList(all);
	}

	private GestureCatalog() {
	}

	/** Every gesture, in the order the player should see them. */
	public static List<Gesture> all() {
		return ALL;
	}

	/** The gesture with this id, or null. */
	public static Gesture byId(final String id) {
		if (id == null) {
			return null;
		}
		String needle = id.trim().toLowerCase(Locale.US);
		for (Gesture g : ALL) {
			if (g.getId().equals(needle)) {
				return g;
			}
		}
		return null;
	}

	/**
	 * The gesture a trigger pattern stands for, or null when the pattern is
	 * ordinary game text.
	 *
	 * @param pattern the trigger's pattern, as the player's profile holds it.
	 * @param literal false for a regex trigger, which is never a gesture — a
	 *        regex beginning with {@code !} is a perfectly ordinary pattern.
	 */
	public static Gesture fromPattern(final String pattern, final boolean literal) {
		if (!literal || pattern == null || !pattern.startsWith(PREFIX)) {
			return null;
		}
		return byId(pattern.substring(PREFIX.length()));
	}

	/** Whether this pattern is a device gesture and not game text. */
	public static boolean isGesturePattern(final String pattern, final boolean literal) {
		return fromPattern(pattern, literal) != null;
	}
}
