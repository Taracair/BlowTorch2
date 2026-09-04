package com.resurrection.blowtorch2.lib.responder.color;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.resurrection.blowtorch2.lib.service.SgrStyle;

/**
 * What a colour trigger paints: per-channel mode (keep / xterm / rgb / reset
 * background), optional SGR styles. Old profiles store two xterm ints;
 * background 0, 16 and 231 meant "foreground only" (SGR 49), not those
 * palette slots.
 */
public final class TriggerColorPaint {

	public enum FgMode {
		KEEP,
		XTERM,
		RGB
	}

	public enum BgMode {
		KEEP,
		XTERM,
		RGB,
		RESET
	}

	public static final int STYLE_BOLD = 1;
	public static final int STYLE_FAINT = 2;
	public static final int STYLE_ITALIC = 4;
	public static final int STYLE_UNDERLINE = 8;
	public static final int STYLE_REVERSE = 16;
	public static final int STYLE_STRIKE = 32;

	public static final int DEFAULT_FG_XTERM = ColorAction.DEFAULT_COLOR;
	public static final int DEFAULT_BG_XTERM = ColorAction.DEFAULT_BACKGROUND_COLOR;

	public static final String TOKEN_KEEP = "keep";
	public static final String TOKEN_DEFAULT = "default";
	public static final String MODE_XTERM = "xterm";
	public static final String MODE_RGB = "rgb";
	public static final String MODE_KEEP = "keep";
	public static final String MODE_RESET = "reset";

	private FgMode fgMode = FgMode.XTERM;
	private int fgXterm = DEFAULT_FG_XTERM;
	private int fgRgb = 0x00BBBBBB;

	private BgMode bgMode = BgMode.XTERM;
	private int bgXterm = DEFAULT_BG_XTERM;
	private int bgRgb = 0x00080808;

	private int styles;

	public static TriggerColorPaint legacyDefaults() {
		TriggerColorPaint p = new TriggerColorPaint();
		p.fgMode = FgMode.XTERM;
		p.fgXterm = DEFAULT_FG_XTERM;
		p.bgMode = BgMode.XTERM;
		p.bgXterm = DEFAULT_BG_XTERM;
		p.styles = 0;
		return p;
	}

	public static TriggerColorPaint fromLegacyInts(int fg, int bg) {
		TriggerColorPaint p = new TriggerColorPaint();
		p.setForegroundXterm(fg);
		p.setBackgroundLegacyIndex(bg);
		return p;
	}

	public static TriggerColorPaint fromParcelFields(int fgMode, int fgXterm,
			int fgRgb, int bgMode, int bgXterm, int bgRgb, int styles) {
		TriggerColorPaint p = new TriggerColorPaint();
		FgMode[] fgs = FgMode.values();
		BgMode[] bgs = BgMode.values();
		if (fgMode >= 0 && fgMode < fgs.length) {
			p.fgMode = fgs[fgMode];
		}
		if (bgMode >= 0 && bgMode < bgs.length) {
			p.bgMode = bgs[bgMode];
		}
		p.fgXterm = fgXterm;
		p.fgRgb = fgRgb & 0x00FFFFFF;
		p.bgXterm = bgXterm;
		p.bgRgb = bgRgb & 0x00FFFFFF;
		p.setStyles(styles);
		return p;
	}

	public static boolean isLegacyBackgroundSentinel(int index) {
		return ColorAction.skipsBackgroundPaint(index);
	}

	public TriggerColorPaint copy() {
		TriggerColorPaint p = new TriggerColorPaint();
		p.fgMode = this.fgMode;
		p.fgXterm = this.fgXterm;
		p.fgRgb = this.fgRgb;
		p.bgMode = this.bgMode;
		p.bgXterm = this.bgXterm;
		p.bgRgb = this.bgRgb;
		p.styles = this.styles;
		return p;
	}

	public FgMode getFgMode() {
		return fgMode;
	}

	public BgMode getBgMode() {
		return bgMode;
	}

	public int getFgXterm() {
		return fgXterm;
	}

	public int getBgXterm() {
		return bgXterm;
	}

	public int getFgRgb() {
		return fgRgb;
	}

	public int getBgRgb() {
		return bgRgb;
	}

	public int getStyles() {
		return styles;
	}

	public void setStyles(int flags) {
		this.styles = flags & (STYLE_BOLD | STYLE_FAINT | STYLE_ITALIC
				| STYLE_UNDERLINE | STYLE_REVERSE | STYLE_STRIKE);
	}

