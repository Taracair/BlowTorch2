package com.resurrection.blowtorch2.lib.button;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;

/**
 * Button-label face. A bad path returns the default face so the label still
 * draws. API 29+ chains the system sans-serif after a file, so a missing
 * glyph is not a blank. API 28 has no {@code CustomFallbackBuilder}.
 */
public final class ButtonTypefaces {

	private static final Map<String, Typeface> CACHE = new HashMap<String, Typeface>();
	/** True only after resolve actually opened that file. A miss means not tried yet. */
	private static final Map<String, Boolean> PAINT_ON_FILE = new HashMap<String, Boolean>();

	private static final class Loaded {
		final Typeface face;
		final boolean paintStyle;

		Loaded(final Typeface face, final boolean paintStyle) {
			this.face = face;
			this.paintStyle = paintStyle;
		}
	}

	private ButtonTypefaces() {
	}

	public static int styleBits(final String style) {
		if (style == null) {
			return Typeface.BOLD;
		}
		String s = style.trim().toLowerCase(Locale.US);
		if ("regular".equals(s) || "normal".equals(s)) {
			return Typeface.NORMAL;
		}
		if ("italic".equals(s)) {
			return Typeface.ITALIC;
		}
		if ("bolditalic".equals(s) || "bold_italic".equals(s)) {
			return Typeface.BOLD_ITALIC;
		}
		return Typeface.BOLD;
	}

	public static Typeface resolve(final Context context, final String path, final String style) {
		int bits = styleBits(style);
		String key = cacheKey(path, bits);
		synchronized (CACHE) {
			Typeface cached = CACHE.get(key);
			if (cached != null) {
				return cached;
			}
			Loaded loaded = load(context, path, bits);
			Typeface face = loaded.face != null ? loaded.face : fallback(bits);
			CACHE.put(key, face);
			if (fileFace(path)) {
				PAINT_ON_FILE.put(key, Boolean.valueOf(loaded.paintStyle && loaded.face != null));
			}
			return face;
		}
	}

	private static String cacheKey(final String path, final int bits) {
		return (path == null ? "" : path) + "\n" + bits;
	}

	static Typeface fallback(final int bits) {
		if (bits == Typeface.BOLD) {
			return Typeface.DEFAULT_BOLD;
		}
		return Typeface.create(Typeface.DEFAULT, bits);
	}

	private static Loaded load(final Context context, final String path, final int bits) {
		if (path == null || path.length() == 0 || "default".equals(path)) {
			return new Loaded(fallback(bits), false);
		}
		if ("serif".equals(path)) {
			return new Loaded(Typeface.create(Typeface.SERIF, bits), false);
		}
		if ("sans serif".equals(path) || "sans".equals(path) || "sans-serif".equals(path)) {
			return new Loaded(Typeface.create(Typeface.SANS_SERIF, bits), false);
		}
		if ("monospace".equals(path)) {
			return new Loaded(Typeface.create(Typeface.MONOSPACE, bits), false);
		}
		Typeface base = null;
		try {
			if (path.startsWith("fonts/") && context != null) {
				base = fromAsset(context, path);
			} else if (path.indexOf('/') >= 0) {
				File file = new File(path);
				if (file.isFile()) {
					base = fromFile(context, file);
				}
			}
		} catch (RuntimeException ignored) {
			base = null;
		}
		if (base == null) {
			return new Loaded(fallback(bits), false);
		}
		// A file face stays the loaded file. Bold and italic are paint flags.
		if (fileFace(path)) {
			return new Loaded(base, true);
		}
		try {
			return new Loaded(Typeface.create(base, bits), false);
		} catch (RuntimeException ignored) {
			return new Loaded(fallback(bits), false);
		}
	}

	/** Bundled or picked font file. System families are not file faces. */
	public static boolean fileFace(final String path) {
		if (path == null) {
			return false;
		}
		String p = path.trim();
		if (p.length() == 0 || "default".equals(p) || "serif".equals(p)
				|| "monospace".equals(p) || p.startsWith("sans")) {
			return false;
		}
		return p.startsWith("fonts/") || p.indexOf('/') >= 0;
	}

	/** Bold painted onto a file face. System families already carry the style. */
	public static boolean fakeBold(final String path, final String style) {
		if (!paintOnFile(path, style)) {
			return false;
		}
		int bits = styleBits(style);
		return bits == Typeface.BOLD || bits == Typeface.BOLD_ITALIC;
	}

	/** Italic skew for a file face. Zero for every other face. */
	public static float textSkew(final String path, final String style) {
		if (!paintOnFile(path, style)) {
			return 0f;
		}
		int bits = styleBits(style);
		if (bits == Typeface.ITALIC || bits == Typeface.BOLD_ITALIC) {
			return -0.25f;
		}
		return 0f;
	}

	/**
	 * A file that has not been resolved yet still wants paint flags.
	 * After resolve, only a face that actually opened does.
	 */
	private static boolean paintOnFile(final String path, final String style) {
		if (!fileFace(path)) {
			return false;
		}
		String key = cacheKey(path, styleBits(style));
		synchronized (CACHE) {
			Boolean on = PAINT_ON_FILE.get(key);
			if (on == null) {
				return true;
			}
			return on.booleanValue();
		}
	}

	private static Typeface fromAsset(final Context context, final String assetPath) {
		if (Build.VERSION.SDK_INT >= 29) {
			Typeface chained = fromAsset29(context, assetPath);
			if (chained != null) {
				return chained;
			}
		}
		try {
			return Typeface.createFromAsset(context.getAssets(), assetPath);
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	private static Typeface fromFile(final Context context, final File file) {
		if (Build.VERSION.SDK_INT >= 29) {
			Typeface chained = fromFile29(file);
			if (chained != null) {
				return chained;
			}
		}
		try {
			return Typeface.createFromFile(file);
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	@android.annotation.TargetApi(29)
	private static Typeface fromAsset29(final Context context, final String assetPath) {
		try {
			android.graphics.fonts.Font font = new android.graphics.fonts.Font.Builder(
					context.getAssets(), assetPath).build();
			return chain(font);
		} catch (java.io.IOException ignored) {
			return null;
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	@android.annotation.TargetApi(29)
	private static Typeface fromFile29(final File file) {
		try {
			android.graphics.fonts.Font font = new android.graphics.fonts.Font.Builder(file).build();
			return chain(font);
		} catch (java.io.IOException ignored) {
			return null;
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	@android.annotation.TargetApi(29)
	private static Typeface chain(final android.graphics.fonts.Font font) {
		android.graphics.fonts.FontFamily family =
				new android.graphics.fonts.FontFamily.Builder(font).build();
		Typeface.CustomFallbackBuilder builder = new Typeface.CustomFallbackBuilder(family);
		builder.setSystemFallback("sans-serif");
		return builder.build();
	}
}
