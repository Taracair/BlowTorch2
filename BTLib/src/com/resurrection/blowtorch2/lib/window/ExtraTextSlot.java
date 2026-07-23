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
		DRAWER_BOTTOM("drawer_bottom"),
		FLOAT("float");

		private final String jsonValue;

		Mode(final String jsonValue) {
			this.jsonValue = jsonValue;
		}

		public String toJsonValue() {
			return jsonValue;
		}

		/**
		 * Parse a contract mode string; unknown / null → {@link #DRAWER_BOTTOM}.
		 */
		public static Mode fromJsonValue(final String raw) {
			if (raw == null) {
				return DRAWER_BOTTOM;
			}
			String s = raw.trim().toLowerCase(java.util.Locale.US);
			for (Mode m : values()) {
				if (m.jsonValue.equals(s)) {
					return m;
				}
			}
			// Also accept enum-style names.
			if ("drawer_top".equals(s) || "drawertop".equals(s)) {
				return DRAWER_TOP;
			}
			if ("float".equals(s) || "floating".equals(s)) {
				return FLOAT;
			}
			return DRAWER_BOTTOM;
		}
	}

	private String name = "";
	private String title = "";
	private Mode mode = Mode.DRAWER_BOTTOM;
	private int heightDp = 160;
	private int floatX = 24;
	private int floatY = 120;
	private int floatW = 320;
	private int floatH = 220;
	private boolean visible = true;
	private boolean collapsed = false;

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
		return mode != null ? mode : Mode.DRAWER_BOTTOM;
	}

	public void setMode(final Mode mode) {
		this.mode = mode != null ? mode : Mode.DRAWER_BOTTOM;
	}

	public int getHeightDp() {
		return heightDp;
	}

	public void setHeightDp(final int heightDp) {
		this.heightDp = heightDp > 0 ? heightDp : 160;
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
		s.visible = this.visible;
		s.collapsed = this.collapsed;
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
		o.put("visible", visible);
		o.put("collapsed", collapsed);
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
		s.mode = Mode.fromJsonValue(o.optString("mode", "drawer_bottom"));
		s.heightDp = o.optInt("height_dp", 160);
		if (s.heightDp <= 0) {
			s.heightDp = 160;
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
		s.visible = o.optBoolean("visible", true);
		s.collapsed = o.optBoolean("collapsed", false);
		return s;
	}
}
