package com.resurrection.blowtorch2.lib.util;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import com.resurrection.blowtorch2.lib.R;

/** Bundled trigger-notification sounds and path → Uri resolution. */
public final class NotificationSounds {

	public static final String PREFIX_BUNDLED = "bundled:";

	/** Soft / mid / loud presets shipped in {@code res/raw}. */
	public static final SoundPreset[] BUNDLED = {
			new SoundPreset("soft_chime", "Soft chime", R.raw.notif_soft_chime),
			new SoundPreset("soft_tap", "Soft tap", R.raw.notif_soft_tap),
			new SoundPreset("mid_ping", "Mid ping", R.raw.notif_mid_ping),
			new SoundPreset("mid_pluck", "Mid pluck", R.raw.notif_mid_pluck),
			new SoundPreset("loud_alert", "Loud alert", R.raw.notif_loud_alert),
	};

	private NotificationSounds() {
	}

	public static String bundledPath(String key) {
		return PREFIX_BUNDLED + key;
	}

	public static boolean isBundled(String path) {
		return path != null && path.startsWith(PREFIX_BUNDLED);
	}

	public static String bundledKey(String path) {
		if (!isBundled(path)) {
			return null;
		}
		return path.substring(PREFIX_BUNDLED.length());
	}

	public static String displayLabel(String path) {
		if (path == null || path.isEmpty()) {
			return "Default";
		}
		String key = bundledKey(path);
		if (key != null) {
			for (SoundPreset p : BUNDLED) {
				if (p.key.equals(key)) {
					return p.label;
				}
			}
			return key;
		}
		int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf(':'));
		if (slash >= 0 && slash + 1 < path.length()) {
			return path.substring(slash + 1);
		}
		return path;
	}

	/** Resolve stored soundPath to a playable Uri, or null for system default. */
	public static Uri resolveUri(Context context, String soundPath) {
		if (soundPath == null || soundPath.isEmpty()) {
			return null;
		}
		String key = bundledKey(soundPath);
		if (key != null) {
			for (SoundPreset p : BUNDLED) {
				if (p.key.equals(key)) {
					return new Uri.Builder()
							.scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
							.authority(context.getPackageName())
							.appendPath(String.valueOf(p.rawResId))
							.build();
				}
			}
			return null;
		}
		if (soundPath.startsWith("content:") || soundPath.startsWith("file:")
				|| soundPath.startsWith("android.resource:")) {
			return Uri.parse(soundPath);
		}
		return Uri.parse("file://" + soundPath);
	}

	/**
	 * Play a notification sound immediately. Used because Android O+ channel sounds
	 * are unreliable for custom/bundled URIs (OEM often substitutes the system sound).
	 */
	public static void play(Context context, Uri uri) {
		if (context == null || uri == null) {
			return;
		}
		try {
			android.media.Ringtone ringtone = android.media.RingtoneManager.getRingtone(
					context.getApplicationContext(), uri);
			if (ringtone != null) {
				if (android.os.Build.VERSION.SDK_INT >= 28) {
					ringtone.setLooping(false);
				}
				ringtone.play();
				return;
			}
		} catch (Exception ignored) {
		}
		android.media.MediaPlayer player = null;
		try {
			player = new android.media.MediaPlayer();
			player.setDataSource(context, uri);
			player.setAudioStreamType(android.media.AudioManager.STREAM_NOTIFICATION);
			player.setOnCompletionListener(new android.media.MediaPlayer.OnCompletionListener() {
				@Override
				public void onCompletion(android.media.MediaPlayer mp) {
					try {
						mp.release();
					} catch (Exception ignored) {
					}
				}
			});
			player.prepare();
			player.start();
		} catch (Exception e) {
			if (player != null) {
				try {
					player.release();
				} catch (Exception ignored) {
				}
			}
		}
	}

	public static final class SoundPreset {
		public final String key;
		public final String label;
		public final int rawResId;

		public SoundPreset(String key, String label, int rawResId) {
			this.key = key;
			this.label = label;
			this.rawResId = rawResId;
		}
	}
}