	public boolean hasStyle(int flag) {
		return (styles & flag) != 0;
	}

	public void setStyle(int flag, boolean on) {
		if (on) {
			styles |= flag;
		} else {
			styles &= ~flag;
		}
	}

	public void setForegroundKeep() {
		fgMode = FgMode.KEEP;
	}

	public void setForegroundXterm(int index) {
		fgMode = FgMode.XTERM;
		fgXterm = index;
	}

	public void setForegroundRgb(int packed) {
		fgMode = FgMode.RGB;
		fgRgb = packed & 0x00FFFFFF;
	}

	public void setBackgroundKeep() {
		bgMode = BgMode.KEEP;
	}

	public void setBackgroundReset() {
		bgMode = BgMode.RESET;
	}

	/** Paint this xterm index, including 0 / 16 / 231. */
	public void setBackgroundXterm(int index) {
		bgMode = BgMode.XTERM;
		bgXterm = index;
	}

	/**
	 * {@link ColorAction#setBackgroundColor(int)}: 0, 16, 231 stay "foreground
	 * only" (SGR 49). Any other index is a real xterm background.
	 */
	public void setBackgroundLegacyIndex(int index) {
		bgXterm = index;
		if (isLegacyBackgroundSentinel(index)) {
			bgMode = BgMode.RESET;
		} else {
			bgMode = BgMode.XTERM;
		}
	}

	public void setBackgroundRgb(int packed) {
		bgMode = BgMode.RGB;
		bgRgb = packed & 0x00FFFFFF;
	}

	public boolean paintsForeground() {
		return fgMode == FgMode.XTERM || fgMode == FgMode.RGB;
	}

	public boolean paintsBackground() {
		return bgMode == BgMode.XTERM || bgMode == BgMode.RGB;
	}

	public boolean resetsBackground() {
		return bgMode == BgMode.RESET;
	}

	public boolean keepsBackground() {
		return bgMode == BgMode.KEEP;
	}

	public boolean hasStyles() {
		return styles != 0;
	}

	public boolean isNoOp() {
		return !paintsForeground() && !paintsBackground() && !resetsBackground()
				&& !hasStyles();
	}

	/**
	 * Int the old field still reports. RESET reports 16 so
	 * {@link ColorAction#skipsBackgroundPaint(int)} stays true for tests that
	 * only look at the int.
	 */
	public int legacyForegroundInt() {
		return fgXterm;
	}

	public int legacyBackgroundInt() {
		if (bgMode == BgMode.RESET) {
			return 16;
		}
		if (bgMode == BgMode.KEEP) {
			return 16;
		}
		return bgXterm;
	}

	public List<Integer> toForegroundSgrOps() {
		ArrayList<Integer> ops = new ArrayList<Integer>();
		if (fgMode == FgMode.XTERM) {
			ops.add(Integer.valueOf(38));
			ops.add(Integer.valueOf(5));
			ops.add(Integer.valueOf(fgXterm));
		} else if (fgMode == FgMode.RGB) {
			appendTruecolor(ops, 38, fgRgb);
		}
		return ops;
	}

	public List<Integer> toBackgroundSgrOps() {
		ArrayList<Integer> ops = new ArrayList<Integer>();
		if (bgMode == BgMode.RESET) {
			ops.add(Integer.valueOf(49));
		} else if (bgMode == BgMode.XTERM) {
			ops.add(Integer.valueOf(48));
			ops.add(Integer.valueOf(5));
			ops.add(Integer.valueOf(bgXterm));
		} else if (bgMode == BgMode.RGB) {
			appendTruecolor(ops, 48, bgRgb);
		}
		return ops;
	}

	public List<Integer> toStyleOnOps() {
		ArrayList<Integer> ops = new ArrayList<Integer>();
		if (hasStyle(STYLE_BOLD)) {
			ops.add(Integer.valueOf(SgrStyle.WEIGHT_ON_CODE));
		}
		if (hasStyle(STYLE_FAINT)) {
			ops.add(Integer.valueOf(2));
		}
		if (hasStyle(STYLE_ITALIC)) {
			ops.add(Integer.valueOf(3));
		}
		if (hasStyle(STYLE_UNDERLINE)) {
			ops.add(Integer.valueOf(4));
		}
		if (hasStyle(STYLE_REVERSE)) {
			ops.add(Integer.valueOf(7));
		}
		if (hasStyle(STYLE_STRIKE)) {
			ops.add(Integer.valueOf(9));
		}
		return ops;
	}

