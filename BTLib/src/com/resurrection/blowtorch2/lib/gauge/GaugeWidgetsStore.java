/*
 * Copyright (C) BlowTorch contributors
 */
package com.resurrection.blowtorch2.lib.gauge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Parse / serialize the connection setting {@code gauge_widgets} JSON array.
 * Invalid JSON or null → empty list. Enforces max widgets and id rules.
 * Live value/max are never written.
 */
public final class GaugeWidgetsStore {

	public static final int MAX = 12;
	public static final String SETTING_KEY = "gauge_widgets";
	public static final String ENABLED_KEY = "gauge_widgets_enabled";

	private static final Pattern NAME_PATTERN = Pattern.compile("^[a-z0-9_]{1,24}$");
	private static final Set<String> RESERVED_NAMES;

	static {
		HashSet<String> reserved = new HashSet<String>();
		reserved.add("maindisplay");
		reserved.add("button_window");
		reserved.add("main");
		RESERVED_NAMES = Collections.unmodifiableSet(reserved);
	}

	private GaugeWidgetsStore() {
	}

	/**
	 * Normalize a candidate gauge id: trim, lowercase, require {@code [a-z0-9_]+}
	 * length 1..24, not reserved.
	 *
	 * @return normalized id, or null if invalid / reserved
	 */
	public static String normalizeName(final String raw) {
		if (raw == null) {
			return null;
		}
		String n = raw.trim().toLowerCase(Locale.US);
		if (n.length() < 1 || n.length() > 24) {
			return null;
		}
		if (!NAME_PATTERN.matcher(n).matches()) {
			return null;
		}
		if (RESERVED_NAMES.contains(n)) {
			return null;
		}
		// "mainDisplay" lowercases to "maindisplay" — also block exact reserved tokens.
		if ("maindisplay".equals(n) || "button_window".equals(n) || "main".equals(n)) {
			return null;
		}
		return n;
	}

	/** True if {@code name} is reserved (case-insensitive). */
	public static boolean isReservedName(final String raw) {
		if (raw == null) {
			return false;
		}
		String n = raw.trim().toLowerCase(Locale.US);
		return RESERVED_NAMES.contains(n) || "maindisplay".equals(n);
	}

	/**
	 * Parse a JSON array string into gauges. Null / blank / invalid → empty list.
	 * Skips bad entries; stops accepting new ids after {@link #MAX}.
	 */
	public static ArrayList<GaugeWidget> parse(final String json) {
		ArrayList<GaugeWidget> out = new ArrayList<GaugeWidget>();
		if (json == null) {
			return out;
		}
		String trimmed = json.trim();
		if (trimmed.length() == 0 || "null".equalsIgnoreCase(trimmed)) {
			return out;
		}
		try {
			JSONArray arr = new JSONArray(trimmed);
			HashSet<String> seen = new HashSet<String>();
			for (int i = 0; i < arr.length(); i++) {
				if (out.size() >= MAX) {
					break;
				}
				JSONObject o = arr.optJSONObject(i);
				if (o == null) {
					continue;
				}
				GaugeWidget g = GaugeWidget.fromJson(o);
				if (g == null || g.getId() == null || g.getId().length() == 0) {
					continue;
				}
				if (seen.contains(g.getId())) {
					continue;
				}
				seen.add(g.getId());
				out.add(g);
			}
		} catch (JSONException e) {
			return new ArrayList<GaugeWidget>();
		} catch (Exception e) {
			return new ArrayList<GaugeWidget>();
		}
		return out;
	}

	/** Serialize gauges to a compact JSON array string (never null). Live values omitted. */
	public static String toJson(final List<GaugeWidget> gauges) {
		JSONArray arr = new JSONArray();
		if (gauges != null) {
			int count = 0;
			HashSet<String> seen = new HashSet<String>();
			for (GaugeWidget g : gauges) {
				if (g == null || count >= MAX) {
					continue;
				}
				String n = normalizeName(g.getId());
				if (n == null || seen.contains(n)) {
					continue;
				}
				seen.add(n);
				try {
					GaugeWidget copy = g.copy();
					copy.setId(n);
					arr.put(copy.toPersistedJson());
					count++;
				} catch (JSONException e) {
					com.resurrection.blowtorch2.lib.util.BlowTorchLogger.logThrowable(
							"GaugeWidgetsStore.serialize widget", e);
				}
			}
		}
		return arr.toString();
	}

	/**
	 * Validate and clamp a mutable list in place (ids, max {@link #MAX}, drop
	 * reserved/dupes).
	 *
	 * @return the same list instance
	 */
	public static ArrayList<GaugeWidget> validate(final ArrayList<GaugeWidget> gauges) {
		if (gauges == null) {
			return new ArrayList<GaugeWidget>();
		}
		ArrayList<GaugeWidget> cleaned = new ArrayList<GaugeWidget>();
		HashSet<String> seen = new HashSet<String>();
		for (GaugeWidget g : gauges) {
			if (g == null || cleaned.size() >= MAX) {
				continue;
			}
			String n = normalizeName(g.getId());
			if (n == null || seen.contains(n)) {
				continue;
			}
			seen.add(n);
			g.setId(n);
			cleaned.add(g);
		}
		gauges.clear();
		gauges.addAll(cleaned);
		return gauges;
	}

	/** First gauge whose normalized id matches, or null. */
	public static GaugeWidget find(final List<GaugeWidget> list, final String id) {
		String n = normalizeName(id);
		if (n == null || list == null) {
			return null;
		}
		for (int i = 0; i < list.size(); i++) {
			GaugeWidget g = list.get(i);
			if (g == null) {
				continue;
			}
			String gid = normalizeName(g.getId());
			if (n.equals(gid) || n.equals(g.getId())) {
				return g;
			}
		}
		return null;
	}
}
