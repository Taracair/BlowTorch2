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

	/** One gesture: what the player picks, and how it might be measured. */
	public static final class Gesture {
		private final String id;
		private final String label;
		private final String help;
		private final List<String> providers;

		Gesture(final String id, final String label, final String help,
				final String... providers) {
			this.id = id;
			this.label = label;
			this.help = help;
			List<String> p = new ArrayList<String>();
			for (int i = 0; i < providers.length; i++) {
				p.add(providers[i]);
			}
			this.providers = Collections.unmodifiableList(p);
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
		List<Gesture> all = new ArrayList<Gesture>();
		all.add(new Gesture("wave", "Wave a hand over the screen",
				"Pass your hand over the top of the screen and away again. Quiet,"
					+ " one-handed, and it works with the screen off.",
				BY_PROXIMITY, BY_LIGHT));
		all.add(new Gesture("cover", "Hold a hand over the screen",
				"Cover the top of the screen and keep it there for a moment. A"
					+ " different gesture from a wave, told apart by time rather"
					+ " than by how hard you did it.",
				BY_PROXIMITY));
		all.add(new Gesture("facedown", "Put the phone face down",
				"Lay the phone screen-down on a table. The classic \"I am stepping"
					+ " away\" — send afk, hush the speech, stop the timers.",
				BY_GRAVITY, BY_ACCELEROMETER));
		all.add(new Gesture("faceup", "Turn the phone face up again",
				"The other half of face down: you picked it back up and turned it"
					+ " over. Send afk off, start talking again.",
				BY_GRAVITY, BY_ACCELEROMETER));
		all.add(new Gesture("shake", "Shake the phone",
				"Shake the phone the way you would to get out of a fight. Needs the"
					+ " screen on, and a threshold that suits how you shake.",
				BY_LINEAR_ACCELERATION, BY_ACCELEROMETER));
		all.add(new Gesture("pickup", "Pick the phone up",
				"Lift the phone off the table. Done by a sensor built for exactly"
					+ " this, so it costs almost nothing and works with the screen"
					+ " off — but not every phone has one.",
				BY_PICKUP_SENSOR));
		all.add(new Gesture("moving", "Start moving about",
				"You got up and walked off with the phone. Fires once when real"
					+ " movement begins, not while you fidget.",
				BY_SIGNIFICANT_MOTION));
		all.add(new Gesture("still", "The phone goes still",
				"It has been lying untouched for a while. The quiet opposite of"
					+ " picking it up.",
				BY_STATIONARY));
		// The system events. No sensor, no battery cost, and — unlike everything
		// above — no phone anywhere is missing them, so a profile built on these
		// works for whoever it is sent to.
		all.add(new Gesture("headphonesout", "Unplug the headphones",
				"The jack came out. Hush anything that speaks, before the room"
					+ " hears your MUD.",
				BY_SYSTEM));
		all.add(new Gesture("headphonesin", "Plug the headphones in",
				"Sound is private again.", BY_SYSTEM));
		all.add(new Gesture("powerin", "Plug the charger in",
				"Settling in for a long session.", BY_SYSTEM));
		all.add(new Gesture("powerout", "Unplug the charger",
				"Off the charger and on the clock.", BY_SYSTEM));
		all.add(new Gesture("screenoff", "The screen goes off",
				"The phone locked or timed out. You are not reading the game any"
					+ " more, whatever the connection thinks.",
				BY_SYSTEM));
		all.add(new Gesture("screenon", "The screen comes back",
				"You are looking at it again.", BY_SYSTEM));
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
