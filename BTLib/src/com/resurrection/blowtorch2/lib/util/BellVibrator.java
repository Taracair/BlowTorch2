package com.resurrection.blowtorch2.lib.util;

import android.content.Context;
import android.os.Build;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;

/**
 * Bell and trigger-notification buzz. Amplitude 255 because
 * {@code DEFAULT_AMPLITUDE} (-1) is a no-op on some phones. The service
 * process is often treated as background; {@code .dobell} still prefers the
 * UI process while the game window is showing.
 */
public final class BellVibrator {

	private BellVibrator() {
	}

	public static void vibrate(Context context, int durationMs, int amplitude) {
		if (context == null || durationMs < 1) {
			return;
		}
		Vibrator vibrator = vibratorOf(context);
		if (vibrator == null) {
			return;
		}
		int amp = clampAmp(amplitude);
		play(vibrator, VibrationEffect.createOneShot(durationMs, amp));
	}

	/**
	 * Off-on timings in the old {@code Notification.vibrate} shape:
	 * {@code {delay, buzz, gap, buzz, …}}. Amplitude 255 — {@code DEFAULT_AMPLITUDE}
	 * is a no-op on some phones.
	 */
	public static void waveform(Context context, long[] offOn) {
		if (context == null || offOn == null || offOn.length < 2) {
			return;
		}
		Vibrator vibrator = vibratorOf(context);
		if (vibrator == null) {
			return;
		}
		int amp = clampAmp(255);
		int[] amps = new int[offOn.length];
		for (int i = 0; i < offOn.length; i++) {
			amps[i] = (i % 2 == 1) ? amp : 0;
		}
		play(vibrator, VibrationEffect.createWaveform(offOn, amps, -1));
	}

	/**
	 * {@code count} short pulses with gaps. Distinct from a single long buzz:
	 * short vs long is easy to miss in a pocket; three taps is not.
	 */
	public static void burst(Context context, int pulseMs, int gapMs, int count,
			int amplitude) {
		if (context == null || pulseMs < 1 || count < 1) {
			return;
		}
		Vibrator vibrator = vibratorOf(context);
		if (vibrator == null) {
			return;
		}
		int amp = clampAmp(amplitude);
		int n = Math.min(count, 8);
		int gap = Math.max(0, gapMs);
		long[] timings = new long[n * 2 - 1];
		int[] amps = new int[timings.length];
		for (int i = 0; i < n; i++) {
			int pulseAt = i * 2;
			timings[pulseAt] = pulseMs;
			amps[pulseAt] = amp;
			if (i + 1 < n) {
				timings[pulseAt + 1] = gap;
				amps[pulseAt + 1] = 0;
			}
		}
		play(vibrator, VibrationEffect.createWaveform(timings, amps, -1));
	}

	private static Vibrator vibratorOf(Context context) {
		Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
		if (vibrator == null || !vibrator.hasVibrator()) {
			return null;
		}
		return vibrator;
	}

	private static int clampAmp(int amplitude) {
		return amplitude < 1 ? 255 : Math.min(255, amplitude);
	}

	private static void play(Vibrator vibrator, VibrationEffect effect) {
		if (Build.VERSION.SDK_INT >= 33) {
			vibrator.vibrate(effect, VibrationAttributes.createForUsage(
					VibrationAttributes.USAGE_NOTIFICATION));
		} else {
			vibrator.vibrate(effect);
		}
	}
}
