/*
 * Copyright (C) BlowTorch contributors
 */
package com.resurrection.blowtorch2.lib.window;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * One player-facing extra text window slot (drawer or floating).
 * Public id {@link #name} is shared with gag/replace retarget and Lua.
 */
public final class ExtraTextSlot {

	/** Layout / presentation mode for the overlay. */
	public enum Mode {
		DRAWER_TOP("drawer_top"),
		FLOAT("float");

		private final String jsonValue;

		Mode(final String jsonValue) {
			this.jsonValue = jsonValue;
		}

		public String toJsonValue() {
			return jsonValue;
		}

		/**
		 * Parse a contract mode string. Legacy {@code drawer_bottom} maps to
		 * {@link #DRAWER_TOP}. Unknown / null → {@link #DRAWER_TOP}.
		 */
		public static Mode fromJsonValue(final String raw) {
			if (raw == null) {
				return DRAWER_TOP;
			}
			String s = raw.trim().toLowerCase(java.util.Locale.US);
			if ("float".equals(s) || "floating".equals(s)) {
				return FLOAT;
			}
			// drawer_bottom retired — treat as top drawer
			if ("drawer_bottom".equals(s) || "drawerbottom".equals(s)
					|| "drawer_top".equals(s) || "drawertop".equals(s)) {
				return DRAWER_TOP;
			}
			for (Mode m : values()) {
				if (m.jsonValue.equals(s)) {
					return m;
				}
			}
			return DRAWER_TOP;
		}
	}

	private String name = "";
	private String title = "";
	private Mode mode = Mode.DRAWER_TOP;
	private int heightDp = 160;
	private int floatX = 24;
	private int floatY = 120;
	private int floatW = 320;
	private int floatH = 220;
	/** Overlay opacity percent 40–100 (same range as mapper float). */
	private int opacity = 85;
	private boolean visible = true;
	private boolean collapsed = false;
	/**
	 * GMCP module names/patterns routed into this slot (case-insensitive).
	 * Exact match, or prefix with trailing {@code .} / {@code .*}/ {@code *}
	 * (e.g. {@code Char.Vitals}, {@code Char.}, {@code Comm.*}).
	 */
	private final java.util.ArrayList<String> gmcpModules = new java.util.ArrayList<String>();

	public ExtraTextSlot() {
	}

	public ExtraTextSlot(final String name) {
		this.name = name != null ? name : "";
		this.title = this.name;
	}

	public String getName() {
		return name;
	}

	public void setName(final String name) {
		this.name = name != null ? name : "";
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(final String title) {
		this.title = title != null ? title : "";
	}

	public Mode getMode() {
		return mode != null ? mode : Mode.DRAWER_TOP;
	}

	public void setMode(final Mode mode) {
		this.mode = mode != null ? mode : Mode.DRAWER_TOP;
	}

	public int getHeightDp() {
		return heightDp;
	}

	public void setHeightDp(final int heightDp) {
		// Keep enough height for the bottom grab strip (~28dp) + a few lines of text.
		this.heightDp = heightDp < 50 ? 50 : heightDp;
	}

	public int getFloatX() {
		return floatX;
	}

	public void setFloatX(final int floatX) {
		this.floatX = floatX;
	}

	public int getFloatY() {
		return floatY;
	}

	public void setFloatY(final int floatY) {
		this.floatY = floatY;
	}

	public int getFloatW() {
		return floatW;
	}

	public void setFloatW(final int floatW) {
		this.floatW = floatW > 0 ? floatW : 320;
	}

	public int getFloatH() {
		return floatH;
	}

	public void setFloatH(final int floatH) {
		this.floatH = floatH > 0 ? floatH : 220;
	}

	public int getOpacity() {
		return opacity;
	}

	/** Clamp to 40–100 (readable minimum, like mapper overlay). */
	public void setOpacity(final int opacity) {
		if (opacity < 40) {
			this.opacity = 40;
		} else if (opacity > 100) {
			this.opacity = 100;
		} else {
			this.opacity = opacity;
		}
	}

	public boolean isVisible() {
		return visible;
	}

	public void setVisible(final boolean visible) {
		this.visible = visible;
	}

	public boolean isCollapsed() {
		return collapsed;
	}

	public void setCollapsed(final boolean collapsed) {
		this.collapsed = collapsed;
	}

	/** Never null; may be empty. */
	public java.util.ArrayList<String> getGmcpModules() {
		return gmcpModules;
	}

	public void setGmcpModules(final java.util.List<String> modules) {
		gmcpModules.clear();
		if (modules == null) {
			return;
		}
		for (int i = 0; i < modules.size(); i++) {
			String m = normalizeGmcpPattern(modules.get(i));
			if (m != null && !gmcpModules.contains(m)) {
				gmcpModules.add(m);
			}
		}
	}

	/** Parse comma/space separated patterns from the Options editor. */
	public void setGmcpModulesCsv(final String csv) {
		gmcpModules.clear();
		if (csv == null || csv.trim().length() == 0) {
			return;
		}
		String[] parts = csv.split("[,;\\s]+");
		for (int i = 0; i < parts.length; i++) {
			String m = normalizeGmcpPattern(parts[i]);
			if (m != null && !gmcpModules.contains(m)) {
				gmcpModules.add(m);
			}
		}
	}

	public String getGmcpModulesCsv() {
		if (gmcpModules.isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < gmcpModules.size(); i++) {
			if (i > 0) {
				sb.append(", ");
			}
			sb.append(gmcpModules.get(i));
		}
		return sb.toString();
	}

	/**
	 * True if this slot should receive {@code module} (e.g. {@code Char.Vitals}).
	 */
	public boolean matchesGmcpModule(final String module) {
		if (module == null || module.length() == 0 || gmcpModules.isEmpty()) {
			return false;
		}
		String mod = module.trim();
		for (int i = 0; i < gmcpModules.size(); i++) {
			if (patternMatchesModule(gmcpModules.get(i), mod)) {
				return true;
			}
		}
		return false;
	}

	static String normalizeGmcpPattern(final String raw) {
		if (raw == null) {
			return null;
		}
		String s = raw.trim();
		if (s.length() == 0) {
			return null;
		}
		return s;
	}

	/** Match GMCP module name against a route pattern (exact / prefix / wildcard). */
	public static boolean patternMatchesModule(final String pattern, final String module) {
		if (pattern == null || module == null) {
			return false;
		}
		String p = pattern.trim();
		String m = module.trim();
		if (p.length() == 0 || m.length() == 0) {
			return false;
		}
		// Trailing .* or * → prefix
		if (p.endsWith(".*")) {
			String pref = p.substring(0, p.length() - 2);
			return m.regionMatches(true, 0, pref, 0, pref.length());
		}
		if (p.endsWith("*") && !p.endsWith(".*")) {
			String pref = p.substring(0, p.length() - 1);
			return m.regionMatches(true, 0, pref, 0, pref.length());
		}
		// Trailing . → family prefix (Char. matches Char.Vitals)
		if (p.endsWith(".")) {
			return m.regionMatches(true, 0, p, 0, p.length());
		}
		return m.equalsIgnoreCase(p);
	}

	/** Deep copy for safe UI/service handoff. */
	public ExtraTextSlot copy() {
		ExtraTextSlot s = new ExtraTextSlot();
		s.name = this.name;
		s.title = this.title;
		s.mode = this.mode;
		s.heightDp = this.heightDp;
		s.floatX = this.floatX;
		s.floatY = this.floatY;
		s.floatW = this.floatW;
		s.floatH = this.floatH;
		s.opacity = this.opacity;
		s.visible = this.visible;
		s.collapsed = this.collapsed;
		s.gmcpModules.clear();
		s.gmcpModules.addAll(this.gmcpModules);
		return s;
	}

	/** Serialize one slot to a JSON object (contract field names). */
	public JSONObject toJson() throws JSONException {
		JSONObject o = new JSONObject();
		o.put("name", name != null ? name : "");
		o.put("title", title != null ? title : "");
		o.put("mode", getMode().toJsonValue());
		o.put("height_dp", heightDp);
		o.put("float_x", floatX);
		o.put("float_y", floatY);
		o.put("float_w", floatW);
		o.put("float_h", floatH);
		o.put("opacity", opacity);
		o.put("visible", visible);
		o.put("collapsed", collapsed);
		if (!gmcpModules.isEmpty()) {
			org.json.JSONArray arr = new org.json.JSONArray();
			for (int i = 0; i < gmcpModules.size(); i++) {
				arr.put(gmcpModules.get(i));
			}
			o.put("gmcp", arr);
		}
		return o;
	}

	/**
	 * Parse one slot from JSON. Returns null if {@code name} is missing/invalid
	 * (caller should use {@link ExtraTextSlotsStore} for validation).
	 */
	public static ExtraTextSlot fromJson(final JSONObject o) {
		if (o == null) {
			return null;
		}
		String rawName = o.optString("name", "");
		String normalized = ExtraTextSlotsStore.normalizeName(rawName);
		if (normalized == null) {
			return null;
		}
		ExtraTextSlot s = new ExtraTextSlot();
		s.name = normalized;
		String t = o.optString("title", "");
		s.title = (t != null && t.length() > 0) ? t : normalized;
		s.mode = Mode.fromJsonValue(o.optString("mode", "drawer_top"));
		s.heightDp = o.optInt("height_dp", 160);
		if (s.heightDp < 50) {
			s.heightDp = 50;
		}
		s.floatX = o.optInt("float_x", 24);
		s.floatY = o.optInt("float_y", 120);
		s.floatW = o.optInt("float_w", 320);
		if (s.floatW <= 0) {
			s.floatW = 320;
		}
		s.floatH = o.optInt("float_h", 220);
		if (s.floatH <= 0) {
			s.floatH = 220;
		}
		int op = o.optInt("opacity", 85);
		if (op < 40) {
			op = 40;
		} else if (op > 100) {
			op = 100;
		}
		s.opacity = op;
		s.visible = o.optBoolean("visible", true);
		s.collapsed = o.optBoolean("collapsed", false);
		s.gmcpModules.clear();
		org.json.JSONArray gmcp = o.optJSONArray("gmcp");
		if (gmcp != null) {
			for (int i = 0; i < gmcp.length(); i++) {
				String m = normalizeGmcpPattern(gmcp.optString(i, ""));
				if (m != null && !s.gmcpModules.contains(m)) {
					s.gmcpModules.add(m);
				}
			}
		} else {
			// Also accept a single string / CSV for hand-edited JSON.
			String csv = o.optString("gmcp", "");
			if (csv != null && csv.length() > 0 && !csv.startsWith("[")) {
				s.setGmcpModulesCsv(csv);
			}
		}
		return s;
	}
}
