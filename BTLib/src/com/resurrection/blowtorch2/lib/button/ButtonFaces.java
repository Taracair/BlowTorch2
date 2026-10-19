package com.resurrection.blowtorch2.lib.button;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import com.caverock.androidsvg.SVG;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * Draws a picture in place of the colored pad. A missing file returns false
 * and the caller keeps the colored pad. Built-in ids are
 * {@code buttonfaces/<set>/<id>.png}. SVG is rasterized at the destination
 * size, then magenta-keyed like PNG and JPEG.
 */
public final class ButtonFaces {

	public static final int STATE_IDLE = 0;
	public static final int STATE_PRESSED = 1;
	public static final int STATE_FLIP = 2;

	/**
	 * Face while the finger is down. A swipe preview keeps the idle picture;
	 * the arrow is the swipe mark. Same rule as {@code buttonlook.pictureState}.
	 */
	public static int pictureState(final int touchState, final boolean swipePreview) {
		if (swipePreview) {
			return STATE_IDLE;
		}
		if (touchState == STATE_PRESSED || touchState == STATE_FLIP) {
			return touchState;
		}
		return STATE_IDLE;
	}

	private static final int MAX_EDGE = 1024;
	/** Refuse a file whose decoded edge is past this. 5000px is sampled, not refused. */
	static final int MAX_SOURCE_EDGE = 8192;
	static final int MAX_SOURCE_BYTES = 2 * 1024 * 1024;
	private static final int CACHE_MAX = 32;

	private static final java.util.HashSet<String> MISSES = new java.util.HashSet<String>();

	private static final Map<String, Bitmap> CACHE = new LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
		private static final long serialVersionUID = 1L;

