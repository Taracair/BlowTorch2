package com.resurrection.blowtorch2.lib.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.json.JSONObject;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * Native handler for GMCP Client.Media (Default / Load / Play / Stop).
 * Sound and music only — video is ignored in this pass.
 * <p>
 * Per <a href="https://wiki.mudlet.org/w/Standards:MUD_Client_Media_Protocol">MCMP</a>,
 * {@code Client.Media.Stop} with no name/type/tag/key/priority filters stops all media
 * (including {@code {"fadeaway": true}} alone). Filters combine with AND.
 * Fade-out applies only when {@code fadeaway} is true or {@code fadeout} is present.
 */
public final class GmcpMediaPlayer {

	private static final String TAG = "GmcpMedia";
	private static final int CONNECT_TIMEOUT_MS = 15000;
	private static final int READ_TIMEOUT_MS = 30000;
	private static final int DEFAULT_STOP_FADE_MS = 5000;

	private final Context mAppContext;
	private final Handler mMain = new Handler(Looper.getMainLooper());
	private ExecutorService mIo = Executors.newSingleThreadExecutor();
	private final ArrayList<Track> mTracks = new ArrayList<Track>();
	private final ArrayList<Runnable> mFadeTicks = new ArrayList<Runnable>();
	/** Bumped on release / hard stop-all so in-flight downloads cannot restart audio. */
	private final AtomicInteger mEpoch = new AtomicInteger(0);
	private volatile boolean mReleased = false;

	private String mDefaultUrl = "";

	public GmcpMediaPlayer(final Context context) {
		mAppContext = context.getApplicationContext();
	}

	public void handle(final String module, final JSONObject body) {
		if (mReleased) {
			return;
		}
		String action = lastSegment(module).toLowerCase(Locale.US);
		JSONObject data = body != null ? body : new JSONObject();
		if ("default".equals(action)) {
			mDefaultUrl = normalizeBaseUrl(data.optString("url", mDefaultUrl));
			return;
		}
		if ("load".equals(action)) {
			String name = data.optString("name", "");
			String url = resolveUrl(data.optString("url", ""), name);
			if (name.length() == 0 || url.length() == 0) {
				Log.w(TAG, "Client.Media.Load missing name/url");
				return;
			}
			final int epoch = mEpoch.get();
			mIo.execute(new Runnable() {
				@Override
				public void run() {
					if (mReleased || epoch != mEpoch.get()) {
						return;
					}
					cacheRemoteFile(url, name);
				}
			});
			return;
		}
		if ("play".equals(action)) {
			play(data);
			return;
		}
		if ("stop".equals(action)) {
			stop(data);
		}
	}

	/**
	 * Stop every playing track immediately (disconnect, task removed, app teardown).
	 * Safe to call repeatedly; does not shut down the player for future Play packets.
	 */
	public void stopAllImmediatePublic() {
		if (mReleased) {
			return;
		}
		mEpoch.incrementAndGet();
		runOnMain(new Runnable() {
			@Override
			public void run() {
				cancelFades();
				stopAllImmediate();
			}
		});
	}

	public void release() {
		mReleased = true;
		mEpoch.incrementAndGet();
		runOnMain(new Runnable() {
			@Override
			public void run() {
				cancelFades();
				stopAllImmediate();
			}
		});
		try {
			mIo.shutdownNow();
		} catch (Exception ignored) {
		}
	}

