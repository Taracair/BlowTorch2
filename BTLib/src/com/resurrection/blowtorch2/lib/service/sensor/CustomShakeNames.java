package com.resurrection.blowtorch2.lib.service.sensor;

import java.util.Collection;

import com.resurrection.blowtorch2.lib.service.sensor.GestureCatalog.Gesture;

/**
 * A shake the player recorded. The id is {@code u:} plus the name, so
 * {@code !slash} stays ordinary trigger text and {@code !u:slash} is the gesture.
 */
public final class CustomShakeNames {

	public static final String PREFIX = "u:";

	private CustomShakeNames() {
	}

	public static boolean wanted(final Collection<String> ids) {
		if (ids == null) {
			return false;
		}
		for (String id : ids) {
			if (id != null && id.startsWith(PREFIX)) {
				return true;
			}
		}
		return false;
	}

	/** The gesture for this player name, or null when the name cannot be one. */
	public static Gesture gesture(final String name) {
		String id = CustomShakeLibrary.normalizeName(name);
		if (id == null || CustomShakeLibrary.isReserved(id)) {
			return null;
		}
		return new Gesture(PREFIX + id, "My shakes", id,
				"A shake you recorded on this phone. Same hardness as Shake the phone."
					+ " Needs linear acceleration.",
				GestureCatalog.BY_LINEAR_ACCELERATION);
	}
}
