package com.resurrection.blowtorch2.lib.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import android.annotation.TargetApi;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.net.Uri;
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

	/**
	 * Channel id for a custom alert sound. Sound is baked into the channel on
	 * Android O+ (Builder.setSound is ignored for channel-backed notifications).
	 */
	public static String customAlertChannelId(Context context, Uri soundUri) {
		return baseLabel(context) + ALERT_SUFFIX + "_" + stableUriHash(soundUri);
	}

	private static String baseLabel(Context context) {
		return ConfigurationLoader.getConfigurationValue("ongoingNotificationLabel", context);
	}

	private static String stableUriHash(Uri soundUri) {
		String raw = soundUri != null ? soundUri.toString() : "";
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] dig = md.digest(raw.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder(16);
			for (int i = 0; i < 8; i++) {
				sb.append(String.format("%02x", dig[i] & 0xff));
			}
			return sb.toString();
		} catch (NoSuchAlgorithmException e) {
			return Integer.toHexString(raw.hashCode());
		}
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

	/**
	 * Silent alert channel for custom sounds played via {@link NotificationSounds#play}.
	 * Channel sound on O+ is often replaced by the system default for non-file URIs.
	 */
	@TargetApi(26)
	public static String ensureSilentCustomAlertChannel(Context context) {
		ensureChannels(context);
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
			return alertChannelId(context);
		}
		NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		if (nm == null) {
			return alertChannelId(context);
		}
		String id = alertChannelId(context) + "_custom_silent";
		NotificationChannel existing = nm.getNotificationChannel(id);
		if (existing == null) {
			String brand = baseLabel(context);
			NotificationChannel custom = new NotificationChannel(
					id,
					brand + " — custom alert",
					NotificationManager.IMPORTANCE_DEFAULT);
			custom.setShowBadge(true);
			custom.setSound(null, null);
			nm.createNotificationChannel(custom);
		} else if (existing.getSound() != null) {
			// Force silent if an older build created this channel with a sound.
			nm.deleteNotificationChannel(id);
			String brand = baseLabel(context);
			NotificationChannel custom = new NotificationChannel(
					id,
					brand + " — custom alert",
					NotificationManager.IMPORTANCE_DEFAULT);
			custom.setShowBadge(true);
			custom.setSound(null, null);
			nm.createNotificationChannel(custom);
		}
		return id;
	}

	/**
	 * Ensure the default alert channel (and session channel) exist, and when
	 * {@code soundUri} is non-null return the silent custom channel (caller must
	 * play the sound). Returns the channel id the notification should use.
	 */
	@TargetApi(26)
	public static String ensureAlertChannel(Context context, Uri soundUri) {
		ensureChannels(context);
		if (soundUri != null) {
			return ensureSilentCustomAlertChannel(context);
		}
		return alertChannelId(context);
	}
}
