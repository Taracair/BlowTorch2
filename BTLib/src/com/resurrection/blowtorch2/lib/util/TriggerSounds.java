package com.resurrection.blowtorch2.lib.util;

import java.io.File;
import java.util.HashMap;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.media.SoundPool;
import android.net.Uri;
import android.os.SystemClock;

/**
 * Short sounds fired by triggers and timers.
 *
 * <p>Separate from {@link NotificationSounds#play}, which builds a
 * {@code Ringtone} or a {@code MediaPlayer} per sound. That is fine for a
 * notification, which happens once; it is the wrong shape for a combat alert
 * that can fire six times a second, because preparing a player costs tens of
 * milliseconds on the thread the game text is being processed on. A
 * {@link SoundPool} decodes each sound once and every later firing is a
 * pointer.
 *
 * <p>Lives in whichever process the responder runs in, which is {@code :stellar}
 * — the same reason the speech engine lives there. A sound you cannot hear while
 * the screen is off is not an alert.
 */
public final class TriggerSounds {

	/** Streams at once. Four overlapping short sounds is already a mess to listen to. */
	private static final int MAX_STREAMS = 4;

	/**
	 * How long a sound asked for before it finished loading is still wanted.
	 *
	 * <p>Loading is asynchronous, so the very first firing of a sound would be
	 * silent. It is played on arrival instead — but only if the line that asked
	 * for it is still recent, or a slow first load would fire a stale alert into
	 * a fight that has moved on.
	 */
	private static final long PENDING_PLAY_WINDOW_MS = 2000;

	private static SoundPool sPool = null;

	/** Stored sound path to the id SoundPool gave it. */
	private static final HashMap<String, Integer> sLoaded = new HashMap<String, Integer>();

	/** Ids still decoding, and when they were asked for. */
	private static final HashMap<Integer, Long> sPending = new HashMap<Integer, Long>();

	/** Volume to use when a pending sound finally lands, by id. */
	private static final HashMap<Integer, Float> sPendingVolume = new HashMap<Integer, Float>();

	/** When each rate-limit key last made a noise. */
	private static final HashMap<String, Long> sLastPlayed = new HashMap<String, Long>();

	/** Paths that could not be loaded, so the log complains once and not per line. */
	private static final HashMap<String, Boolean> sFailed = new HashMap<String, Boolean>();

	private TriggerSounds() {
	}

	/**
	 * Play a sound, unless this key made a noise too recently.
	 *
	 * @param context any context; the application context is what is kept.
	 * @param soundPath as stored by the responder: a {@code bundled:} key, an
	 *        absolute file path, or a content URI.
	 * @param volume 0..1.
	 * @param rateKey what the gap is counted against — one trigger's sound, so
	 *        that two different triggers do not silence each other.
	 * @param minGapMs shortest gap between two firings of that key.
	 * @return true if a sound was started or queued, false if it was suppressed
	 *         or could not be loaded.
	 */
	public static synchronized boolean play(final Context context, final String soundPath,
			final float volume, final String rateKey, final int minGapMs) {
		if (context == null || soundPath == null || soundPath.length() == 0) {
			return false;
		}
		final long now = SystemClock.elapsedRealtime();
		if (minGapMs > 0 && rateKey != null) {
			Long last = sLastPlayed.get(rateKey);
			if (last != null && now - last.longValue() < minGapMs) {
				return false;
			}
		}
		Integer id = sLoaded.get(soundPath);
		if (id == null) {
			if (Boolean.TRUE.equals(sFailed.get(soundPath))) {
				return false;
			}
			id = load(context.getApplicationContext(), soundPath);
			if (id == null) {
				// Once per path per process: a missing file on a trigger that
				// matches every line would otherwise be the whole log.
				sFailed.put(soundPath, Boolean.TRUE);
				// The error file, not just logcat: a sound that has gone quiet
				// because the player moved the file is exactly the kind of thing
				// they will come looking for an explanation of, and the fix is
				// theirs to make.
				BlowTorchLogger.logThrowable("TriggerSounds",
						new java.io.FileNotFoundException(soundPath
							+ " — a trigger wants this sound and it is not there."
							+ " If it is your own file, it has been moved or"
							+ " deleted; pick it again in the trigger's Sound"
							+ " action."));
				return false;
			}
			sLoaded.put(soundPath, id);
			sPending.put(id, Long.valueOf(now));
			sPendingVolume.put(id, Float.valueOf(clamp(volume)));
		}
		if (rateKey != null) {
			sLastPlayed.put(rateKey, Long.valueOf(now));
		}
		if (sPending.containsKey(id)) {
			// Still decoding. onLoadComplete plays it if it is still wanted.
			sPending.put(id, Long.valueOf(now));
			sPendingVolume.put(id, Float.valueOf(clamp(volume)));
			return true;
		}
		float v = clamp(volume);
		sPool.play(id.intValue(), v, v, 1, 0, 1.0f);
		return true;
	}