	public List<Integer> toSgrOps() {
		ArrayList<Integer> ops = new ArrayList<Integer>();
		ops.addAll(toForegroundSgrOps());
		ops.addAll(toBackgroundSgrOps());
		ops.addAll(toStyleOnOps());
		return ops;
	}

	public List<Integer> toStyleOffOps() {
		ArrayList<Integer> ops = new ArrayList<Integer>();
		if (hasStyle(STYLE_BOLD)) {
			ops.add(Integer.valueOf(SgrStyle.WEIGHT_OFF_CODE));
		}
		if (hasStyle(STYLE_FAINT)) {
			ops.add(Integer.valueOf(22));
		}
		if (hasStyle(STYLE_ITALIC)) {
			ops.add(Integer.valueOf(23));
		}
		if (hasStyle(STYLE_UNDERLINE)) {
			ops.add(Integer.valueOf(24));
		}
		if (hasStyle(STYLE_REVERSE)) {
			ops.add(Integer.valueOf(27));
		}
		if (hasStyle(STYLE_STRIKE)) {
			ops.add(Integer.valueOf(29));
		}
		return ops;
	}

	private static void appendTruecolor(List<Integer> ops, int intro, int rgb) {
		ops.add(Integer.valueOf(intro));
		ops.add(Integer.valueOf(2));
		ops.add(Integer.valueOf((rgb >> 16) & 0xFF));
		ops.add(Integer.valueOf((rgb >> 8) & 0xFF));
		ops.add(Integer.valueOf(rgb & 0xFF));
	}

	public String formatTextAttr() {
		return formatFgToken();
	}

	public String formatBackgroundAttr() {
		return formatBgToken();
	}

	public boolean needsTextModeAttr() {
		return false;
	}

	/**
	 * Bare {@code 16} still means sentinel. A real xterm 0/16/231 background
	 * must say {@code backgroundMode="xterm"}.
	 */
	public boolean needsBackgroundModeXtermAttr() {
		return bgMode == BgMode.XTERM && isLegacyBackgroundSentinel(bgXterm);
	}

	public boolean canEmitLegacyInts() {
		if (styles != 0) {
			return false;
		}
		if (fgMode != FgMode.XTERM) {
			return false;
		}
		if (bgMode == BgMode.RESET) {
			return true;
		}
		if (bgMode == BgMode.XTERM && !isLegacyBackgroundSentinel(bgXterm)) {
			return true;
		}
		return false;
	}

	public String formatTextModeAttr() {
		return null;
	}

	public String formatBackgroundModeAttr() {
		if (needsBackgroundModeXtermAttr()) {
			return MODE_XTERM;
		}
		return null;
	}

	public static TriggerColorPaint fromXml(
			String text,
			String textMode,
			String background,
			String backgroundMode,
			boolean bold,
			boolean faint,
			boolean italic,
			boolean underline,
			boolean reverse,
			boolean strike) {
		TriggerColorPaint p = legacyDefaults();
		parseFgToken(p, text, textMode);
		parseBgToken(p, background, backgroundMode);
		p.setStyle(STYLE_BOLD, bold);
		p.setStyle(STYLE_FAINT, faint);
		p.setStyle(STYLE_ITALIC, italic);
		p.setStyle(STYLE_UNDERLINE, underline);
		p.setStyle(STYLE_REVERSE, reverse);
		p.setStyle(STYLE_STRIKE, strike);
		return p;
	}

	/**
	 * Lua {@code foreground}/{@code background}: number = legacy xterm
	 * (background 0/16/231 still reset); {@code false} or {@code "keep"} =
	 * leave that channel; {@code "default"} on background = SGR 49;
	 * {@code "#RRGGBB"} = truecolor; table {@code { xterm = n }} paints that
	 * index including sentinels; {@code { rgb = "#rrggbb" }} or
	 * {@code { r=, g=, b= }}.
	 */
	public static TriggerColorPaint fromLua(Object foreground, Object background) {
		TriggerColorPaint p = legacyDefaults();
		if (foreground != null) {
			applyLuaChannel(p, foreground, true);
		}
		if (background != null) {
			applyLuaChannel(p, background, false);
		}
		return p;
	}

