/*
 * Copyright (C) BlowTorch contributors
 */
package com.resurrection.blowtorch2.lib.gauge;

/**
 * First-layout placement for a widget that has never been positioned. No
 * Android. Stored {@link #UNPLACED} on both x and y means "centre in the
 * parent once it has a size", distinct from a real top-left of {@code 0,0}.
 */
public final class GaugeSpawnPlacement {

	/**
	 * Same sentinel floating buttons use ({@code FloatingLayerGeometry.UNPLACED}).
	 * A saved {@code 0,0} is a real corner and must not be recentred.
	 */
	public static final int UNPLACED = -1;

	private GaugeSpawnPlacement() {
	}

	/** True when both axes still carry {@link #UNPLACED}. */
	public static boolean isUnplaced(final int x, final int y) {
		return x == UNPLACED && y == UNPLACED;
	}

	/**
	 * Top-left that centres a widget in a parent. Negative when the widget is
	 * larger than the parent; callers clamp.
	 *
	 * @return {@code int[]{x, y}} in the same units as the arguments
	 */
	public static int[] center(final int parentW, final int parentH,
			final int widgetW, final int widgetH) {
		int x = (parentW - widgetW) / 2;
		int y = (parentH - widgetH) / 2;
		return new int[] { x, y };
	}
}
