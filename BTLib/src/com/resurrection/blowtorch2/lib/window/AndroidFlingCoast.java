/*
 * Copyright (C) BlowTorch contributors
 */
package com.resurrection.blowtorch2.lib.window;

/** Sign and threshold for OverScroller fling; no scroll-sensitivity gain. */
public final class AndroidFlingCoast {

	private AndroidFlingCoast() {
	}

	public static boolean shouldFling(final float absVy, final float minVy) {
		return absVy >= minVy;
	}

	public static int yVelocity(final float fingerVy, final boolean newestAtTop) {
		final float signed = newestAtTop ? -fingerVy : fingerVy;
		if (signed > Integer.MAX_VALUE) {
			return Integer.MAX_VALUE;
		}
		if (signed < Integer.MIN_VALUE) {
			return Integer.MIN_VALUE;
		}
		return (int) signed;
	}
}