	public static void applyLuaStyles(TriggerColorPaint p, Object bold,
			Object faint, Object italic, Object underline, Object reverse,
			Object strike) {
		if (p == null) {
			return;
		}
		p.setStyle(STYLE_BOLD, luaTrue(bold));
		p.setStyle(STYLE_FAINT, luaTrue(faint));
		p.setStyle(STYLE_ITALIC, luaTrue(italic));
		p.setStyle(STYLE_UNDERLINE, luaTrue(underline));
		p.setStyle(STYLE_REVERSE, luaTrue(reverse));
		p.setStyle(STYLE_STRIKE, luaTrue(strike));
	}

	public String summary() {
		StringBuilder b = new StringBuilder();
		b.append(channelSummary(true));
		b.append(" / ");
		b.append(channelSummary(false));
		if (hasStyle(STYLE_BOLD)) {
			b.append(" bold");
		}
		if (hasStyle(STYLE_FAINT)) {
			b.append(" faint");
		}
		if (hasStyle(STYLE_ITALIC)) {
			b.append(" italic");
		}
		if (hasStyle(STYLE_UNDERLINE)) {
			b.append(" ul");
		}
		if (hasStyle(STYLE_REVERSE)) {
			b.append(" rev");
		}
		if (hasStyle(STYLE_STRIKE)) {
			b.append(" strike");
		}
		return b.toString();
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (!(o instanceof TriggerColorPaint)) {
			return false;
		}
		TriggerColorPaint b = (TriggerColorPaint) o;
		return fgMode == b.fgMode && fgXterm == b.fgXterm && fgRgb == b.fgRgb
				&& bgMode == b.bgMode && bgXterm == b.bgXterm && bgRgb == b.bgRgb
				&& styles == b.styles;
	}

	@Override
	public int hashCode() {
		int h = fgMode.hashCode();
		h = 31 * h + fgXterm;
		h = 31 * h + fgRgb;
		h = 31 * h + bgMode.hashCode();
		h = 31 * h + bgXterm;
		h = 31 * h + bgRgb;
		h = 31 * h + styles;
		return h;
	}

	private String formatFgToken() {
		switch (fgMode) {
		case KEEP:
			return TOKEN_KEEP;
		case RGB:
			return formatHex(fgRgb);
		case XTERM:
		default:
			return Integer.toString(fgXterm);
		}
	}

	private String formatBgToken() {
		switch (bgMode) {
		case KEEP:
			return TOKEN_KEEP;
		case RESET:
			if (canEmitLegacyInts()) {
				return "16";
			}
			return TOKEN_DEFAULT;
		case RGB:
			return formatHex(bgRgb);
		case XTERM:
		default:
			return Integer.toString(bgXterm);
		}
	}

	private String channelSummary(boolean fg) {
		if (fg) {
			switch (fgMode) {
			case KEEP:
				return "fg keep";
			case RGB:
				return formatHex(fgRgb);
			case XTERM:
			default:
				return Integer.toString(fgXterm);
			}
		}
		switch (bgMode) {
		case KEEP:
			return "bg keep";
		case RESET:
			return "bg default";
		case RGB:
			return "bg " + formatHex(bgRgb);
		case XTERM:
		default:
			return "bg " + bgXterm;
		}
	}

	static String formatHex(int rgb) {
		int n = rgb & 0x00FFFFFF;
		String h = Integer.toHexString(n).toUpperCase(Locale.US);
		while (h.length() < 6) {
			h = "0" + h;
		}
		return "#" + h;
	}

	static Integer parseHexRgb(String raw) {
		if (raw == null) {
			return null;
		}
		String s = raw.trim();
		if (s.length() < 2 || s.charAt(0) != '#') {
			return null;
		}
		String h = s.substring(1);
		try {
			if (h.length() == 3) {
				int r = Integer.parseInt(h.substring(0, 1), 16) * 17;
				int g = Integer.parseInt(h.substring(1, 2), 16) * 17;
				int b = Integer.parseInt(h.substring(2, 3), 16) * 17;
				return Integer.valueOf((r << 16) | (g << 8) | b);
			}
			if (h.length() == 6) {
				return Integer.valueOf(Integer.parseInt(h, 16));
			}
			if (h.length() == 8) {
				int v = (int) Long.parseLong(h, 16);
				return Integer.valueOf(v & 0x00FFFFFF);
			}
		} catch (NumberFormatException e) {
			return null;
		}
		return null;
	}