	private void play(final JSONObject data) {
		final String name = data.optString("name", "");
		if (name.length() == 0) {
			Log.w(TAG, "Client.Media.Play missing name");
			return;
		}
		final String type = data.optString("type", "sound").toLowerCase(Locale.US);
		if ("video".equals(type)) {
			Log.i(TAG, "Client.Media.Play video ignored");
			return;
		}
		final String tag = data.optString("tag", "");
		final String key = data.optString("key", "");
		final int volume = clamp(data.optInt("volume", 50), 1, 100);
		final int loops = data.optInt("loops", 1);
		final int priority = data.optInt("priority", 50);
		final boolean continueMusic = optTruthy(data, "continue", true);
		final int playFadeout = data.has("fadeout") ? Math.max(0, data.optInt("fadeout", 0)) : 0;
		final String remoteUrl = resolveUrl(data.optString("url", ""), name);
		final int epoch = mEpoch.get();

		mIo.execute(new Runnable() {
			@Override
			public void run() {
				if (mReleased || epoch != mEpoch.get()) {
					return;
				}
				File local = cacheRemoteFile(remoteUrl, name);
				if (local == null || !local.isFile() || local.length() <= 0) {
					Log.w(TAG, "Client.Media.Play no cached file for " + name
							+ " (download failed or blocked); skipping play");
					return;
				}
				final Uri uri = Uri.fromFile(local);
				mMain.post(new Runnable() {
					@Override
					public void run() {
						if (mReleased || epoch != mEpoch.get()) {
							return;
						}
						startTrack(name, type, tag, key, volume, loops, priority, continueMusic,
								playFadeout, uri);
					}
				});
			}
		});
	}

