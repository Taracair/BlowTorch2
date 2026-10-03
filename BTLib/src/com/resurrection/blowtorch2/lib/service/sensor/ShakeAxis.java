package com.resurrection.blowtorch2.lib.service.sensor;

import java.util.Collection;

/**
 * Which way a linear-acceleration sample points. +X is to the right of the
 * screen, +Y toward the top. The sample is the one that crossed the shake
 * threshold, not a measured peak of the whole shake.
 */
public final class ShakeAxis {

	private ShakeAxis() {
	}

	/**
	 * {@code l} {@code r} {@code u} {@code d}, or null when Z is loudest or
	 * neither in-plane axis is at least 1.5× the other.
	 */
	public static String letter(final float x, final float y, final float z) {
		float ax = Math.abs(x);
		float ay = Math.abs(y);
		float az = Math.abs(z);
		float plane = Math.max(ax, ay);
		if (plane <= 0f || az >= plane) {
			return null;
		}
		if (ax >= ay) {
			if (ay > 0f && ax < ay * 1.5f) {
				return null;
			}
			return x >= 0f ? "r" : "l";
		}
		if (ax > 0f && ay < ax * 1.5f) {
			return null;
		}
		return y >= 0f ? "u" : "d";
	}

	public static String gestureId(final String letter) {
		if ("l".equals(letter)) {
			return "shakeleft";
		}
		if ("r".equals(letter)) {
			return "shakeright";
		}
		if ("u".equals(letter)) {
			return "shakeup";
		}
		if ("d".equals(letter)) {
			return "shakedown";
		}
		return null;
	}

	public static boolean wantsPattern(final Collection<String> wanted) {
		if (wanted == null) {
			return false;
		}
		for (String id : wanted) {
			if (id != null && id.startsWith("pat:")) {
				return true;
			}
		}
		return false;
	}
}
