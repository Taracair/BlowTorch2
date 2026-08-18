/*
 * Copyright (C) BlowTorch contributors
 */
package com.resurrection.blowtorch2.lib.gauge;

import com.resurrection.blowtorch2.lib.window.SuperButtonGestures;

/**
 * Sticky edit-mode policy for overlay gauges. No Android — same extract/test
 * pattern as {@link SuperButtonGestures} / {@link GaugeResizeGrip}.
 *
 * <p>Not editing: the widget is fixed (no resize grip). Tap and swipe still
 * fire on release. A stationary press of {@link #EDIT_HOLD_MS} enters edit
 * and does <em>not</em> fire hold — long-press is how you pick the widget up.
 *
 * <p>Editing: drag (except the grip) moves; the grip resizes. A short tap
 * exits without firing the tap command. A second long-press also exits.
 */
public final class GaugeWidgetEditGestures {

	/** Same as {@link SuperButtonGestures#HOLD_DELAY_MS} (not the 2s move-hold). */
	public static final int EDIT_HOLD_MS = SuperButtonGestures.HOLD_DELAY_MS;

	private GaugeWidgetEditGestures() {
	}

	/**
	 * Arm the delayed enter-edit / exit-edit runnable on finger-down. Grip
	 * resize while already editing does not use the timer.
	 */
	public static boolean shouldArmEditHoldTimer(final boolean editing,
			final boolean hitGrip) {
		return !(editing && hitGrip);
	}

	/** Resize grip is only live in edit mode. */
	public static boolean shouldArmResize(final boolean editing, final boolean hitGrip) {
		return editing && hitGrip;
	}

	/**
	 * True when a stationary press of {@link #EDIT_HOLD_MS} should enter edit
	 * (finger still down). Hold command never fires from this gesture.
	 */
	public static boolean shouldEnterEditOnHold(final boolean editing,
			final boolean cancelled, final boolean movedTooFar,
			final long durationMs) {
		return !editing && !cancelled && !movedTooFar && durationMs >= EDIT_HOLD_MS;
	}

	/**
	 * True when a stationary press of {@link #EDIT_HOLD_MS} while already
	 * editing should leave edit mode.
	 */
	public static boolean shouldExitEditOnHold(final boolean editing,
			final boolean cancelled, final boolean movedTooFar,
			final long durationMs) {
		return editing && !cancelled && !movedTooFar && durationMs >= EDIT_HOLD_MS;
	}

	/**
	 * Short release in edit mode with no drag/resize: leave edit, do not fire
	 * tap/swipe/hold commands.
	 */
	public static boolean shouldExitEditOnTap(final boolean editing,
			final boolean cancelled, final boolean dragged, final boolean resized) {
		return editing && !cancelled && !dragged && !resized;
	}

	/** Commands (tap/swipe/hold) only run when not editing. */
	public static boolean shouldDispatchCommands(final boolean editing,
			final boolean cancelled) {
		return !editing && !cancelled;
	}

	/**
	 * Hold is the enter-edit gesture. Never fire {@code holdCommand} from a
	 * gauge press.
	 */
	public static boolean shouldFireHold() {
		return false;
	}

	/** Draw the resize triangle and edit border only while editing. */
	public static boolean shouldDrawEditChrome(final boolean editing) {
		return editing;
	}
}
