/*
 * Copyright (C) Dan Block 2013
 */
package com.resurrection.blowtorch2.lib.window;

import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/** Detects swipes and holds on a chrome view and reports the bound command.
 *
 * Sits alongside whatever the view already does rather than replacing it. It
 * returns false when a gesture did not fire, so taps, text selection in the
 * input bar and the click listeners all keep working; it only claims the event
 * once a swipe or hold has actually happened.
 */
public final class ChromeGestureTouchListener implements View.OnTouchListener {

	/** Matches the button grid, so the two feel the same. */
	private static final int SWIPE_THRESHOLD_DP = 24;

	/** Movement past this cancels a pending hold. */
	private static final int HOLD_CANCEL_MOVE_DP = 10;

	/** Receives the command a gesture resolved to. */
	public interface CommandSink {
		/** @param command the bound command to run. */
		void runChromeCommand(String command);
	}

	private final String target;
	private final CommandSink sink;
	private final Handler handler = new Handler();
	private final float threshold;
	private final float holdCancel;
	private final long holdDelay;

	private float startX;
	private float startY;
	private boolean holdFired;
	private boolean gestureFired;
	private Runnable pendingHold;

	public ChromeGestureTouchListener(final String target, final float density,
			final CommandSink sink) {
		this.target = target;
		this.sink = sink;
		this.threshold = SWIPE_THRESHOLD_DP * density;
		this.holdCancel = HOLD_CANCEL_MOVE_DP * density;
		this.holdDelay = ViewConfiguration.getLongPressTimeout();
	}

	@Override
	public boolean onTouch(final View v, final MotionEvent e) {
		switch (e.getActionMasked()) {
		case MotionEvent.ACTION_DOWN:
			startX = e.getX();
			startY = e.getY();
			holdFired = false;
			gestureFired = false;
			scheduleHold(v);
			return false;
		case MotionEvent.ACTION_MOVE:
			if (Math.abs(e.getX() - startX) > holdCancel
					|| Math.abs(e.getY() - startY) > holdCancel) {
				cancelHold();
			}
			return false;
		case MotionEvent.ACTION_UP:
			cancelHold();
			if (holdFired) {
				// The hold already ran; swallow the up so it does not also tap.
				holdFired = false;
				return true;
			}
			return fireSwipe(e.getX() - startX, e.getY() - startY);
		case MotionEvent.ACTION_CANCEL:
			cancelHold();
			holdFired = false;
			return false;
		default:
			return false;
		}
	}

	private void scheduleHold(final View v) {
		if (!ChromeGestures.supportsHold(target)) {
			return;
		}
		final String command = ChromeGestures.current().get(target, ChromeGestures.GESTURE_HOLD);
		if (command == null) {
			return;
		}
		cancelHold();
		pendingHold = new Runnable() {
			@Override
			public void run() {
				pendingHold = null;
				holdFired = true;
				gestureFired = true;
				v.performHapticFeedback(
						android.view.HapticFeedbackConstants.LONG_PRESS);
				sink.runChromeCommand(command);
			}
		};
		handler.postDelayed(pendingHold, holdDelay);
	}

	private void cancelHold() {
		if (pendingHold != null) {
			handler.removeCallbacks(pendingHold);
			pendingHold = null;
		}
	}

	private boolean fireSwipe(final float dx, final float dy) {
		if (Math.abs(dx) < threshold && Math.abs(dy) < threshold) {
			return false;
		}
		String direction;
		if (Math.abs(dx) >= Math.abs(dy)) {
			direction = dx > 0 ? "right" : "left";
		} else {
			direction = dy > 0 ? "down" : "up";
		}
		String command = ChromeGestures.current().get(target, direction);
		if (command == null) {
			return false;
		}
		gestureFired = true;
		sink.runChromeCommand(command);
		return true;
	}
}
