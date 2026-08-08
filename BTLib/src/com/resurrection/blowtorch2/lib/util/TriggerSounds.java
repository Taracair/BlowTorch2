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
	 * The media stream — the one a game belongs on.
	 *
	 * <p>The default, and the reason is a measurement: the first build played on
	 * the notification stream and the maintainer heard nothing at all, because a
	 * muted ringer mutes it. Nobody turns their ringer on for a game. This is the
	 * volume the phone's side buttons reach for while an app is in front of you.
	 *
	 * <p>These are indexes into the option's item list, so items are added in
	 * this order and nothing is inserted in the middle.
	 */
	public static final int STREAM_MEDIA = 0;

	/** The notification stream: follows the ringer and is silenced with it. */
	public static final int STREAM_NOTIFICATION = 1;

	/**
	 * The alarm stream. Loudest and hardest to silence by accident — Do Not
	 * Disturb usually lets it through — for the one trigger that must never be
	 * missed.
	 */
	public static final int STREAM_ALARM = 2;

	public static final int DEFAULT_STREAM = STREAM_MEDIA;

	/** How often at most the "you cannot hear this" toast may appear. */
	private static final long WARN_GAP_MS = 30000;

	private static int sStream = DEFAULT_STREAM;

	private static boolean sWarnWhenSilent = true;

	private static long sLastWarned = 0;

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
	 * Which stream trigger sounds play on.
	 *
	 * <p>Changing it rebuilds the pool, because {@link SoundPool} fixes its audio
	 * attributes at construction and there is no setter. Everything the old pool
	 * handed out goes with it: a sample id belongs to the pool that issued it, so
	 * keeping the cache across a rebuild would play ids into a released pool and
	 * do it silently.
	 *
	 * @param stream one of {@link #STREAM_MEDIA}, {@link #STREAM_NOTIFICATION},
	 *        {@link #STREAM_ALARM}. Anything else is treated as the default.
	 */
	public static synchronized void setStream(final int stream) {
		int wanted = (stream == STREAM_NOTIFICATION || stream == STREAM_ALARM)
				? stream : DEFAULT_STREAM;
		if (wanted == sStream && sPool != null) {
			return;
		}
		sStream = wanted;
		if (sPool != null) {
			try {
				sPool.release();
			} catch (Exception e) {
				BlowTorchLogger.logMinor("TriggerSounds.setStream", e);
			}
			sPool = null;
		}
		// Every id in here was issued by the pool just released.
		sLoaded.clear();
		sPending.clear();
		sPendingVolume.clear();
		sFailed.clear();
	}

	public static synchronized int getStream() {
		return sStream;
	}

	/**
	 * Whether to say so when the chosen stream is turned all the way down.
	 *
	 * <p>The failure this exists for has no other symptom: the trigger fires,
	 * the code plays, and nothing comes out. It cost a session to work out once
	 * already.
	 *
	 * @param on true to warn.
	 */
	public static synchronized void setWarnWhenSilent(final boolean on) {
		sWarnWhenSilent = on;
	}

	/** The Android stream constant behind the current setting. */
	private static int androidStream() {
		if (sStream == STREAM_NOTIFICATION) {
			return AudioManager.STREAM_NOTIFICATION;
		}
		if (sStream == STREAM_ALARM) {
			return AudioManager.STREAM_ALARM;
		}
		return AudioManager.STREAM_MUSIC;
	}

	/** What to call it when telling the player which volume to turn up. */
	private static String streamName() {
		if (sStream == STREAM_NOTIFICATION) {
			return "notification";
		}
		if (sStream == STREAM_ALARM) {
			return "alarm";
		}
		return "media";
	}

	/**
	 * Say so, at most every {@link #WARN_GAP_MS}, when nothing can be heard.
	 *
	 * <p>Asks about the stream actually in use rather than the ringer. Warning
	 * about the ringer while playing on media would be advice that does not fix
	 * anything, which is worse than no advice.
	 */
	private static void warnIfInaudible(final Context context) {
		warnIfStreamSilent(context, androidStream(), streamName(),
				"A trigger played a sound", ". Turn it up, or change the stream"
					+ " with .sound stream");
	}

	/**
	 * Say so, at most once every {@link #WARN_GAP_MS}, when a trigger made a
	 * noise into a volume that is turned off.
	 *
	 * <p>Shared with the speech action rather than written twice. Both fail the
	 * same silent way — the trigger fires, the code plays or speaks, nothing
	 * comes out — and a player with one of each would otherwise get two toasts
	 * with two different wordings on two independent timers.
	 *
	 * <p>Asks about the stream actually in use. Warning about the ringer while
	 * playing on media would be advice that fixes nothing, which is worse than
	 * no advice.
	 *
	 * @param context any context.
	 * @param androidStream the {@code AudioManager.STREAM_*} being played into.
	 * @param name what to call it to the player.
	 * @param what did the talking, for the first half of the sentence.
	 * @param advice what to do about it, for the last half.
	 */
	public static synchronized void warnIfStreamSilent(final Context context,
			final int androidStream, final String name, final String what,
			final String advice) {
		if (!sWarnWhenSilent || context == null) {
			return;
		}
		long now = SystemClock.elapsedRealtime();
		if (sLastWarned != 0 && now - sLastWarned < WARN_GAP_MS) {
			return;
		}
		try {
			AudioManager am = (AudioManager) context.getApplicationContext()
					.getSystemService(Context.AUDIO_SERVICE);
			if (am == null) {
				return;
			}
			boolean silent = am.getStreamVolume(androidStream) == 0;
			if (!silent && androidStream == AudioManager.STREAM_NOTIFICATION) {
				// This one is silenced by the ringer switch as well as by its own
				// slider, and the slider still reads non-zero when it is.
				silent = am.getRingerMode() != AudioManager.RINGER_MODE_NORMAL;
			}
			if (!silent) {
				return;
			}
			sLastWarned = now;
			android.widget.Toast.makeText(context.getApplicationContext(),
					what + ", but the " + name + " volume is off" + advice,
					android.widget.Toast.LENGTH_LONG).show();
		} catch (Exception e) {
			BlowTorchLogger.logMinor("TriggerSounds.warnIfStreamSilent", e);
		}
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
		warnIfInaudible(context);
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

	/**
	 * Drop a sample that turned out not to be playable.
	 *
	 * <p>Called from the load callback, which already holds the lock.
	 *
	 * @param sampleId the id SoundPool handed back and then failed to fill.
	 */
	private static void forgetSample(final int sampleId) {
		String path = null;
		for (java.util.Map.Entry<String, Integer> e : sLoaded.entrySet()) {
			if (e.getValue() != null && e.getValue().intValue() == sampleId) {
				path = e.getKey();
				break;
			}
		}
		if (path == null) {
			return;
		}
		sLoaded.remove(path);
		sFailed.put(path, Boolean.TRUE);
		BlowTorchLogger.logThrowable("TriggerSounds",
				new java.io.IOException(path
					+ " — a trigger's sound could not be decoded. If it is your own"
					+ " file, it may be damaged or in a format this phone does not"
					+ " read; pick another in the trigger's Sound action."));
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
			int usage;
			if (sStream == STREAM_NOTIFICATION) {
				usage = android.media.AudioAttributes.USAGE_NOTIFICATION;
			} else if (sStream == STREAM_ALARM) {
				usage = android.media.AudioAttributes.USAGE_ALARM;
			} else {
				usage = android.media.AudioAttributes.USAGE_MEDIA;
			}
			android.media.AudioAttributes attrs = new android.media.AudioAttributes.Builder()
					.setUsage(usage)
					.setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
					.build();
			sPool = new SoundPool.Builder()
					.setMaxStreams(MAX_STREAMS)
					.setAudioAttributes(attrs)
					.build();
		} else {
			sPool = new SoundPool(MAX_STREAMS, androidStream(), 0);
		}
		sPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
			@Override
			public void onLoadComplete(SoundPool pool, int sampleId, int status) {
				synchronized (TriggerSounds.class) {
					Long asked = sPending.remove(Integer.valueOf(sampleId));
					Float vol = sPendingVolume.remove(Integer.valueOf(sampleId));
					if (status != 0) {
						// Decoding failed — a file SoundPool took the name of and
						// then could not read. The id has to come back out of the
						// cache, or every later firing plays a sample that is not
						// there, silently, for the life of the process, with
						// nothing anywhere saying why.
						forgetSample(sampleId);
						return;
					}
					if (asked == null) {
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
