package com.resurrection.blowtorch2.lib.trigger.style;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.service.SgrStyle;

/**
 * One glyph's incoming SGR recipe. Colour spaces are what the MUD sent, not
 * the light-paper remap.
 */
public final class StyleSnapshot {

	public enum ColorSpace {
		ANSI16,
		XTERM256,
		RGB
	}

	public final ColorSpace fgSpace;
	public final int fgCode;
	public final ColorSpace bgSpace;
	public final int bgCode;
	/** SGR 1. Not {@link SgrStyle#WEIGHT}. */
	public final boolean bright;
	public final int sgrBits;
	public final String href;

	public StyleSnapshot(final ColorSpace fgSpace, final int fgCode,
			final ColorSpace bgSpace, final int bgCode, final boolean bright,
			final int sgrBits, final String href) {
		this.fgSpace = fgSpace == null ? ColorSpace.ANSI16 : fgSpace;
		this.fgCode = fgCode;
		this.bgSpace = bgSpace == null ? ColorSpace.ANSI16 : bgSpace;
		this.bgCode = bgCode;
		this.bright = bright;
		this.sgrBits = sgrBits;
		this.href = href;
	}

	static StyleSnapshot fromRegisters(final SgrRegisters r) {
		ColorSpace fgSpace;
		int fgCode;
		if (r.trueColorFG()) {
			fgSpace = ColorSpace.RGB;
			fgCode = r.selectedColor() == null ? 0 : (r.selectedColor().intValue() & 0xFFFFFF);
		} else if (r.xterm256FG()) {
			fgSpace = ColorSpace.XTERM256;
			fgCode = r.selectedColor() == null ? 0 : r.selectedColor().intValue();
		} else {
			fgSpace = ColorSpace.ANSI16;
			fgCode = r.selectedColor() == null ? 37 : r.selectedColor().intValue();
		}
		ColorSpace bgSpace;
		int bgCode;
		if (r.trueColorBG()) {
			bgSpace = ColorSpace.RGB;
			bgCode = r.selectedBackground() == null ? 0
					: (r.selectedBackground().intValue() & 0xFFFFFF);
		} else if (r.xterm256BG()) {
			bgSpace = ColorSpace.XTERM256;
			bgCode = r.selectedBackground() == null ? 0 : r.selectedBackground().intValue();
		} else {
			bgSpace = ColorSpace.ANSI16;
			bgCode = r.selectedBackground() == null ? 40 : r.selectedBackground().intValue();
		}
		boolean bright = r.selectedBright() != null && r.selectedBright().intValue() != 0;
		return new StyleSnapshot(fgSpace, fgCode, bgSpace, bgCode, bright, r.sgrBits(),
				r.href());
	}

	public boolean weight() {
		return (sgrBits & SgrStyle.WEIGHT) != 0;
	}

	public boolean italic() {
		return (sgrBits & SgrStyle.ITALIC) != 0;
	}

	public boolean underline() {
		return (sgrBits & SgrStyle.UNDERLINE) != 0;
	}

	public boolean doubleUnderline() {
		return (sgrBits & SgrStyle.DOUBLE_UNDERLINE) != 0;
	}

	public boolean strike() {
		return (sgrBits & SgrStyle.STRIKE) != 0;
	}

	public boolean reverse() {
		return (sgrBits & SgrStyle.REVERSE) != 0;
	}

	public boolean faint() {
		return (sgrBits & SgrStyle.FAINT) != 0;
	}

	public boolean blink() {
		return (sgrBits & SgrStyle.BLINK) != 0 || (sgrBits & SgrStyle.FAST_BLINK) != 0;
	}

	public boolean hasHref() {
		return href != null && href.length() > 0;
	}

	/** RGB the draw path would use before LightPaper. */
	public int fgLooksArgb() {
		if (fgSpace == ColorSpace.RGB) {
			return 0xFF000000 | (fgCode & 0xFFFFFF);
		}
		Integer brightArg = bright ? Integer.valueOf(1) : Integer.valueOf(0);
		boolean xterm = fgSpace == ColorSpace.XTERM256;
		return 0xFF000000 | Colorizer.getColorValue(brightArg, Integer.valueOf(fgCode), xterm);
	}

	public int bgLooksArgb() {
		if (bgSpace == ColorSpace.RGB) {
			return 0xFF000000 | (bgCode & 0xFFFFFF);
		}
		boolean xterm = bgSpace == ColorSpace.XTERM256;
		return 0xFF000000 | Colorizer.getColorValue(Integer.valueOf(0),
				Integer.valueOf(bgCode), xterm);
	}

	public boolean sameRecipe(final StyleSnapshot o) {
		if (o == null) {
			return false;
		}
		return fgSpace == o.fgSpace && fgCode == o.fgCode && bgSpace == o.bgSpace
				&& bgCode == o.bgCode && bright == o.bright && sgrBits == o.sgrBits
				&& hrefEquals(o.href);
	}

	private boolean hrefEquals(final String other) {
		if (href == null || href.length() == 0) {
			return other == null || other.length() == 0;
		}
		return href.equals(other);
	}

	@Override
	public boolean equals(final Object o) {
		if (o == this) {
			return true;
		}
		if (!(o instanceof StyleSnapshot)) {
			return false;
		}
		return sameRecipe((StyleSnapshot) o);
	}

	@Override
	public int hashCode() {
		int h = fgSpace.ordinal();
		h = 31 * h + fgCode;
		h = 31 * h + bgSpace.ordinal();
		h = 31 * h + bgCode;
		h = 31 * h + (bright ? 1 : 0);
		h = 31 * h + sgrBits;
		h = 31 * h + (href == null ? 0 : href.hashCode());
		return h;
	}

	public String fgLabel() {
		return colorLabel(fgSpace, fgCode, true);
	}

	public String bgLabel() {
		return colorLabel(bgSpace, bgCode, false);
	}

	static String colorLabel(final ColorSpace space, final int code,
			final boolean foreground) {
		if (space == ColorSpace.XTERM256) {
			return "xterm " + code;
		}
		if (space == ColorSpace.RGB) {
			return String.format("rgb #%06x", Integer.valueOf(code & 0xFFFFFF));
		}
		return "ansi " + code;
	}
}
