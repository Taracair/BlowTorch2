package com.resurrection.blowtorch2.lib.window;

/**
 * A finger on a recent-command chip. A short lift sends that chip.
 * After the hold arms, the finger can move to another chip and the lift
 * sends that one. A second finger cancels the lift.
 */
public final class LastBarHold {

	private boolean tracking;
	private boolean armed;
	private boolean cancel;
	private boolean dragged;
	private int highlighted = -1;
	private int downX;
	private int downY;

	public void down(final int chip, final int x, final int y) {
		tracking = true;
		armed = false;
		cancel = false;
		dragged = false;
		highlighted = chip;
		downX = x;
		downY = y;
	}

	public boolean tracking() {
		return tracking;
	}

	public boolean armed() {
		return armed;
	}

	public boolean dragged() {
		return dragged;
	}

	public int chip() {
		return highlighted;
	}

	/**
	 * @param chip chip under the finger, or -1 in a gap or off the row
	 */
	public void move(final int chip, final int x, final int y, final int slop) {
		if (!tracking || cancel) {
			return;
		}
		if (!armed) {
			if (Math.abs(x - downX) > slop || Math.abs(y - downY) > slop) {
				dragged = true;
			}
			return;
		}
		if (chip >= 0) {
			highlighted = chip;
		}
	}

	/** Long-press timeout, and the finger has not already slid away. */
	public boolean arm() {
		if (!tracking || cancel || dragged || highlighted < 0) {
			return false;
		}
		armed = true;
		return true;
	}

	public void secondFinger() {
		if (!tracking) {
			return;
		}
		cancel = true;
		armed = false;
		highlighted = -1;
	}

	/** Child index to send, or -1. */
	public int up() {
		int send = -1;
		if (tracking && !cancel && !dragged && highlighted >= 0) {
			send = highlighted;
		}
		clear();
		return send;
	}

	public void cancelTouch() {
		clear();
	}

	/**
	 * @return the chip index, or -1 when the point is in a gap or outside
	 */
	public static int chipAt(final int x, final int y, final int[] left, final int[] top,
			final int[] right, final int[] bottom) {
		if (left == null || top == null || right == null || bottom == null
				|| left.length != top.length || left.length != right.length
				|| left.length != bottom.length) {
			return -1;
		}
		for (int i = 0; i < left.length; i++) {
			if (x >= left[i] && x < right[i] && y >= top[i] && y < bottom[i]) {
				return i;
			}
		}
		return -1;
	}

	private void clear() {
		tracking = false;
		armed = false;
		cancel = false;
		dragged = false;
		highlighted = -1;
	}
}
