package com.resurrection.blowtorch2.lib.window;

/**
 * Light-paper remap at paint time. The buffer still holds the SGR the MUD and
 * colour triggers injected; only the ARGB handed to Canvas changes.
 *
 * <p>Dark mode is identity: the same RGB Colorizer already produced.
 * Light mode puts a warm grey paper under the glyphs and darkens ink that would
 * vanish on it (default ANSI 37, whites, light greys, pale yellow). Saturated
 * reds and cyans stay themselves unless contrast against the paper is poor.
 */
public final class LightPaper {

	/** Canvas fill used when the light theme is off. */
	public static final int DARK_PAPER = 0xFF0A0A0A;
	/** Shade 2 — warm grey paper. Not {@code #FFFFFF}, which makes yellow vanish. */
	public static final int LIGHT_PAPER = 0xFFECE8E0;
	/** Default ANSI 37 / 39 on shade-2 paper. Other shades compute via {@link #inkFor}. */
	public static final int LIGHT_INK = 0xFF2C2C2C;
	/** ANSI 37 as {@code Colorizer} resolves it on a dark window. */
	public static final int DARK_INK = 0xFFBBBBBB;

	public static final int SHADE_MIN = 1;
	public static final int SHADE_MAX = 5;
	/** Same paper as {@link #LIGHT_PAPER} so old profiles match. */
	public static final int SHADE_DEFAULT = 2;

	/** 1 grey → 5 near-white. Index {@code shade - 1}. */
	private static final int[] SHADE_PAPERS = new int[] {
			0xFFD4D0C8,
			LIGHT_PAPER,
			0xFFF4F0E6,
			0xFFFAF7F2,
			0xFFFEFCF8
	};

	static final double MIN_GREY_CONTRAST = 4.0;
	static final double MIN_CHROMA_CONTRAST = 3.0;
	static final double GREY_SATURATION = 0.14;

	private LightPaper() {
	}

	public static int clampShade(final int shade) {
		if (shade < SHADE_MIN) {
			return SHADE_MIN;
		}
		if (shade > SHADE_MAX) {
			return SHADE_MAX;
		}
		return shade;
	}

	public static int paper(final boolean light) {
		return paper(light, SHADE_DEFAULT);
	}

	public static int paper(final boolean light, final int shade) {
		if (!light) {
			return DARK_PAPER;
		}
		return SHADE_PAPERS[clampShade(shade) - 1];
	}

	/**
	 * Body ink for this paper. Shade 2 is {@link #LIGHT_INK}; lighter paper
	 * darkens the glyphs, greyer paper lifts them a little, then contrast is
	 * checked. Not a {@code static} of the current shade — two processes.
	 */
	public static int inkFor(final int paper) {
		if ((paper | 0xFF000000) == LIGHT_PAPER) {
			return LIGHT_INK;
		}
		final double paperL = luminance(paper);
		final double refL = luminance(LIGHT_PAPER);
		int ink = LIGHT_INK;
		if (paperL > refL) {
			final float t = (float) Math.min(0.40, (paperL - refL) * 3.0);
			ink = mix(ink, 0xFF000000, t);
		} else if (paperL < refL) {
			final float t = (float) Math.min(0.28, (refL - paperL) * 3.0);
			ink = mix(ink, 0xFF4A4A4A, t);
		}
		int cur = ink;
		for (int i = 0; i < 12; i++) {
			if (contrastRatio(cur, paper) >= MIN_GREY_CONTRAST) {
				return cur;
			}
			cur = mix(cur, 0xFF000000, 0.18f);
		}
		return cur;
	}

	/** Nested chrome (edit strip, extra-text title) a step darker than paper. */
	public static int nest(final int paper) {
		return mix(paper, 0xFF000000, 0.07f);
	}

