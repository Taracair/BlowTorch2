package com.resurrection.blowtorch2.lib.util;

import android.content.Context;

/**
 * Empty stand-in. The production flavor compiles this file, and that is the
 * flavor F-Droid builds, so the timing writer is not in that APK.
 */
public final class TimingTrace {

	public static final String EXTRA_LAUNCH_UP = "bt.timing.launchUp";
	public static final String EXTRA_LAUNCH_SRC = "bt.timing.launchSrc";

	private TimingTrace() {
	}

	public static boolean on() {
		return false;
	}

	public static void arm(final Context context) {
	}

	public static void event(final String kind, final String fields) {
	}

	public static void markLeave() {
	}

	public static void noteStopped() {
	}

	public static int stoppedFlag() {
		return 0;
	}

	public static long sinceLeave(final long now) {
		return -1L;
	}

	public static void noteResumeEnter(final long up) {
	}

	public static void noteLaunch(final long up, final String src) {
	}

	public static void expectPaint(final String reason, final long fromUp) {
	}

	public static long paintStart(final String windowName) {
		return 0L;
	}

	public static void paint(final long drawStart, final int hw, final int dbg,
			final int tiles, final int bake, final int typeset) {
	}
}
