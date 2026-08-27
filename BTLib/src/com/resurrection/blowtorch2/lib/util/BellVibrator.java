package com.resurrection.blowtorch2.lib.util;

import android.content.Context;
import android.os.Build;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;

/**
 * One-shot buzz for the bell. The service process is often treated as
 * background, so the UI process should call this; amplitude 255 because
 * {@code DEFAULT_AMPLITUDE} (-1) is a no-op on some phones.
 */
public final class BellVibrator {

	private BellVibrator() {
	}

	public static void vibrate(Context context, int durationMs, int amplitude) {
		if (context == null || durationMs < 1) {
			return;
		}
		Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
		if (vibrator == null || !vibrator.hasVibrator()) {
			return;
		}
		int amp = amplitude < 1 ? 255 : Math.min(255, amplitude);
		VibrationEffect effect = VibrationEffect.createOneShot(durationMs, amp);
		if (Build.VERSION.SDK_INT >= 33) {
			vibrator.vibrate(effect, VibrationAttributes.createForUsage(
					VibrationAttributes.USAGE_NOTIFICATION));
		} else {
			vibrator.vibrate(effect);
		}
	}
}
