/*
 * Copyright (C) BlowTorch contributors
 */
package com.resurrection.blowtorch2.lib.window;

/**
 * Scroll gain as a percent. Profiles written before this stored a list index
 * (0–8 in window XML, 1–9 in extra-text JSON with 0 = inherit).
 */
public final class ScrollSensitivity {

	private ScrollSensitivity() {
	}

	public static final int DEFAULT_PERCENT = 100;
	public static final int[] ALLOWED = {
		50, 75, 100, 150, 200, 300, 350, 400, 450, 500
	};
	private static final int[] OLD_INDEX = {
		75, 100, 150, 200, 300, 350, 400, 450, 500
	};

	public static String[] labels() {
		String[] out = new String[ALLOWED.length];
		for (int i = 0; i < ALLOWED.length; i++) {
			out[i] = ALLOWED[i] + "%";
		}
		return out;
	}

	/** Window XML / ListOption: old 0–8 is an index; 50–500 is percent. */
	public static int migrateWindowXml(final int raw) {
		if (raw >= 0 && raw <= 8) {
			return OLD_INDEX[raw];
		}
		if (raw >= 50 && raw <= 500) {
			return snapAllowed(raw);
		}
		return DEFAULT_PERCENT;
	}

	/** Extra-text JSON: 0 inherit, old 1–9 is index+1, 50–500 is percent. */
	public static int migrateExtraTextJson(final int raw) {
		if (raw == 0) {
			return 0;
		}
		if (raw >= 1 && raw <= 9) {
			return OLD_INDEX[raw - 1];
		}
		if (raw >= 50 && raw <= 500) {
			return snapAllowed(raw);
		}
		return 0;
	}

	public static int indexOf(final int raw) {
		int p = migrateWindowXml(raw);
		for (int i = 0; i < ALLOWED.length; i++) {
			if (ALLOWED[i] == p) {
				return i;
			}
		}
		return indexOfExact(DEFAULT_PERCENT);
	}

	public static int extraTextSpinnerIndex(final int stored) {
		if (stored == 0) {
			return 0;
		}
		return indexOf(stored) + 1;
	}

	public static int extraTextStoredFromSpinner(final int position) {
		if (position <= 0 || position > ALLOWED.length) {
			return 0;
		}
		return ALLOWED[position - 1];
	}

	public static float gain(final Integer raw) {
		if (raw == null) {
			return 1.0f;
		}
		return migrateWindowXml(raw.intValue()) / 100.0f;
	}

	private static int snapAllowed(final int raw) {
		int best = ALLOWED[0];
		int bestDist = Math.abs(raw - best);
		for (int i = 1; i < ALLOWED.length; i++) {
			int d = Math.abs(raw - ALLOWED[i]);
			if (d < bestDist) {
				best = ALLOWED[i];
				bestDist = d;
			}
		}
		return best;
	}

	private static int indexOfExact(final int p) {
		for (int i = 0; i < ALLOWED.length; i++) {
			if (ALLOWED[i] == p) {
				return i;
			}
		}
		return 2;
	}
}
