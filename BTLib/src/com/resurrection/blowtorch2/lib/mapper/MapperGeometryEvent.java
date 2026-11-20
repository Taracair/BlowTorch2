package com.resurrection.blowtorch2.lib.mapper;

/**
 * Payload for the window callback {@code OnMapperGeometry}. One string because
 * window Lua calls take one argument. A float {@code will} may answer with
 * four numbers, {@code x,y,w,h}, to replace that frame before it is written.
 */
public final class MapperGeometryEvent {

	private MapperGeometryEvent() {}

	public static String message(String phase, boolean fullscreen,
			int x, int y, int width, int height) {
		return phase + "\t" + (fullscreen ? "full" : "float")
				+ "\t" + x + "\t" + y + "\t" + width + "\t" + height;
	}

	/**
	 * @return x, y, width, height, or null when the script left the frame alone
	 */
	public static int[] floatAdjustment(String returned) {
		if (returned == null) {
			return null;
		}
		String trimmed = returned.trim();
		if (trimmed.length() == 0) {
			return null;
		}
		String[] parts = trimmed.split("[,\\s]+");
		if (parts.length != 4) {
			return null;
		}
		try {
			int x = Integer.parseInt(parts[0]);
			int y = Integer.parseInt(parts[1]);
			int width = Integer.parseInt(parts[2]);
			int height = Integer.parseInt(parts[3]);
			if (width <= 0 || height <= 0) {
				return null;
			}
			return new int[] { x, y, width, height };
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
