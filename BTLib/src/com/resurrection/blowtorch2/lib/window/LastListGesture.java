package com.resurrection.blowtorch2.lib.window;

/**
 * A tap on a recent-command row. The list scrolls on its own: once that
 * scroll starts, the lift does not send. A second finger outside the list
 * cancels the tap. A second finger on the list while the frame is minimal
 * brings the frame back and does not send.
 */
public final class LastListGesture {

	private boolean tracking;
	private boolean cancel;
	private int highlighted = -1;

	public void down(final int row) {
		tracking = true;
		cancel = false;
		highlighted = row;
	}

	public boolean tracking() {
		return tracking;
	}

	public int row() {
		return highlighted;
	}

	/**
	 * @param exitFrame second finger landed on the list while the frame is minimal
	 * @param cancelTap second finger landed outside the list
	 */
	public void secondFinger(final boolean exitFrame, final boolean cancelTap) {
		if (!tracking) {
			return;
		}
		if (exitFrame || cancelTap) {
			cancel = true;
			highlighted = -1;
		}
	}

	/** 1-based row to send, or 0 when the lift should send nothing. */
	public int up() {
		int send = 0;
		if (tracking && !cancel && highlighted >= 0) {
			send = highlighted + 1;
		}
		clear();
		return send;
	}

	/** The list took the drag, or the finger was cancelled. */
	public void cancelTouch() {
		clear();
	}

	/** Second finger on the list while the frame is hidden. */
	public static boolean exitFrame(final boolean minimal, final boolean insideList) {
		return minimal && insideList;
	}

	/** Second finger outside the list while a tappable row is held. */
	public static boolean cancelTap(final boolean tappable, final boolean insideList) {
		return tappable && !insideList;
	}

	public static boolean contains(final int x, final int y, final int left, final int top,
			final int right, final int bottom) {
		return x >= left && x < right && y >= top && y < bottom;
	}

	/** Pointer N's screen position. Local coordinates shift when the list scrolls. */
	public static int screenAxis(final float raw0, final float local0, final float localN) {
		return Math.round(localN + (raw0 - local0));
	}

	private void clear() {
		tracking = false;
		cancel = false;
		highlighted = -1;
	}
}
