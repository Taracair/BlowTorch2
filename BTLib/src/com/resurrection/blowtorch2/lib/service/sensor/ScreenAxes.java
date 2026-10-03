package com.resurrection.blowtorch2.lib.service.sensor;

/**
 * Device linear acceleration into the screen the player is looking at.
 * {@code +X} is right, {@code +Y} is up, {@code +Z} is toward the player.
 * Rotation values match {@code android.view.Surface}: 0, 90, 180, 270.
 *
 * <p>The axes are the ones {@code SensorManager.remapCoordinateSystem} uses
 * for those rotations (screen X along device Y at 90 degrees, and so on).
 * Z is unchanged; the rotation is around the screen normal.
 */
public final class ScreenAxes {

	public static final int ROTATION_0 = 0;
	public static final int ROTATION_90 = 1;
	public static final int ROTATION_180 = 2;
	public static final int ROTATION_270 = 3;

	private ScreenAxes() {
	}

	public static void toScreen(final int rotation, final float x, final float y,
			final float z, final float[] out) {
		switch (rotation) {
			case ROTATION_90:
				out[0] = y;
				out[1] = -x;
				out[2] = z;
				return;
			case ROTATION_180:
				out[0] = -x;
				out[1] = -y;
				out[2] = z;
				return;
			case ROTATION_270:
				out[0] = -y;
				out[1] = x;
				out[2] = z;
				return;
			default:
				out[0] = x;
				out[1] = y;
				out[2] = z;
		}
	}
}
