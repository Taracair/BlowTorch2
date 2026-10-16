package com.resurrection.blowtorch2.lib.button;

/**
 * Magenta {@code #FF00FF} (and JPEG-near magenta) becomes transparent.
 * PNG alpha is left alone.
 */
public final class ButtonChromaKey {

	private ButtonChromaKey() {
	}

	public static boolean isMagenta(final int argb) {
		int r = (argb >> 16) & 0xFF;
		int g = (argb >> 8) & 0xFF;
		int b = argb & 0xFF;
		return r >= 240 && g <= 16 && b >= 240;
	}

	public static void keyInto(final int[] pixels) {
		if (pixels == null) {
			return;
		}
		for (int i = 0; i < pixels.length; i++) {
			if (isMagenta(pixels[i])) {
				pixels[i] = 0;
			}
		}
	}
}
