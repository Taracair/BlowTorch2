/*
 * Copyright (C) BlowTorch contributors
 */
package com.resurrection.blowtorch2.lib.gauge;

import java.util.Locale;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * One overlay gauge (bar, ring, or countdown timer). Live value/max and
 * {@code remainSec} are session memory and are not written by
 * {@link #toPersistedJson()}. Widgets are player-created; MXP GAUGE does not
 * mint them.
 */
public final class GaugeWidget {

	public static final int DEFAULT_COLOR_FILL = 0xFFCC2222;
	public static final int DEFAULT_COLOR_TRACK = 0xFF333333;
	public static final int DEFAULT_COLOR_WARN = 0xFFFFAA00;
	public static final int DEFAULT_WARN_PCT = 25;
	public static final int DEFAULT_OPACITY = 85;
	public static final double DEFAULT_LIVE_MAX = 100.0;

	public enum Shape {
		HBAR("hbar"),
		VBAR("vbar"),
		RING("ring"),
		TIMER("timer");

		private final String jsonValue;

		Shape(final String jsonValue) {
			this.jsonValue = jsonValue;
		}

		public String toJsonValue() {
			return jsonValue;
		}

		/**
		 * Parse a shape string. {@code bar} → {@link #HBAR}, {@code vertical} →
		 * {@link #VBAR}, {@code circle}/{@code pie}/{@code zelda} → {@link #RING},
		 * {@code countdown} → {@link #TIMER}. Unknown / null → {@link #HBAR}.
		 */
		public static Shape fromJsonValue(final String raw) {
			if (raw == null) {
				return HBAR;
			}
			String s = raw.trim().toLowerCase(Locale.US);
			if ("hbar".equals(s) || "bar".equals(s)) {
				return HBAR;
			}
			if ("vbar".equals(s) || "vertical".equals(s)) {
				return VBAR;
			}
			if ("ring".equals(s) || "circle".equals(s) || "pie".equals(s)
					|| "zelda".equals(s)) {
				return RING;
			}
			if ("timer".equals(s) || "countdown".equals(s)) {
				return TIMER;
			}
			for (Shape sh : values()) {
				if (sh.jsonValue.equals(s)) {
					return sh;
				}
			}
			return HBAR;
		}
	}

	public enum Source {
		MANUAL("manual"),
		GMCP("gmcp"),
		MCP("mcp"),
		VAR("var"),
		TIMER("timer");

		private final String jsonValue;

		Source(final String jsonValue) {
			this.jsonValue = jsonValue;
		}

		public String toJsonValue() {
			return jsonValue;
		}

		/** Unknown / null → {@link #MANUAL}. */
		public static Source fromJsonValue(final String raw) {
			if (raw == null) {
				return MANUAL;
			}
			String s = raw.trim().toLowerCase(Locale.US);
			for (Source src : values()) {
				if (src.jsonValue.equals(s)) {
					return src;
				}
			}
			return MANUAL;
		}
	}

	/**
	 * Where the overlay sits while the IME is up. Default {@link #STAY}: on the
	 * game window, following IME lift. {@link #HIDE} is gone while the IME is
	 * up. {@link #OVERLAY} may sit over the IME ({@code TYPE_APPLICATION_OVERLAY},
	 * same permission as floating buttons).
	 */
	public enum ImeMode {
		STAY("stay"),
		HIDE("hide"),
		OVERLAY("overlay");

		private final String jsonValue;

		ImeMode(final String jsonValue) {
			this.jsonValue = jsonValue;
		}

		public String toJsonValue() {
			return jsonValue;
		}

		/**
		 * {@code over}/{@code float} → {@link #OVERLAY}, {@code game} →
		 * {@link #STAY}. Unknown / null → {@link #STAY}.
		 */
		public static ImeMode fromJsonValue(final String raw) {
			if (raw == null) {
				return STAY;
			}
			String s = raw.trim().toLowerCase(Locale.US);
			if ("stay".equals(s) || "game".equals(s)) {
				return STAY;
			}
			if ("hide".equals(s)) {
				return HIDE;
			}
			if ("overlay".equals(s) || "over".equals(s) || "float".equals(s)) {
				return OVERLAY;
			}
			for (ImeMode m : values()) {
				if (m.jsonValue.equals(s)) {
					return m;
				}
			}
			return STAY;
		}
	}

	private String id = "";
	private String label = "";
	private Shape shape = Shape.HBAR;
	private Source source = Source.MANUAL;
	private String path = "";
	private String maxPath = "";
	private int colorFill = DEFAULT_COLOR_FILL;
	private int colorTrack = DEFAULT_COLOR_TRACK;
	private int colorWarn = DEFAULT_COLOR_WARN;
	private int warnPct = DEFAULT_WARN_PCT;
	private int opacity = DEFAULT_OPACITY;
	private boolean showValue = true;
	private boolean showLabel = true;
	private boolean visible = true;
	private int x = 0;
	private int y = 0;
	private int w = 200;
	private int h = 24;
	/** 0 together with the other land* fields means copy portrait. */
	private int landX = 0;
	private int landY = 0;
	private int landW = 0;
	private int landH = 0;
	private String tapCommand = "";
	private String swipeUp = "";
	private String swipeDown = "";
	private String swipeLeft = "";
	private String swipeRight = "";
	private String holdCommand = "";
	private ImeMode imeMode = ImeMode.STAY;
	/** Full timer length in seconds; 0 = unset. Persisted. */
	private double durationSec = 0.0;
	/**
	 * Optional client {@code .timer} name. When {@link Source#TIMER},
	 * {@link #path} also holds this name.
	 */
	private String timerName = "";
	/** Transient; not persisted. */
	private double liveValue = 0.0;
	/** Transient; not persisted. Default 100. */
	private double liveMax = DEFAULT_LIVE_MAX;
	/** Transient remaining seconds for {@link Shape#TIMER}; not persisted. */
	private double remainSec = 0.0;

	public GaugeWidget() {
	}

	public GaugeWidget(final String id) {
		this.id = id != null ? id : "";
	}

	public String getId() {
		return id != null ? id : "";
	}

	public void setId(final String id) {
		this.id = id != null ? id : "";
	}

	public String getLabel() {
		return label != null ? label : "";
	}

	public void setLabel(final String label) {
		this.label = label != null ? label : "";
	}

	public Shape getShape() {
		return shape != null ? shape : Shape.HBAR;
	}

	public void setShape(final Shape shape) {
		this.shape = shape != null ? shape : Shape.HBAR;
	}

	public Source getSource() {
		return source != null ? source : Source.MANUAL;
	}

	public void setSource(final Source source) {
		this.source = source != null ? source : Source.MANUAL;
	}

	public String getPath() {
		return path != null ? path : "";
	}

	public void setPath(final String path) {
		this.path = path != null ? path : "";
	}

	public String getMaxPath() {
		return maxPath != null ? maxPath : "";
	}

	public void setMaxPath(final String maxPath) {
		this.maxPath = maxPath != null ? maxPath : "";
	}

	public int getColorFill() {
		return colorFill;
	}

	public void setColorFill(final int colorFill) {
		this.colorFill = colorFill;
	}

	public int getColorTrack() {
		return colorTrack;
	}

	public void setColorTrack(final int colorTrack) {
		this.colorTrack = colorTrack;
	}

	public int getColorWarn() {
		return colorWarn;
	}

	public void setColorWarn(final int colorWarn) {
		this.colorWarn = colorWarn;
	}

	public int getWarnPct() {
		return warnPct;
	}

	public void setWarnPct(final int warnPct) {
		if (warnPct < 0) {
			this.warnPct = 0;
		} else if (warnPct > 100) {
			this.warnPct = 100;
		} else {
			this.warnPct = warnPct;
		}
	}

	public int getOpacity() {
		return opacity;
	}

	/** Clamp to 10–100. */
	public void setOpacity(final int opacity) {
		if (opacity < 10) {
			this.opacity = 10;
		} else if (opacity > 100) {
			this.opacity = 100;
		} else {
			this.opacity = opacity;
		}
	}

	public boolean isShowValue() {
		return showValue;
	}

	public void setShowValue(final boolean showValue) {
		this.showValue = showValue;
	}

	public boolean isShowLabel() {
		return showLabel;
	}

	public void setShowLabel(final boolean showLabel) {
		this.showLabel = showLabel;
	}

	public boolean isVisible() {
		return visible;
	}

	public void setVisible(final boolean visible) {
		this.visible = visible;
	}

	public int getX() {
		return x;
	}

	public void setX(final int x) {
		this.x = x;
	}

	public int getY() {
		return y;
	}

	public void setY(final int y) {
		this.y = y;
	}

	public int getW() {
		return w;
	}

	public void setW(final int w) {
		this.w = w < 1 ? 1 : w;
	}

	public int getH() {
		return h;
	}

	public void setH(final int h) {
		this.h = h < 1 ? 1 : h;
	}

	/** Stored landscape X; {@code 0} means copy {@link #getX()}. */
	public int getLandX() {
		return landX;
	}

	public void setLandX(final int landX) {
		this.landX = landX;
	}

	public int getLandY() {
		return landY;
	}

	public void setLandY(final int landY) {
		this.landY = landY;
	}

	public int getLandW() {
		return landW;
	}

	/** {@code 0} means copy portrait width; negative treated as 0. */
	public void setLandW(final int landW) {
		this.landW = landW < 0 ? 0 : landW;
	}

	public int getLandH() {
		return landH;
	}

	public void setLandH(final int landH) {
		this.landH = landH < 0 ? 0 : landH;
	}

	/**
	 * True once any landscape field is non-zero. All-zero is the persisted
	 * default and still means “copy portrait”. A real landscape layout always
	 * writes size as well, so {@code land_x}/{@code land_y} of 0 (top/left
	 * edge) is distinguishable from unset.
	 */
	public boolean hasLandscapeGeometry() {
		return landX != 0 || landY != 0 || landW != 0 || landH != 0;
	}

	/** Landscape X. Unset (all land* 0) substitutes portrait; 0 is a real edge. */
	public int resolveLandX() {
		return hasLandscapeGeometry() ? landX : x;
	}

	public int resolveLandY() {
		return hasLandscapeGeometry() ? landY : y;
	}

	public int resolveLandW() {
		return landW != 0 ? landW : w;
	}

	public int resolveLandH() {
		return landH != 0 ? landH : h;
	}

	public String getTapCommand() {
		return tapCommand != null ? tapCommand : "";
	}

	public void setTapCommand(final String tapCommand) {
		this.tapCommand = tapCommand != null ? tapCommand : "";
	}

	public String getSwipeUp() {
		return swipeUp != null ? swipeUp : "";
	}

	public void setSwipeUp(final String swipeUp) {
		this.swipeUp = swipeUp != null ? swipeUp : "";
	}

	public String getSwipeDown() {
		return swipeDown != null ? swipeDown : "";
	}

	public void setSwipeDown(final String swipeDown) {
		this.swipeDown = swipeDown != null ? swipeDown : "";
	}

	public String getSwipeLeft() {
		return swipeLeft != null ? swipeLeft : "";
	}

	public void setSwipeLeft(final String swipeLeft) {
		this.swipeLeft = swipeLeft != null ? swipeLeft : "";
	}

	public String getSwipeRight() {
		return swipeRight != null ? swipeRight : "";
	}

	public void setSwipeRight(final String swipeRight) {
		this.swipeRight = swipeRight != null ? swipeRight : "";
	}

	public String getHoldCommand() {
		return holdCommand != null ? holdCommand : "";
	}

	public void setHoldCommand(final String holdCommand) {
		this.holdCommand = holdCommand != null ? holdCommand : "";
	}

	public ImeMode getImeMode() {
		return imeMode != null ? imeMode : ImeMode.STAY;
	}

	public void setImeMode(final ImeMode imeMode) {
		this.imeMode = imeMode != null ? imeMode : ImeMode.STAY;
	}

	public double getDurationSec() {
		return durationSec;
	}

	public void setDurationSec(final double durationSec) {
		if (Double.isNaN(durationSec) || durationSec < 0.0) {
			this.durationSec = 0.0;
		} else {
			this.durationSec = durationSec;
		}
	}

	public String getTimerName() {
		return timerName != null ? timerName : "";
	}

	public void setTimerName(final String timerName) {
		this.timerName = timerName != null ? timerName : "";
	}

	/**
	 * Name of the client {@code .timer} this widget follows: {@link #path}
	 * when {@link Source#TIMER}, otherwise {@link #timerName}.
	 */
	public String resolveTimerName() {
		if (getSource() == Source.TIMER && getPath().length() > 0) {
			return getPath();
		}
		if (getTimerName().length() > 0) {
			return getTimerName();
		}
		return getPath();
	}

	public double getLiveValue() {
		return liveValue;
	}

	public void setLiveValue(final double liveValue) {
		this.liveValue = liveValue;
	}

	public double getLiveMax() {
		return liveMax;
	}

	public void setLiveMax(final double liveMax) {
		this.liveMax = liveMax;
	}

	public double getRemainSec() {
		return remainSec;
	}

	public void setRemainSec(final double remainSec) {
		if (Double.isNaN(remainSec)) {
			this.remainSec = 0.0;
		} else {
			this.remainSec = remainSec;
		}
	}

	/**
	 * Fill fraction clamped to 0..1. {@link Shape#TIMER} uses
	 * {@code remainSec / durationSec} ({@code durationSec <= 0} → 0);
	 * otherwise {@code liveValue / liveMax} ({@code liveMax <= 0} → 0).
	 */
	public double ratio() {
		if (getShape() == Shape.TIMER) {
			return clampRatio(remainSec, durationSec);
		}
		return clampRatio(liveValue, liveMax);
	}

	private static double clampRatio(final double value, final double max) {
		if (max <= 0 || Double.isNaN(max) || Double.isNaN(value)) {
			return 0.0;
		}
		double r = value / max;
		if (r < 0.0) {
			return 0.0;
		}
		if (r > 1.0) {
			return 1.0;
		}
		return r;
	}

	/**
	 * True when {@link #getWarnPct()} is &gt; 0 and {@link #ratio()} as a
	 * percent is at or below that threshold.
	 */
	public boolean isLow() {
		if (warnPct <= 0) {
			return false;
		}
		return ratio() * 100.0 <= warnPct;
	}

	/** Deep copy for safe UI/service handoff (includes live value/max). */
	public GaugeWidget copy() {
		GaugeWidget g = new GaugeWidget();
		g.id = this.id;
		g.label = this.label;
		g.shape = this.shape;
		g.source = this.source;
		g.path = this.path;
		g.maxPath = this.maxPath;
		g.colorFill = this.colorFill;
		g.colorTrack = this.colorTrack;
		g.colorWarn = this.colorWarn;
		g.warnPct = this.warnPct;
		g.opacity = this.opacity;
		g.showValue = this.showValue;
		g.showLabel = this.showLabel;
		g.visible = this.visible;
		g.x = this.x;
		g.y = this.y;
		g.w = this.w;
		g.h = this.h;
		g.landX = this.landX;
		g.landY = this.landY;
		g.landW = this.landW;
		g.landH = this.landH;
		g.tapCommand = this.tapCommand;
		g.swipeUp = this.swipeUp;
		g.swipeDown = this.swipeDown;
		g.swipeLeft = this.swipeLeft;
		g.swipeRight = this.swipeRight;
		g.holdCommand = this.holdCommand;
		g.imeMode = this.imeMode;
		g.durationSec = this.durationSec;
		g.timerName = this.timerName;
		g.liveValue = this.liveValue;
		g.liveMax = this.liveMax;
		g.remainSec = this.remainSec;
		return g;
	}

	/**
	 * Serialize one gauge for the connection setting. Does not write
	 * {@code liveValue}/{@code liveMax}/{@code remainSec}.
	 */
	public JSONObject toPersistedJson() throws JSONException {
		JSONObject o = new JSONObject();
		o.put("id", id != null ? id : "");
		o.put("label", label != null ? label : "");
		o.put("shape", getShape().toJsonValue());
		o.put("source", getSource().toJsonValue());
		o.put("path", path != null ? path : "");
		o.put("max_path", maxPath != null ? maxPath : "");
		o.put("color_fill", formatColor(colorFill));
		o.put("color_track", formatColor(colorTrack));
		o.put("color_warn", formatColor(colorWarn));
		o.put("warn_pct", warnPct);
		o.put("opacity", opacity);
		o.put("show_value", showValue);
		o.put("show_label", showLabel);
		o.put("visible", visible);
		o.put("ime_mode", getImeMode().toJsonValue());
		o.put("x", x);
		o.put("y", y);
		o.put("w", w);
		o.put("h", h);
		o.put("land_x", landX);
		o.put("land_y", landY);
		o.put("land_w", landW);
		o.put("land_h", landH);
		o.put("tap_command", tapCommand != null ? tapCommand : "");
		o.put("swipe_up", swipeUp != null ? swipeUp : "");
		o.put("swipe_down", swipeDown != null ? swipeDown : "");
		o.put("swipe_left", swipeLeft != null ? swipeLeft : "");
		o.put("swipe_right", swipeRight != null ? swipeRight : "");
		o.put("hold_command", holdCommand != null ? holdCommand : "");
		o.put("duration_sec", durationSec);
		o.put("timer_name", timerName != null ? timerName : "");
		return o;
	}

	/**
	 * Parse one gauge from JSON. Returns null if {@code id} is missing/invalid
	 * (caller should use {@link GaugeWidgetsStore} for validation).
	 * {@code live_value}/{@code liveValue}/{@code remain_sec} keys are ignored.
	 */
	public static GaugeWidget fromJson(final JSONObject o) {
		if (o == null) {
			return null;
		}
		String rawId = o.optString("id", "");
		String normalized = GaugeWidgetsStore.normalizeName(rawId);
		if (normalized == null) {
			return null;
		}
		GaugeWidget g = new GaugeWidget();
		g.setId(normalized);
		g.setLabel(o.optString("label", ""));
		g.setShape(Shape.fromJsonValue(o.optString("shape", "hbar")));
		g.setSource(Source.fromJsonValue(o.optString("source", "manual")));
		g.setPath(o.optString("path", ""));
		g.setMaxPath(o.optString("max_path", ""));
		g.setColorFill(parseColor(o.optString("color_fill", "#CC2222"), DEFAULT_COLOR_FILL));
		g.setColorTrack(parseColor(o.optString("color_track", "#333333"), DEFAULT_COLOR_TRACK));
		g.setColorWarn(parseColor(o.optString("color_warn", "#FFAA00"), DEFAULT_COLOR_WARN));
		g.setWarnPct(o.optInt("warn_pct", DEFAULT_WARN_PCT));
		g.setOpacity(o.optInt("opacity", DEFAULT_OPACITY));
		g.setShowValue(o.optBoolean("show_value", true));
		g.setShowLabel(o.optBoolean("show_label", true));
		g.setVisible(o.optBoolean("visible", true));
		g.setX(o.optInt("x", 0));
		g.setY(o.optInt("y", 0));
		int ww = o.optInt("w", 200);
		g.setW(ww < 1 ? 200 : ww);
		int hh = o.optInt("h", 24);
		g.setH(hh < 1 ? 24 : hh);
		g.setLandX(o.optInt("land_x", 0));
		g.setLandY(o.optInt("land_y", 0));
		g.setLandW(o.optInt("land_w", 0));
		g.setLandH(o.optInt("land_h", 0));
		g.setTapCommand(o.optString("tap_command", ""));
		g.setSwipeUp(o.optString("swipe_up", ""));
		g.setSwipeDown(o.optString("swipe_down", ""));
		g.setSwipeLeft(o.optString("swipe_left", ""));
		g.setSwipeRight(o.optString("swipe_right", ""));
		g.setHoldCommand(o.optString("hold_command", ""));
		g.setImeMode(ImeMode.fromJsonValue(o.optString("ime_mode", "stay")));
		g.setDurationSec(o.optDouble("duration_sec", 0.0));
		g.setTimerName(o.optString("timer_name", ""));
		if (g.getSource() == Source.TIMER) {
			if (g.getTimerName().length() == 0 && g.getPath().length() > 0) {
				g.setTimerName(g.getPath());
			} else if (g.getPath().length() == 0 && g.getTimerName().length() > 0) {
				g.setPath(g.getTimerName());
			}
		}
		g.liveValue = 0.0;
		g.liveMax = DEFAULT_LIVE_MAX;
		g.remainSec = 0.0;
		return g;
	}

	/**
	 * Parse {@code #RGB}, {@code #RRGGBB}, {@code #AARRGGBB}, or a colour name
	 * ({@code red}/{@code green}/{@code blue}/{@code yellow}/{@code orange}/
	 * {@code cyan}/{@code magenta}/{@code white}/{@code black}).
	 */
	public static int parseColor(final String raw, final int fallback) {
		if (raw == null) {
			return fallback;
		}
		String s = raw.trim();
		if (s.length() == 0) {
			return fallback;
		}
		if (s.charAt(0) == '#') {
			String h = s.substring(1);
			try {
				if (h.length() == 3) {
					int r = Integer.parseInt(h.substring(0, 1), 16) * 17;
					int g = Integer.parseInt(h.substring(1, 2), 16) * 17;
					int b = Integer.parseInt(h.substring(2, 3), 16) * 17;
					return 0xFF000000 | (r << 16) | (g << 8) | b;
				}
				if (h.length() == 6) {
					int rgb = Integer.parseInt(h, 16);
					return 0xFF000000 | rgb;
				}
				if (h.length() == 8) {
					long v = Long.parseLong(h, 16);
					return (int) v;
				}
			} catch (NumberFormatException e) {
				return fallback;
			}
			return fallback;
		}
		String n = s.toLowerCase(Locale.US);
		if ("red".equals(n)) {
			return 0xFFFF0000;
		}
		if ("green".equals(n)) {
			return 0xFF00FF00;
		}
		if ("blue".equals(n)) {
			return 0xFF0000FF;
		}
		if ("yellow".equals(n)) {
			return 0xFFFFFF00;
		}
		if ("orange".equals(n)) {
			return 0xFFFFA500;
		}
		if ("cyan".equals(n)) {
			return 0xFF00FFFF;
		}
		if ("magenta".equals(n)) {
			return 0xFFFF00FF;
		}
		if ("white".equals(n)) {
			return 0xFFFFFFFF;
		}
		if ("black".equals(n)) {
			return 0xFF000000;
		}
		return fallback;
	}

	/**
	 * {@code #RRGGBB} when opaque, otherwise {@code #AARRGGBB}.
	 */
	public static String formatColor(final int argb) {
		int a = (argb >>> 24) & 0xFF;
		int r = (argb >>> 16) & 0xFF;
		int g = (argb >>> 8) & 0xFF;
		int b = argb & 0xFF;
		if (a == 0xFF) {
			return String.format(Locale.US, "#%02X%02X%02X", r, g, b);
		}
		return String.format(Locale.US, "#%02X%02X%02X%02X", a, r, g, b);
	}
}
