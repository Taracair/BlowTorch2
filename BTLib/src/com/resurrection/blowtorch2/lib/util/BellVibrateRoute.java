package com.resurrection.blowtorch2.lib.util;

/**
 * Where a bell buzz runs. Android-free so the service/UI split is JVM-tested.
 */
public final class BellVibrateRoute {

	private BellVibrateRoute() {
	}

	/**
	 * UI process may be frozen; FGS vibrates here when the window is not showing.
	 */
	public static boolean inService(final boolean windowShowing,
			final int uiCallbackCount) {
		return !windowShowing || uiCallbackCount < 1;
	}
}