		@Override
		protected boolean removeEldestEntry(final Map.Entry<String, Bitmap> eldest) {
			return size() > CACHE_MAX;
		}
	};

	private ButtonFaces() {
	}

	public static boolean draw(final Canvas canvas, final Context context, final RectF dst,
			final float radius, final boolean oval, final String face, final String facePressed,
			final String faceFlip, final int state, final int alpha) {
		if (canvas == null || dst == null) {
			return false;
		}
		int w = Math.max(1, Math.round(dst.width()));
		int h = Math.max(1, Math.round(dst.height()));
		String spec = face;
		boolean darken = false;
		if (state == STATE_PRESSED) {
			if (facePressed != null && facePressed.trim().length() > 0) {
				spec = facePressed;
			} else {
				darken = true;
			}
		} else if (state == STATE_FLIP) {
			if (faceFlip != null && faceFlip.trim().length() > 0) {
				spec = faceFlip;
			}
		}
		Bitmap bitmap = bitmapFor(context, spec, w, h);
		if (bitmap == null) {
			return false;
		}
		return paintBitmap(canvas, bitmap, dst, radius, oval, alpha, darken);
	}

	/** Draw {@code bitmap} into {@code dst} as a round rect, or an oval. */
	public static boolean paintBitmap(final Canvas canvas, final Bitmap bitmap, final RectF dst,
			final float radius, final boolean oval, final int alpha, final boolean darken) {
		if (canvas == null || bitmap == null || dst == null || bitmap.isRecycled()) {
			return false;
		}
		BitmapShader shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
		Matrix matrix = new Matrix();
		matrix.setRectToRect(new RectF(0, 0, bitmap.getWidth(), bitmap.getHeight()), dst,
				Matrix.ScaleToFit.FILL);
		shader.setLocalMatrix(matrix);
		Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
		paint.setShader(shader);
		paint.setAlpha(clampAlpha(alpha));
		if (darken) {
			paint.setColorFilter(darkenFilter());
		}
		if (oval) {
			canvas.drawOval(dst, paint);
		} else {
			float r = radius < 0f ? 0f : radius;
			canvas.drawRoundRect(dst, r, r, paint);
		}
		return true;
	}

	/**
	 * Power-of-two sample so the decoded edge is at most {@code maxEdge}.
	 * 5000 with a 1024 cap is 8, which decodes at 625.
	 */
	static int sampleSize(final int width, final int height, final int maxEdge) {
		if (width < 1 || height < 1 || maxEdge < 1) {
			return 1;
		}
		int sample = 1;
		int longest = Math.max(width, height);
		while (sample < 64 && longest / sample > maxEdge) {
			int next = sample * 2;
			if (longest / next < 1) {
				break;
			}
			sample = next;
		}
		return sample;
	}

	private static ColorMatrixColorFilter darkenFilter() {
		ColorMatrix scale = new ColorMatrix();
		scale.setScale(0.55f, 0.55f, 0.55f, 1f);
		return new ColorMatrixColorFilter(scale);
	}

	/** Square preview for the icon picker. Empty spec returns null. */
	public static Bitmap thumbnail(final Context context, final String spec, final int sizePx) {
		int size = sizePx < 1 ? 1 : sizePx;
		return bitmapFor(context, spec, size, size);
	}

	static Bitmap bitmapFor(final Context context, final String spec, final int width,
			final int height) {
		ButtonFaceRef.Spec parsed = ButtonFaceRef.parse(spec);
		if (parsed.isNone() || context == null) {
			return null;
		}
		int w = clampEdge(width);
		int h = clampEdge(height);
		String key = spec + "\n" + w + "x" + h;
		synchronized (CACHE) {
			Bitmap cached = CACHE.get(key);
			if (cached != null && !cached.isRecycled()) {
				return cached;
			}
			if (MISSES.contains(key)) {
				return null;
			}
			Bitmap loaded = load(context, parsed, w, h);
			if (loaded == null) {
				if (parsed.named != null) {
					MISSES.add(key);
				}
				return null;
			}
			CACHE.put(key, loaded);
			return loaded;
		}
	}

	/** Drop decoded {@code named:} pictures so a replaced file is read again. */
	public static void forgetNamed() {
		synchronized (CACHE) {
			java.util.Iterator<String> keys = CACHE.keySet().iterator();
			while (keys.hasNext()) {
				if (keys.next().startsWith("named:")) {
					keys.remove();
				}
			}
			java.util.Iterator<String> misses = MISSES.iterator();
			while (misses.hasNext()) {
				if (misses.next().startsWith("named:")) {
					misses.remove();
				}
			}
		}
	}

	private static int clampEdge(final int edge) {
		if (edge < 1) {
			return 1;
		}
		if (edge > MAX_EDGE) {
			return MAX_EDGE;
		}
		return edge;
	}

	private static int clampAlpha(final int alpha) {
		if (alpha < 0) {
			return 0;
		}
		if (alpha > 255) {
			return 255;
		}
		return alpha;
	}

	private static Bitmap load(final Context context, final ButtonFaceRef.Spec spec,
			final int width, final int height) {
		Bitmap raw = null;
		if (spec.assetPng != null) {
			raw = decodeAsset(context, spec.assetPng);
			if (raw == null) {
				String svg = spec.assetPng.substring(0, spec.assetPng.length() - 4) + ".svg";
				raw = decodeAssetSvg(context, svg, width, height);
			}
		} else if (spec.named != null) {
			InputStream in = IconFolder.open(context, spec.named);
			try {
				if (in != null && ButtonFaceRef.isSvg(spec.named)) {
					raw = renderSvg(in, width, height);
				} else if (in != null && ButtonFaceRef.isRaster(spec.named)) {
					raw = decodeStream(in);
				}
			} finally {
				close(in);
			}
		} else if (spec.file != null) {
			if (ButtonFaceRef.isSvg(spec.file)) {
				raw = decodeFileSvg(spec.file, width, height);
			} else if (ButtonFaceRef.isRaster(spec.file)) {
				raw = decodeFile(spec.file);
			}
		}
		if (raw == null) {
			return null;
		}
		Bitmap keyed = key(raw);
		if (keyed != raw && raw != keyed) {
			raw.recycle();
		}
		if (keyed.getWidth() == width && keyed.getHeight() == height) {
			return keyed;
		}
		Bitmap scaled = Bitmap.createScaledBitmap(keyed, width, height, true);
		if (scaled != keyed) {
			keyed.recycle();
		}
		return scaled;
	}

	private static Bitmap key(final Bitmap src) {
		Bitmap mutable = src;
		if (!src.isMutable() || src.getConfig() != Bitmap.Config.ARGB_8888) {
			mutable = src.copy(Bitmap.Config.ARGB_8888, true);
			if (mutable == null) {
				return src;
			}
		}
		int w = mutable.getWidth();
		int h = mutable.getHeight();
		int[] pixels = new int[w * h];
		mutable.getPixels(pixels, 0, w, 0, 0, w, h);
		ButtonChromaKey.keyInto(pixels);
		mutable.setPixels(pixels, 0, w, 0, 0, w, h);
		return mutable;
	}

	private static Bitmap decodeAsset(final Context context, final String asset) {
		InputStream in = null;
		try {
			in = context.getAssets().open(asset);
			return decodeStream(in);
		} catch (java.io.IOException ignored) {
			return null;
		} catch (RuntimeException ignored) {
			return null;
		} finally {
			close(in);
		}
	}

	private static Bitmap decodeFile(final String path) {
		InputStream in = null;
		try {
			File file = new File(path);
			if (!file.isFile()) {
				return null;
			}
			in = new FileInputStream(file);
			return decodeStream(in);
		} catch (java.io.IOException ignored) {
			return null;
		} catch (RuntimeException ignored) {
			return null;
		} finally {
			close(in);
		}
	}

	private static Bitmap decodeStream(final InputStream in) {
		// decodeStream with Options rewinds the stream. Asset and file streams
		// cannot, so the bytes are buffered and decoded twice: bounds, then sample.
		try {
			ByteArrayOutputStream buf = new ByteArrayOutputStream();
			byte[] tmp = new byte[8192];
			int n;
			int total = 0;
			while ((n = in.read(tmp)) >= 0) {
				if (n == 0) {
					continue;
				}
				total += n;
				if (total > MAX_SOURCE_BYTES) {
					return null;
				}
				buf.write(tmp, 0, n);
			}
			byte[] bytes = buf.toByteArray();
			if (bytes.length == 0) {
				return null;
			}
			BitmapFactory.Options bounds = new BitmapFactory.Options();
			bounds.inJustDecodeBounds = true;
			BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
			if (bounds.outWidth < 1 || bounds.outHeight < 1) {
				return null;
			}
			if (bounds.outWidth > MAX_SOURCE_EDGE || bounds.outHeight > MAX_SOURCE_EDGE) {
				return null;
			}
			BitmapFactory.Options opts = new BitmapFactory.Options();
			opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
			opts.inScaled = false;
			opts.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, MAX_EDGE);
			return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
		} catch (java.io.IOException ignored) {
			return null;
		} catch (OutOfMemoryError ignored) {
			return null;
		}
	}

	private static Bitmap decodeAssetSvg(final Context context, final String asset,
			final int width, final int height) {
		InputStream in = null;
		try {
			in = context.getAssets().open(asset);
			return renderSvg(in, width, height);
		} catch (java.io.IOException ignored) {
			return null;
		} catch (RuntimeException ignored) {
			return null;
		} finally {
			close(in);
		}
	}

	private static Bitmap decodeFileSvg(final String path, final int width, final int height) {
		InputStream in = null;
		try {
			File file = new File(path);
			if (!file.isFile()) {
				return null;
			}
			in = new FileInputStream(file);
			return renderSvg(in, width, height);
		} catch (java.io.IOException ignored) {
			return null;
		} catch (RuntimeException ignored) {
			return null;
		} finally {
			close(in);
		}
	}

	private static Bitmap renderSvg(final InputStream in, final int width, final int height) {
		try {
			SVG svg = SVG.getFromInputStream(in);
			svg.setDocumentWidth(width);
			svg.setDocumentHeight(height);
			Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
			svg.renderToCanvas(new Canvas(bitmap));
			return bitmap;
		} catch (com.caverock.androidsvg.SVGParseException ignored) {
			return null;
		} catch (RuntimeException ignored) {
			return null;
		} catch (OutOfMemoryError ignored) {
			return null;
		}
	}

	private static void close(final InputStream in) {
		if (in == null) {
			return;
		}
		try {
			in.close();
		} catch (java.io.IOException ignored) {
		}
	}
}
