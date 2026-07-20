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
 */
public final class GmcpMediaPlayer {

	private static final String TAG = "GmcpMedia";
	private static final int CONNECT_TIMEOUT_MS = 15000;
	private static final int READ_TIMEOUT_MS = 30000;
	private static final int DEFAULT_STOP_FADE_MS = 5000;

	private final Context mAppContext;
	private final Handler mMain = new Handler(Looper.getMainLooper());
	private final ExecutorService mIo = Executors.newSingleThreadExecutor();
	private final ArrayList<Track> mTracks = new ArrayList<Track>();

	private String mDefaultUrl = "";

	public GmcpMediaPlayer(final Context context) {
		mAppContext = context.getApplicationContext();
	}

	public void handle(final String module, final JSONObject body) {
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
			mIo.execute(new Runnable() {
				@Override
				public void run() {
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

	public void release() {
		mMain.post(new Runnable() {
			@Override
			public void run() {
				stopAllImmediate();
			}
		});
		mIo.shutdownNow();
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
		final boolean continueMusic = data.optBoolean("continue", true);
		final String remoteUrl = resolveUrl(data.optString("url", ""), name);

		mIo.execute(new Runnable() {
			@Override
			public void run() {
				File local = cacheRemoteFile(remoteUrl, name);
				final Uri uri;
				if (local != null && local.isFile()) {
					uri = Uri.fromFile(local);
				} else if (remoteUrl.length() > 0) {
					uri = Uri.parse(remoteUrl);
				} else {
					Log.w(TAG, "Client.Media.Play no source for " + name);
					return;
				}
				mMain.post(new Runnable() {
					@Override
					public void run() {
						startTrack(name, type, tag, key, volume, loops, priority, continueMusic, uri);
					}
				});
			}
		});
	}

	private void startTrack(final String name, final String type, final String tag, final String key,
			final int volume, final int loops, final int priority, final boolean continueMusic, final Uri uri) {
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
		Track track = new Track(name, type, tag, key, priority, volume, player);
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
			player.prepare();
			player.start();
			mTracks.add(track);
		} catch (Exception e) {
			Log.w(TAG, "play failed for " + name, e);
			track.release();
		}
	}

	private void stop(final JSONObject data) {
		mMain.post(new Runnable() {
			@Override
			public void run() {
				boolean empty = data.length() == 0;
				String name = data.optString("name", "");
				String type = data.optString("type", "");
				String tag = data.optString("tag", "");
				String key = data.optString("key", "");
				int priority = data.has("priority") ? data.optInt("priority") : -1;
				boolean fadeaway = data.optBoolean("fadeaway", false);
				int fadeout = data.optInt("fadeout", DEFAULT_STOP_FADE_MS);

				Iterator<Track> it = mTracks.iterator();
				while (it.hasNext()) {
					Track t = it.next();
					boolean match = empty;
					if (!empty) {
						match = false;
						if (name.length() > 0 && name.equals(t.name)) {
							match = true;
						}
						if (type.length() > 0 && type.equalsIgnoreCase(t.type)) {
							match = true;
						}
						if (tag.length() > 0 && tag.equals(t.tag)) {
							match = true;
						}
						if (key.length() > 0 && key.equals(t.key)) {
							match = true;
						}
						if (priority >= 0 && t.priority <= priority) {
							match = true;
						}
					}
					if (!match) {
						continue;
					}
					it.remove();
					if (fadeaway || fadeout > 0) {
						fadeAndRelease(t, Math.max(200, fadeout));
					} else {
						t.release();
					}
				}
			}
		});
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
				step[0]++;
				float frac = 1f - (step[0] / (float) steps);
				if (frac <= 0f || step[0] >= steps) {
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
				mMain.postDelayed(tick[0], stepMs);
			}
		};
		mMain.post(tick[0]);
	}

	private void stopAllImmediate() {
		for (Track t : mTracks) {
			t.release();
		}
		mTracks.clear();
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

	private static final class Track {
		final String name;
		final String type;
		final String tag;
		final String key;
		final int priority;
		final int volume;
		MediaPlayer player;
		int remainingLoops;

		Track(final String name, final String type, final String tag, final String key,
				final int priority, final int volume, final MediaPlayer player) {
			this.name = name;
			this.type = type;
			this.tag = tag;
			this.key = key;
			this.priority = priority;
			this.volume = volume;
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
