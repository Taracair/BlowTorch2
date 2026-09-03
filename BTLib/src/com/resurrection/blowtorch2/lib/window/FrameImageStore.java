package com.resurrection.blowtorch2.lib.window;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.LruCache;

/**
 * Decode URL / {@code base64:} pictures off the main thread. UI-process only —
 * the bitmap never crosses the binder. Frame id is the key so a replace updates
 * in place; inline text images get their own key so scrollback stays put.
 */
public final class FrameImageStore {

	/** Told on the main thread when a key's picture is ready, or failed. */
	public interface Listener {
		void onFrameImageChanged(String key);
	}

	/**
	 * How much decoded image we keep, in bytes.
	 *
	 * <p>Four megabytes is a handful of full-screen maps. Past that the least
	 * recently drawn one goes, and if it is wanted again its spec is still here
	 * to load it from.
	 */
	private static final int CACHE_BYTES = 4 * 1024 * 1024;

	/**
	 * Most bytes we will pull down for one image.
	 *
	 * <p>A server that answers a picture URL with something endless would
	 * otherwise fill the heap. This is a ceiling, not an expectation: the map
	 * eden sends is a few tens of kilobytes.
	 */
	private static final int MAX_DOWNLOAD_BYTES = 8 * 1024 * 1024;

	private static final int CONNECT_TIMEOUT_MS = 8000;
	private static final int READ_TIMEOUT_MS = 12000;

	/** Longest edge we decode to. Bigger than any phone screen, small enough to hold. */
	private static final int MAX_EDGE_PX = 2048;

	private static final String BASE64_PREFIX = "base64:";

	private static FrameImageStore sInstance;

	/** The one store for this process. Main thread only, so no locking here. */
	public static synchronized FrameImageStore get() {
		if (sInstance == null) {
			sInstance = new FrameImageStore();
		}
		return sInstance;
	}

	private final LruCache<String, Bitmap> bitmaps = new LruCache<String, Bitmap>(CACHE_BYTES) {
		@Override
		protected int sizeOf(String key, Bitmap value) {
			return value == null ? 0 : value.getByteCount();
		}
	};

	/**
	 * How many keys {@link #specs} and {@link #failures} remember.
	 *
	 * <p>Both were plain {@code HashMap}s while only the window path existed, and
	 * that path reuses one key per frame, so they stayed small. The in-text path
	 * mints a unique key per image ({@code Processor.placeImageInText}) and
	 * nothing calls {@link #forget} when the text scrolls that image out of the
	 * buffer, so a long session with in-text pictures grew both maps for as long
	 * as it ran. The bitmaps behind them were capped from the start; only these
	 * two were not.
	 *
	 * <p>A few hundred bytes an entry, so the cap is set where it will never be
	 * reached by a frame the player can still see, and evicting is only a lost
	 * chance to re-fetch, never a wrong picture.
	 */
	private static final int KEY_MEMORY_LIMIT = 512;

	/** The spec each key was last asked to show, so a dropped bitmap can come back. */
	private final Map<String, String> specs = boundedKeyMap();
	/** Keys with a load in flight; a second request for one of these is dropped. */
	private final Set<String> loading = new HashSet<String>();
	/**
	 * Keys asked to reload while a load was in flight — at most one more fetch.
	 *
	 * <p>This is a set, not a queue, and that is the point. eden sends eight
	 * {@code frame.image} for one step between two tiles; starting a download for
	 * each on the single worker would leave the picture further behind the player
	 * with every step. One in flight plus one waiting is enough to end up showing
	 * the newest picture, and the ones in between are of no interest by the time
	 * they would have been drawn.
	 */
	private final Set<String> pendingReload = new HashSet<String>();
	/** Why a key has no picture, in words a player can act on. Empty when fine. */
	private final Map<String, String> failures = boundedKeyMap();

	/**
	 * Newest {@link #KEY_MEMORY_LIMIT} keys, oldest touched first out.
	 *
	 * <p>Access-ordered, so a frame that is still being drawn keeps its place
	 * however old it is. Safe because every read and write of these maps is on
	 * the main thread — the worker only decodes, then posts {@code finish} back
	 * — and access ordering makes {@code get} a structural change.
	 */
	private static Map<String, String> boundedKeyMap() {
		return new LinkedHashMap<String, String>(16, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(final Map.Entry<String, String> eldest) {
				return size() > KEY_MEMORY_LIMIT;
			}
		};
	}

	private final List<Listener> listeners = new ArrayList<Listener>();
	private final Handler main = new Handler(Looper.getMainLooper());
	private final ExecutorService worker = Executors.newSingleThreadExecutor();