	private void startTrack(final String name, final String type, final String tag, final String key,
			final int volume, final int loops, final int priority, final boolean continueMusic,
			final int playFadeout, final Uri uri) {
		if (key.length() > 0) {
			Iterator<Track> it = mTracks.iterator();
			while (it.hasNext()) {
				Track t = it.next();
				if (key.equals(t.key) && (!name.equals(t.name))) {
					t.release();
					it.remove();
				}
			}
		}
		if ("music".equals(type) && !continueMusic && key.length() > 0) {
			Iterator<Track> it = mTracks.iterator();
			while (it.hasNext()) {
				Track t = it.next();
				if (key.equals(t.key) && "music".equals(t.type)) {
					t.release();
					it.remove();
				}
			}
		}
		Iterator<Track> low = mTracks.iterator();
		while (low.hasNext()) {
			Track t = low.next();
			if (t.priority < priority) {
				t.release();
				low.remove();
			}
		}

		MediaPlayer player = new MediaPlayer();
		Track track = new Track(name, type, tag, key, priority, volume, playFadeout, player);
		try {
			player.setAudioStreamType(AudioManager.STREAM_MUSIC);
			player.setDataSource(mAppContext, uri);
			float vol = volume / 100f;
			player.setVolume(vol, vol);
			if (loops == -1) {
				player.setLooping(true);
			} else if (loops > 1) {
				track.remainingLoops = loops - 1;
				player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
					@Override
					public void onCompletion(MediaPlayer mp) {
						if (track.remainingLoops > 0) {
							track.remainingLoops--;
							try {
								mp.seekTo(0);
								mp.start();
								return;
							} catch (Exception e) {
								Log.w(TAG, "loop restart failed", e);
							}
						}
						mTracks.remove(track);
						track.release();
					}
				});
			} else {
				player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
					@Override
					public void onCompletion(MediaPlayer mp) {
						mTracks.remove(track);
						track.release();
					}
				});
			}
			player.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
				@Override
				public void onPrepared(MediaPlayer mp) {
					if (mReleased) {
						mTracks.remove(track);
						track.release();
						return;
					}
					try {
						mp.start();
						mTracks.add(track);
					} catch (Exception e) {
						Log.w(TAG, "start failed for " + name, e);
						mTracks.remove(track);
						track.release();
					}
				}
			});
			player.setOnErrorListener(new MediaPlayer.OnErrorListener() {
				@Override
				public boolean onError(MediaPlayer mp, int what, int extra) {
					Log.w(TAG, "MediaPlayer error for " + name + " what=" + what + " extra=" + extra);
					mTracks.remove(track);
					track.release();
					return true;
				}
			});
			player.prepareAsync();
		} catch (Exception e) {
			Log.w(TAG, "play failed for " + name, e);
			track.release();
		}
	}

	private void stop(final JSONObject data) {
		final JSONObject body = data != null ? data : new JSONObject();
		runOnMain(new Runnable() {
			@Override
			public void run() {
				if (mReleased) {
					return;
				}
				final String name = body.optString("name", "");
				final String type = body.optString("type", "");
				final String tag = body.optString("tag", "");
				final String key = body.optString("key", "");
				final boolean hasPriority = body.has("priority");
				final int priority = hasPriority ? body.optInt("priority") : -1;
				final boolean fadeaway = optTruthy(body, "fadeaway", false);
				final boolean hasFadeout = body.has("fadeout");
				final int stopFadeout = hasFadeout
						? Math.max(200, body.optInt("fadeout", DEFAULT_STOP_FADE_MS))
						: DEFAULT_STOP_FADE_MS;
				final boolean doFade = shouldFade(fadeaway, hasFadeout);

				int stopped = 0;
				Iterator<Track> it = mTracks.iterator();
				while (it.hasNext()) {
					Track t = it.next();
					if (!matchesStop(t.name, t.type, t.tag, t.key, t.priority,
							name, type, tag, key, hasPriority, priority)) {
						continue;
					}
					it.remove();
					stopped++;
					if (doFade) {
						int fadeMs = t.playFadeout > 0 ? t.playFadeout : stopFadeout;
						fadeAndRelease(t, Math.max(200, fadeMs));
					} else {
						t.release();
					}
				}
				Log.i(TAG, "Client.Media.Stop matched=" + stopped
						+ " filters={name=" + name + ",type=" + type + ",tag=" + tag
						+ ",key=" + key + ",priority=" + (hasPriority ? String.valueOf(priority) : "-")
						+ "} fade=" + doFade);
			}
		});
	}

	/**
	 * MCMP Stop matching: unspecified filters are ignored; specified filters AND together.
	 * No filters at all (including fadeaway-only) → match every track.
	 */
	static boolean matchesStop(final String trackName, final String trackType, final String trackTag,
			final String trackKey, final int trackPriority,
			final String name, final String type, final String tag, final String key,
			final boolean hasPriority, final int priority) {
		if (name != null && name.length() > 0 && !name.equals(trackName)) {
			return false;
		}
		if (type != null && type.length() > 0
				&& !type.equalsIgnoreCase(trackType != null ? trackType : "")) {
			return false;
		}
		if (tag != null && tag.length() > 0 && !tag.equals(trackTag)) {
			return false;
		}
		if (key != null && key.length() > 0 && !key.equals(trackKey)) {
			return false;
		}
		if (hasPriority && trackPriority > priority) {
			return false;
		}
		return true;
	}

	/** Fade only when fadeaway is true or an explicit fadeout was sent on Stop. */
	static boolean shouldFade(final boolean fadeaway, final boolean hasFadeoutKey) {
		return fadeaway || hasFadeoutKey;
	}

	private void fadeAndRelease(final Track track, final int durationMs) {
		final MediaPlayer p = track.player;
		if (p == null) {
			track.release();
			return;
		}
		final float startVol = track.volume / 100f;
		final int steps = Math.max(5, durationMs / 50);
		final long stepMs = Math.max(20L, durationMs / (long) steps);
		final Runnable[] tick = new Runnable[1];
		final int[] step = new int[] { 0 };
		tick[0] = new Runnable() {
			@Override
			public void run() {
				mFadeTicks.remove(tick[0]);
				step[0]++;
				float frac = 1f - (step[0] / (float) steps);
				if (frac <= 0f || step[0] >= steps || mReleased) {
					track.release();
					return;
				}
				try {
					float v = startVol * frac;
					p.setVolume(v, v);
				} catch (Exception ignored) {
					track.release();
					return;
				}
				mFadeTicks.add(tick[0]);
				mMain.postDelayed(tick[0], stepMs);
			}
		};
		mFadeTicks.add(tick[0]);
		mMain.post(tick[0]);
	}

	private void cancelFades() {
		for (Runnable r : mFadeTicks) {
			mMain.removeCallbacks(r);
		}
		mFadeTicks.clear();
	}

	private void stopAllImmediate() {
		for (Track t : mTracks) {
			t.release();
		}
		mTracks.clear();
	}

	private void runOnMain(final Runnable r) {
		if (Looper.myLooper() == Looper.getMainLooper()) {
			r.run();
		} else {
			mMain.post(r);
		}
	}

	private File cacheRemoteFile(final String url, final String name) {
		if (url == null || url.length() == 0) {
			return localCacheFile(name);
		}
		File dest = localCacheFile(name);
		if (dest != null && dest.isFile() && dest.length() > 0) {
			return dest;
		}
		HttpURLConnection conn = null;
		InputStream in = null;
		FileOutputStream out = null;
		try {
			conn = (HttpURLConnection) new URL(url).openConnection();
			conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
			conn.setReadTimeout(READ_TIMEOUT_MS);
			conn.setInstanceFollowRedirects(true);
			int code = conn.getResponseCode();
			if (code < 200 || code >= 300) {
				Log.w(TAG, "download HTTP " + code + " for " + url);
				return null;
			}
			in = conn.getInputStream();
			File parent = dest.getParentFile();
			if (parent != null && !parent.exists()) {
				parent.mkdirs();
			}
			out = new FileOutputStream(dest);
			byte[] buf = new byte[8192];
			int n;
			while ((n = in.read(buf)) >= 0) {
				out.write(buf, 0, n);
			}
			out.flush();
			return dest;
		} catch (Exception e) {
			Log.w(TAG, "download failed: " + url, e);
			if (dest != null && dest.exists()) {
				dest.delete();
			}
			return null;
		} finally {
			try {
				if (out != null) {
					out.close();
				}
			} catch (Exception ignored) {
			}
			try {
				if (in != null) {
					in.close();
				}
			} catch (Exception ignored) {
			}
			if (conn != null) {
				conn.disconnect();
			}
		}
	}

	private File localCacheFile(final String name) {
		if (name == null || name.length() == 0) {
			return null;
		}
		String safe = name.replace("..", "_").replace('\\', '/');
		while (safe.startsWith("/")) {
			safe = safe.substring(1);
		}
		File root = new File(mAppContext.getFilesDir(), "gmcp-media");
		return new File(root, safe);
	}

	private String resolveUrl(final String packetUrl, final String name) {
		String base = normalizeBaseUrl(packetUrl.length() > 0 ? packetUrl : mDefaultUrl);
		if (base.length() == 0) {
			return "";
		}
		if (name.length() == 0) {
			return base;
		}
		if (base.endsWith("/")) {
			return base + name;
		}
		return base + "/" + name;
	}

	private static String normalizeBaseUrl(final String url) {
		if (url == null) {
			return "";
		}
		String u = url.trim();
		if (u.length() == 0) {
			return "";
		}
		if (!u.endsWith("/")) {
			u = u + "/";
		}
		return u;
	}

	private static String lastSegment(final String module) {
		if (module == null) {
			return "";
		}
		int dot = module.lastIndexOf('.');
		if (dot < 0 || dot + 1 >= module.length()) {
			return module;
		}
		return module.substring(dot + 1);
	}

	private static int clamp(final int v, final int min, final int max) {
		if (v < min) {
			return min;
		}
		if (v > max) {
			return max;
		}
		return v;
	}

	/** Spec: boolean values may also arrive as strings. */
	static boolean optTruthy(final JSONObject data, final String key, final boolean defaultValue) {
		if (data == null || !data.has(key)) {
			return defaultValue;
		}
		Object raw = data.opt(key);
		if (raw instanceof Boolean) {
			return ((Boolean) raw).booleanValue();
		}
		if (raw instanceof Number) {
			return ((Number) raw).intValue() != 0;
		}
		String s = String.valueOf(raw).toLowerCase(Locale.US).trim();
		if ("true".equals(s) || "1".equals(s) || "yes".equals(s)) {
			return true;
		}
		if ("false".equals(s) || "0".equals(s) || "no".equals(s)) {
			return false;
		}
		return defaultValue;
	}

	private static final class Track {
		final String name;
		final String type;
		final String tag;
		final String key;
		final int priority;
		final int volume;
		final int playFadeout;
		MediaPlayer player;
		int remainingLoops;

		Track(final String name, final String type, final String tag, final String key,
				final int priority, final int volume, final int playFadeout, final MediaPlayer player) {
			this.name = name;
			this.type = type;
			this.tag = tag;
			this.key = key;
			this.priority = priority;
			this.volume = volume;
			this.playFadeout = playFadeout;
			this.player = player;
		}

		void release() {
			if (player != null) {
				try {
					if (player.isPlaying()) {
						player.stop();
					}
				} catch (Exception ignored) {
				}
				try {
					player.release();
				} catch (Exception ignored) {
				}
				player = null;
			}
		}
	}
}