	private static void parseFgToken(TriggerColorPaint p, String text,
			String textMode) {
		if (text == null || text.length() == 0) {
			return;
		}
		String t = text.trim();
		String mode = modeOrNull(textMode);
		if (TOKEN_KEEP.equalsIgnoreCase(t) || MODE_KEEP.equals(mode)) {
			p.setForegroundKeep();
			return;
		}
		Integer hex = parseHexRgb(t);
		if (hex != null || MODE_RGB.equals(mode)) {
			if (hex != null) {
				p.setForegroundRgb(hex.intValue());
			}
			return;
		}
		Integer n = parseIntLoose(t);
		if (n != null) {
			p.setForegroundXterm(n.intValue());
		}
	}

	private static void parseBgToken(TriggerColorPaint p, String background,
			String backgroundMode) {
		if (background == null || background.length() == 0) {
			return;
		}
		String t = background.trim();
		String mode = modeOrNull(backgroundMode);
		if (TOKEN_KEEP.equalsIgnoreCase(t) || MODE_KEEP.equals(mode)) {
			p.setBackgroundKeep();
			return;
		}
		if (TOKEN_DEFAULT.equalsIgnoreCase(t) || MODE_RESET.equals(mode)) {
			p.setBackgroundReset();
			return;
		}
		Integer hex = parseHexRgb(t);
		if (hex != null || MODE_RGB.equals(mode)) {
			if (hex != null) {
				p.setBackgroundRgb(hex.intValue());
			}
			return;
		}
		Integer n = parseIntLoose(t);
		if (n == null) {
			return;
		}
		if (MODE_XTERM.equals(mode)) {
			p.setBackgroundXterm(n.intValue());
			return;
		}
		p.setBackgroundLegacyIndex(n.intValue());
	}

	private static String modeOrNull(String mode) {
		if (mode == null) {
			return null;
		}
		String m = mode.trim().toLowerCase(Locale.US);
		if (m.length() == 0) {
			return null;
		}
		return m;
	}

	private static Integer parseIntLoose(String t) {
		try {
			return Integer.valueOf(Integer.parseInt(t.trim()));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	@SuppressWarnings("rawtypes")
	private static void applyLuaChannel(TriggerColorPaint p, Object value,
			boolean fg) {
		if (value instanceof Boolean) {
			if (!((Boolean) value).booleanValue()) {
				if (fg) {
					p.setForegroundKeep();
				} else {
					p.setBackgroundKeep();
				}
			}
			return;
		}
		if (value instanceof Number) {
			int n = ((Number) value).intValue();
			if (fg) {
				p.setForegroundXterm(n);
			} else {
				p.setBackgroundLegacyIndex(n);
			}
			return;
		}
		if (value instanceof String) {
			if (fg) {
				parseFgToken(p, (String) value, null);
			} else {
				parseBgToken(p, (String) value, null);
			}
			return;
		}
		if (value instanceof Map) {
			Map map = (Map) value;
			Object xterm = map.get("xterm");
			if (xterm instanceof Number) {
				int n = ((Number) xterm).intValue();
				if (fg) {
					p.setForegroundXterm(n);
				} else {
					p.setBackgroundXterm(n);
				}
				return;
			}
			Object rgb = map.get("rgb");
			if (rgb instanceof String) {
				Integer hex = parseHexRgb((String) rgb);
				if (hex != null) {
					if (fg) {
						p.setForegroundRgb(hex.intValue());
					} else {
						p.setBackgroundRgb(hex.intValue());
					}
					return;
				}
			}
			Integer r = luaByte(map.get("r"));
			Integer g = luaByte(map.get("g"));
			Integer b = luaByte(map.get("b"));
			if (r != null && g != null && b != null) {
				int packed = (r.intValue() << 16) | (g.intValue() << 8)
						| b.intValue();
				if (fg) {
					p.setForegroundRgb(packed);
				} else {
					p.setBackgroundRgb(packed);
				}
			}
		}
	}

	private static Integer luaByte(Object o) {
		if (!(o instanceof Number)) {
			return null;
		}
		int n = ((Number) o).intValue();
		if (n < 0) {
			n = 0;
		} else if (n > 255) {
			n = 255;
		}
		return Integer.valueOf(n);
	}

	private static boolean luaTrue(Object o) {
		if (o instanceof Boolean) {
			return ((Boolean) o).booleanValue();
		}
		if (o instanceof Number) {
			return ((Number) o).intValue() != 0;
		}
		if (o instanceof String) {
			return "true".equalsIgnoreCase(((String) o).trim())
					|| "1".equals(((String) o).trim());
		}
		return false;
	}
}
