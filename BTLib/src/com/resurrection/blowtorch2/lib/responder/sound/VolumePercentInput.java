package com.resurrection.blowtorch2.lib.responder.sound;

/** Whether the Play a Sound volume field may hold this text (0–100). */
public final class VolumePercentInput {

	private VolumePercentInput() {
	}

	public static boolean allow(final String next) {
		if (next == null || next.length() == 0) {
			return true;
		}
		try {
			int n = Integer.parseInt(next);
			return n >= 0 && n <= 100;
		} catch (NumberFormatException e) {
			return false;
		}
	}
}