	/**
	 * Whether a resolved cell background should be skipped so the canvas paper
	 * shows through.
	 *
	 * <p>Dark: skip pure black and the dark paper (today's {@code useBackground}
	 * test). Light: skip the light paper itself, so an xterm-0 / truecolor-black
	 * cell can actually paint. ANSI 40 and 49 are remapped to paper before this
	 * test, so they still skip — they cannot be told apart after the registers
	 * collapse, and painting every {@code 40} as a black stripe would wreck
	 * worlds that send 40 as "normal".
	 */
	public static boolean skipCellBackground(final int bgArgb, final boolean light) {
		return skipCellBackground(bgArgb, light, SHADE_DEFAULT);
	}

	public static boolean skipCellBackground(final int bgArgb, final boolean light,
			final int shade) {
		final int rgb = bgArgb | 0xFF000000;
		if (!light) {
			return rgb == 0xFF000000 || rgb == DARK_PAPER;
		}
		return rgb == paper(true, shade);
	}

	/**
	 * ANSI default foreground: register 37 after SGR 0 / 39 / explicit 37, and
	 * not an xterm-256 or truecolor fg.
	 */
	public static boolean isDefaultAnsiForeground(final Integer selectedColor,
			final boolean xterm256Fg, final boolean trueColorFg) {
		if (xterm256Fg || trueColorFg || selectedColor == null) {
			return false;
		}
		return selectedColor.intValue() == 37 || selectedColor.intValue() == 39;
	}

	/**
	 * ANSI default background: register 40 after SGR 0 / 49 / explicit 40, the
	 * unset constructor value 60, and not an xterm-256 or truecolor bg.
	 */
	public static boolean isDefaultAnsiBackground(final Integer selectedBackground,
			final boolean xterm256Bg, final boolean trueColorBg) {
		if (xterm256Bg || trueColorBg) {
			return false;
		}
		if (selectedBackground == null) {
			return true;
		}
		final int v = selectedBackground.intValue();
		return v == 40 || v == 49 || v == 60;
	}

	public static int remapForeground(final int argb, final boolean light,
			final boolean defaultAnsiFg) {
		return remapForeground(argb, light, defaultAnsiFg, SHADE_DEFAULT);
	}

	public static int remapForeground(final int argb, final boolean light,
			final boolean defaultAnsiFg, final int shade) {
		final int rgb = argb | 0xFF000000;
		if (!light) {
			return rgb;
		}
		final int sheet = paper(true, shade);
		if (defaultAnsiFg) {
			return inkFor(sheet);
		}
		return contrastInk(rgb, sheet);
	}

	public static int remapBackground(final int argb, final boolean light,
			final boolean defaultAnsiBg) {
		return remapBackground(argb, light, defaultAnsiBg, SHADE_DEFAULT);
	}

	public static int remapBackground(final int argb, final boolean light,
			final boolean defaultAnsiBg, final int shade) {
		if (!light) {
			return argb | 0xFF000000;
		}
		final int sheet = paper(true, shade);
		if (defaultAnsiBg) {
			return sheet;
		}
		return contrastWash(argb | 0xFF000000, sheet);
	}

	/**
	 * Dim a repeated line. Dark theme scales toward black (existing
	 * {@link RepeatedLineDimmer#dimForeground}). Light theme mixes toward paper
	 * by the same keep-factor so "dimmer" still means closer to the paper.
	 */
	public static int dimTowardPaper(final int color, final int strengthPercent,
			final boolean light) {
		return dimTowardPaper(color, strengthPercent, light, SHADE_DEFAULT);
	}

	public static int dimTowardPaper(final int color, final int strengthPercent,
			final boolean light, final int shade) {
		if (!light) {
			return RepeatedLineDimmer.dimForeground(color, strengthPercent);
		}
		final float keep = RepeatedLineDimmer.keepFactor(strengthPercent);
		return mix(paper(true, shade), color | 0xFF000000, keep);
	}

	/**
	 * Unicode shade blocks ({@code ░▒▓}) currently multiply RGB toward black.
	 * That is "mix toward paper" on a dark window. On light paper, mix toward
	 * the light paper with the same 64/128/192 weights.
	 */
	public static int shadeTowardPaper(final int fgArgb, final int amount255,
			final boolean light) {
		return shadeTowardPaper(fgArgb, amount255, light, SHADE_DEFAULT);
	}

