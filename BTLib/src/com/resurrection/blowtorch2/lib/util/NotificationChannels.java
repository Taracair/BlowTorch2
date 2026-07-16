package com.resurrection.blowtorch2.lib.util;

import android.annotation.TargetApi;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import com.resurrection.blowtorch2.lib.settings.ConfigurationLoader;

/** Separate channels for quiet session status vs noisy alerts/triggers. */
public final class NotificationChannels {

	public static final String SESSION_SUFFIX = "_session";
	public static final String ALERT_SUFFIX = "_alerts";

	private NotificationChannels() {
	}

	public static String sessionChannelId(Context context) {
		return baseLabel(context) + SESSION_SUFFIX;
	}

	public static String alertChannelId(Context context) {
		return baseLabel(context) + ALERT_SUFFIX;
	}

	private static String baseLabel(Context context) {
		return ConfigurationLoader.getConfigurationValue("ongoingNotificationLabel", context);
	}

	@TargetApi(26)
	public static void ensureChannels(Context context) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
			return;
		}
		NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		if (nm == null) {
			return;
		}
		String brand = baseLabel(context);

		NotificationChannel session = new NotificationChannel(
				sessionChannelId(context),
				brand + " — session",
				NotificationManager.IMPORTANCE_LOW);
		session.setShowBadge(false);
		session.setSound(null, null);
		session.enableVibration(false);
		nm.createNotificationChannel(session);

		NotificationChannel alerts = new NotificationChannel(
				alertChannelId(context),
				brand + " — alerts",
				NotificationManager.IMPORTANCE_DEFAULT);
		alerts.setShowBadge(true);
		nm.createNotificationChannel(alerts);
	}
}