	/**
	 * Is this sound something that can still be played?
	 *
	 * <p>For the editor, so a file the player has since deleted says so where
	 * they can do something about it, rather than going quiet mid-fight with the
	 * only evidence in the log.
	 *
	 * @param soundPath as stored by the responder.
	 * @return false when it names a file that is not there any more.
	 */
	public static boolean isAvailable(final String soundPath) {
		if (soundPath == null || soundPath.length() == 0) {
			return false;
		}
		if (NotificationSounds.isBundled(soundPath)) {
			return true;
		}
		if (soundPath.startsWith("content:") || soundPath.startsWith("android.resource:")) {
			// Only opening it would tell, and that is not a thing to do while
			// drawing a dialog. Treated as present.
			return true;
		}
		String path = soundPath.startsWith("file:")
				? Uri.parse(soundPath).getPath() : soundPath;
		if (path == null) {
			return false;
		}
		File f = new File(path);
		return f.isFile() && f.canRead();
	}

	/** Forget a path, so the next firing tries to load it again. */
	public static synchronized void forget(final String soundPath) {
		if (soundPath == null) {
			return;
		}
		sFailed.remove(soundPath);
		Integer id = sLoaded.remove(soundPath);
		if (id != null) {
			sPending.remove(id);
			sPendingVolume.remove(id);
			if (sPool != null) {
				try {
					sPool.unload(id.intValue());
				} catch (Exception e) {
					BlowTorchLogger.logMinor("TriggerSounds.forget", e);
				}
			}
		}
	}

	private static float clamp(final float v) {
		if (v < 0f) {
			return 0f;
		}
		if (v > 1f) {
			return 1f;
		}
		return v;
	}

	/** Hand the path to SoundPool in whichever of its three forms fits. */
	private static Integer load(final Context app, final String soundPath) {
		ensurePool();
		try {
			if (NotificationSounds.isBundled(soundPath)) {
				int res = NotificationSounds.bundledResId(soundPath);
				if (res == 0) {
					return null;
				}
				int id = sPool.load(app, res, 1);
				return id == 0 ? null : Integer.valueOf(id);
			}
			if (soundPath.startsWith("content:") || soundPath.startsWith("android.resource:")
					|| soundPath.startsWith("file:")) {
				AssetFileDescriptor afd =
						app.getContentResolver().openAssetFileDescriptor(Uri.parse(soundPath), "r");
				if (afd == null) {
					return null;
				}
				try {
					int id = sPool.load(afd, 1);
					return id == 0 ? null : Integer.valueOf(id);
				} finally {
					try {
						afd.close();
					} catch (Exception e) {
						BlowTorchLogger.logMinor("TriggerSounds.load", e);
					}
				}
			}
			File f = new File(soundPath);
			if (!f.isFile() || !f.canRead()) {
				return null;
			}
			int id = sPool.load(soundPath, 1);
			return id == 0 ? null : Integer.valueOf(id);
		} catch (Exception e) {
			BlowTorchLogger.logMinor("TriggerSounds.load", e);
			return null;
		}
	}

	@SuppressWarnings("deprecation")
	private static void ensurePool() {
		if (sPool != null) {
			return;
		}
		if (android.os.Build.VERSION.SDK_INT >= 21) {
			android.media.AudioAttributes attrs = new android.media.AudioAttributes.Builder()
					.setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
					.setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
					.build();
			sPool = new SoundPool.Builder()
					.setMaxStreams(MAX_STREAMS)
					.setAudioAttributes(attrs)
					.build();
		} else {
			sPool = new SoundPool(MAX_STREAMS, AudioManager.STREAM_NOTIFICATION, 0);
		}
		sPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
			@Override
			public void onLoadComplete(SoundPool pool, int sampleId, int status) {
				synchronized (TriggerSounds.class) {
					Long asked = sPending.remove(Integer.valueOf(sampleId));
					Float vol = sPendingVolume.remove(Integer.valueOf(sampleId));
					if (status != 0 || asked == null) {
						return;
					}
					if (SystemClock.elapsedRealtime() - asked.longValue()
							> PENDING_PLAY_WINDOW_MS) {
						// Loaded, but the line that wanted it has scrolled away.
						return;
					}
					float v = vol == null ? 1f : vol.floatValue();
					pool.play(sampleId, v, v, 1, 0, 1.0f);
				}
			}
		});
	}
}
