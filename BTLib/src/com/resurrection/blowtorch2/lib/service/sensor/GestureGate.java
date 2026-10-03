package com.resurrection.blowtorch2.lib.service.sensor;

import com.resurrection.blowtorch2.lib.service.sensor.GestureCatalog.Gesture;

/**
 * Whether one gesture id may fire into one world. Screen and background are
 * already resolved by the caller; this only applies the per-world switches.
 */
public final class GestureGate {

	private GestureGate() {
	}

	public static boolean isBuiltinDirection(final String id) {
		return "shakeleft".equals(id) || "shakeright".equals(id)
				|| "shakeup".equals(id) || "shakedown".equals(id);
	}

	public static boolean isShakePattern(final String id) {
		return id != null && id.startsWith("pat:");
	}

	/**
	 * @param masterOn false silences every reading on the Sensors list, including
	 *        headphones and the screen
	 * @param myShakes when true, the four directions and {@code pat:} patterns
	 *        stay quiet and a recorded shake may fire. {@code shake} itself is
	 *        left to its own trigger.
	 * @param backgroundOk false when movement must be held because the app is not
	 *        in front
	 * @param screenOk false when movement must be held because the display is asleep
	 */
	public static boolean allow(final String id, final boolean masterOn, final boolean myShakes,
			final boolean backgroundOk, final boolean screenOk) {
		if (!masterOn || id == null) {
			return false;
		}
		Gesture g = GestureCatalog.byId(id);
		if (g == null) {
			return false;
		}
		boolean custom = id.startsWith(CustomShakeNames.PREFIX);
		if (myShakes && (isBuiltinDirection(id) || isShakePattern(id))) {
			return false;
		}
		if (!myShakes && custom) {
			return false;
		}
		if (g.getProviders().contains(GestureCatalog.BY_SYSTEM)) {
			return true;
		}
		return backgroundOk && screenOk;
	}
}
