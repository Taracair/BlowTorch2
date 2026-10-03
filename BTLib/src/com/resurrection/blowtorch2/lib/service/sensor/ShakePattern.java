package com.resurrection.blowtorch2.lib.service.sensor;

import java.util.Locale;

/** A typed shake pattern {@code pat:lr}. Two to eight of l, r, u, d. */
public final class ShakePattern {

	private ShakePattern() {
	}

	public static String normalize(final String raw) {
		if (raw == null) {
			return null;
		}
		String s = raw.trim().toLowerCase(Locale.US);
		if (s.length() < 2 || s.length() > 8) {
			return null;
		}
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c != 'l' && c != 'r' && c != 'u' && c != 'd') {
				return null;
			}
		}
		return s;
	}

	public static GestureCatalog.Gesture gesture(final String raw) {
		String seq = normalize(raw);
		if (seq == null) {
			return null;
		}
		return new GestureCatalog.Gesture("pat:" + seq, GestureCatalog.GROUP_MOVEMENT,
				"Shake pattern " + seq.toUpperCase(Locale.US),
				"Shake " + seq.toUpperCase(Locale.US)
					+ " in order, within a few seconds. Same hardness as Shake.",
				GestureCatalog.BY_LINEAR_ACCELERATION);
	}
}