	private FrameImageStore() {
	}

	public void addListener(final Listener l) {
		if (l != null && !listeners.contains(l)) {
			listeners.add(l);
		}
	}

	public void removeListener(final Listener l) {
		listeners.remove(l);
	}

	/** The picture for this key, or null while it loads or if it never arrived. */
	public Bitmap getBitmap(final String key) {
		return key == null ? null : bitmaps.get(key);
	}

	/** Why {@link #getBitmap} is null, or null when there is nothing to explain. */
	public String getFailure(final String key) {
		return key == null ? null : failures.get(key);
	}

	/** True when this key has been asked for and has not answered yet. */
	public boolean isLoading(final String key) {
		return key != null && loading.contains(key);
	}

	/**
	 * Show {@code spec} under {@code key}, loading it if it is not already here.
	 *
	 * <p>Re-requesting the same spec for the same key does nothing, so this is
	 * safe to call from a layout pass or a sync: a frame whose picture has not
	 * changed does not fetch it again.
	 *
	 * @param spec A URL, or {@code base64:…}. Empty clears the key.
	 */
	public void request(final String key, final String spec) {
		requestInternal(key, spec, false);
	}

	/**
	 * Load {@code spec} under {@code key} again even if it is the spec already
	 * showing.
	 *
	 * <p>For when the server said the picture changed. A URL is not a picture:
	 * eden's map is one address, {@code …/Taracair/surrounding.png}, whose
	 * contents change as the character walks — so "same spec" says nothing about
	 * "same image", and {@link #request} skipping the fetch is what left the
	 * frame showing the room the player left. A {@code frame.image} packet is the
	 * server stating there is something new at that address; that is the signal
	 * to trust, not the string.
	 *
	 * <p>The picture already on screen stays there until the new one arrives.
	 * Blanking to "Loading…" eight times per step would be worse than the stale
	 * map it replaced.
	 */
	public void requestFresh(final String key, final String spec) {
		requestInternal(key, spec, true);
	}

	private void requestInternal(final String key, final String spec, final boolean force) {
		if (key == null || key.length() == 0) {
			return;
		}
		final String want = spec == null ? "" : spec.trim();
		if (want.length() == 0) {
			forget(key);
			return;
		}
		String have = specs.get(key);
		boolean sameSpec = want.equals(have);
		if (sameSpec && !force && (bitmaps.get(key) != null || loading.contains(key))) {
			return;
		}
		specs.put(key, want);
		failures.remove(key);
		if (!sameSpec) {
			// A different address means what is held is a picture of something
			// else. The same address re-requested keeps its bitmap: it is still
			// the best answer available until the new bytes land.
			bitmaps.remove(key);
		}
		if (loading.contains(key)) {
			// One in flight. Mark the key rather than start a second download —
			// finish() picks this up and does exactly one more.
			pendingReload.add(key);
			return;
		}
		loading.add(key);
		worker.execute(new Runnable() {
			@Override
			public void run() {
				Bitmap loaded = null;
				String failure = null;
				try {
					loaded = load(want);
					if (loaded == null) {
						failure = "the image could not be decoded";
					}
				} catch (IOException e) {
					failure = shortReason(e);
				} catch (OutOfMemoryError e) {
					// Deliberately caught: a server-chosen picture is the one
					// input here big enough to do this, and losing the picture
					// beats losing the app.
					failure = "the image was too big to hold in memory";
				} catch (RuntimeException e) {
					failure = shortReason(e);
				}
				final Bitmap result = loaded;
				final String why = failure;
				main.post(new Runnable() {
					@Override
					public void run() {
						finish(key, want, result, why);
					}
				});
			}
		});
	}

	/** Drop a key's picture and everything remembered about it. */
	public void forget(final String key) {
		if (key == null) {
			return;
		}
		specs.remove(key);
		failures.remove(key);
		bitmaps.remove(key);
		pendingReload.remove(key);
		// A load in flight is left alone; finish() drops what it produced
		// because the spec is gone.
		notifyChanged(key);
	}

	/** Drop everything. Used when a connection ends and its frames go with it. */
	public void clear() {
		specs.clear();
		failures.clear();
		pendingReload.clear();
		bitmaps.evictAll();
		notifyChanged(null);
	}