	public static int shadeTowardPaper(final int fgArgb, final int amount255,
			final boolean light, final int shade) {
		final int fg = fgArgb | 0xFF000000;
		final int amount = amount255 < 0 ? 0 : (amount255 > 255 ? 255 : amount255);
		if (!light) {
			final int r = (((fg >> 16) & 0xFF) * amount) / 255;
			final int g = (((fg >> 8) & 0xFF) * amount) / 255;
			final int b = ((fg & 0xFF) * amount) / 255;
			return 0xFF000000 | (r << 16) | (g << 8) | b;
		}
		return mix(paper(true, shade), fg, amount / 255f);
	}

	static int contrastInk(final int rgb, final int paper) {
		if (contrastRatio(rgb, paper) >= MIN_GREY_CONTRAST) {
			return rgb;
		}
		if (saturation(rgb) < GREY_SATURATION) {
			final int inverted = invertRgb(rgb);
			if (contrastRatio(inverted, paper) >= MIN_GREY_CONTRAST) {
				return inverted;
			}
			return inkFor(paper);
		}
		int cur = rgb;
		for (int i = 0; i < 12; i++) {
			if (contrastRatio(cur, paper) >= MIN_CHROMA_CONTRAST) {
				return cur;
			}
			cur = mix(cur, 0xFF000000, 0.18f);
		}
		return cur;
	}

	static int contrastWash(final int rgb, final int paper) {
		final double dy = Math.abs(luminance(rgb) - luminance(paper));
		if (dy >= 0.12) {
			return rgb;
		}
		if (saturation(rgb) < GREY_SATURATION) {
			final int inverted = invertRgb(rgb);
			if (Math.abs(luminance(inverted) - luminance(paper)) >= 0.12) {
				return inverted;
			}
			return 0xFF5A5A5A;
		}
		return mix(rgb, 0xFF000000, 0.40f);
	}

	static int invertRgb(final int rgb) {
		final int r = 255 - ((rgb >> 16) & 0xFF);
		final int g = 255 - ((rgb >> 8) & 0xFF);
		final int b = 255 - (rgb & 0xFF);
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}

	static int mix(final int from, final int to, final float t) {
		final float u = t < 0f ? 0f : (t > 1f ? 1f : t);
		final int r = Math.round(((from >> 16) & 0xFF) * (1f - u) + ((to >> 16) & 0xFF) * u);
		final int g = Math.round(((from >> 8) & 0xFF) * (1f - u) + ((to >> 8) & 0xFF) * u);
		final int b = Math.round((from & 0xFF) * (1f - u) + (to & 0xFF) * u);
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}

	static double luminance(final int argb) {
		return 0.2126 * srgbToLinear((argb >> 16) & 0xFF)
				+ 0.7152 * srgbToLinear((argb >> 8) & 0xFF)
				+ 0.0722 * srgbToLinear(argb & 0xFF);
	}

	static double contrastRatio(final int a, final int b) {
		final double l1 = luminance(a);
		final double l2 = luminance(b);
		final double hi = l1 > l2 ? l1 : l2;
		final double lo = l1 > l2 ? l2 : l1;
		return (hi + 0.05) / (lo + 0.05);
	}

	static double saturation(final int rgb) {
		final int r = (rgb >> 16) & 0xFF;
		final int g = (rgb >> 8) & 0xFF;
		final int b = rgb & 0xFF;
		final int max = r > g ? (r > b ? r : b) : (g > b ? g : b);
		final int min = r < g ? (r < b ? r : b) : (g < b ? g : b);
		if (max == 0) {
			return 0.0;
		}
		return (max - min) / (double) max;
	}

	private static double srgbToLinear(final int channel) {
		final double s = channel / 255.0;
		if (s <= 0.04045) {
			return s / 12.92;
		}
		return Math.pow((s + 0.055) / 1.055, 2.4);
	}
}