	private void finish(final String key, final String spec, final Bitmap bmp,
			final String failure) {
		loading.remove(key);
		String current = specs.get(key);
		boolean stale = current == null || !current.equals(spec);
		if (stale) {
			// The key was forgotten, or asked for something else, while this was
			// loading. Whatever came back is not what anyone is waiting for. It was
			// never handed to a view, so recycling it here is safe.
			if (bmp != null) {
				bmp.recycle();
			}
		} else if (bmp != null) {
			bitmaps.put(key, bmp);
			failures.remove(key);
			notifyChanged(key);
		} else {
			failures.put(key, failure == null ? "the image could not be loaded" : failure);
			notifyChanged(key);
		}
		boolean askedAgain = pendingReload.remove(key);
		if (current != null && (stale || askedAgain)) {
			// Either a newer spec arrived while this one held the slot, or the
			// server said the same address has something new. One more fetch,
			// however many requests were collapsed into that mark.
			requestInternal(key, current, true);
		}
	}

	private void notifyChanged(final String key) {
		// Over a copy. A listener that reacts by detaching a view — which
		// unregisters it — would otherwise be removing an entry from the list
		// this loop is walking.
		List<Listener> snapshot = new ArrayList<Listener>(listeners);
		for (int i = 0; i < snapshot.size(); i++) {
			try {
				snapshot.get(i).onFrameImageChanged(key);
			} catch (RuntimeException e) {
				com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logMinor(
						"FrameImageStore.notify", e);
			}
		}
	}

	private static String shortReason(final Throwable t) {
		String m = t.getMessage();
		if (m == null || m.length() == 0) {
			return t.getClass().getSimpleName();
		}
		return m;
	}

	/** Runs on the worker thread. Never on a main thread — see the class note. */
	private static Bitmap load(final String spec) throws IOException {
		byte[] raw;
		if (spec.toLowerCase(Locale.US).startsWith(BASE64_PREFIX)) {
			String payload = spec.substring(BASE64_PREFIX.length());
			raw = Base64.decode(payload, Base64.DEFAULT);
		} else {
			raw = download(spec);
		}
		if (raw == null || raw.length == 0) {
			return null;
		}
		return decodeScaled(raw);
	}

	private static byte[] download(final String spec) throws IOException {
		HttpURLConnection conn = null;
		InputStream in = null;
		try {
			URL url = new URL(spec);
			conn = (HttpURLConnection) url.openConnection();
			conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
			conn.setReadTimeout(READ_TIMEOUT_MS);
			conn.setInstanceFollowRedirects(true);
			conn.setRequestProperty("Accept", "image/*");
			// The picture behind a frame's URL changes while the URL does not, so
			// anything between us and the server answering from a cache would hand
			// back the map of a room the player has already left. Nothing installs
			// an HttpResponseCache in this app today — this is a fence, not a fix.
			conn.setUseCaches(false);
			conn.setRequestProperty("Cache-Control", "no-cache");
			int code = conn.getResponseCode();
			if (code < 200 || code > 299) {
				throw new IOException("server answered " + code);
			}
			in = conn.getInputStream();
			ByteArrayOutputStream out = new ByteArrayOutputStream(32 * 1024);
			byte[] buf = new byte[16 * 1024];
			int total = 0;
			int n;
			while ((n = in.read(buf)) > 0) {
				total += n;
				if (total > MAX_DOWNLOAD_BYTES) {
					throw new IOException("the image is over "
							+ (MAX_DOWNLOAD_BYTES / (1024 * 1024)) + " MB");
				}
				out.write(buf, 0, n);
			}
			return out.toByteArray();
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException ignored) {
					// Closing a stream we are done with; nothing to report.
				}
			}
			if (conn != null) {
				conn.disconnect();
			}
		}
	}

	/**
	 * Decode within {@link #MAX_EDGE_PX}, halving until it fits.
	 *
	 * <p>Bounds first, pixels second. A picture is whatever size the server felt
	 * like sending, and decoding one at full size to then draw it into a
	 * 300&nbsp;dp window is how a phone runs out of memory.
	 */
	private static Bitmap decodeScaled(final byte[] raw) {
		BitmapFactory.Options bounds = new BitmapFactory.Options();
		bounds.inJustDecodeBounds = true;
		BitmapFactory.decodeByteArray(raw, 0, raw.length, bounds);
		int sample = 1;
		int w = bounds.outWidth;
		int h = bounds.outHeight;
		if (w <= 0 || h <= 0) {
			return null;
		}
		while ((w / sample) > MAX_EDGE_PX || (h / sample) > MAX_EDGE_PX) {
			sample *= 2;
		}
		BitmapFactory.Options opts = new BitmapFactory.Options();
		opts.inSampleSize = sample;
		return BitmapFactory.decodeByteArray(raw, 0, raw.length, opts);
	}
}
