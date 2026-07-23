/*
 * Copyright (C) BlowTorch contributors
 */
package com.resurrection.blowtorch2.lib.window;

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
 * Parse / serialize the connection setting {@code extra_text_windows} JSON array.
 * Invalid JSON or null → empty list. Enforces max slots and name rules.
 */
public final class ExtraTextSlotsStore {

	public static final int MAX_SLOTS = 8;
	public static final String SETTING_KEY = "extra_text_windows";
	public static final String ENABLED_KEY = "extra_text_windows_enabled";
	/** When true, drawer overlays shrink mainDisplay so game text is not covered (buttons unchanged). */
	public static final String PUSH_MAIN_KEY = "extra_text_push_main";

	private static final Pattern NAME_PATTERN = Pattern.compile("^[a-z0-9_]{1,24}$");
	private static final Set<String> RESERVED_NAMES;

	static {
		HashSet<String> reserved = new HashSet<String>();
		reserved.add("maindisplay");
		reserved.add("button_window");
		reserved.add("main");
		RESERVED_NAMES = Collections.unmodifiableSet(reserved);
	}

	private ExtraTextSlotsStore() {
	}

	/**
	 * Normalize a candidate slot name: trim, lowercase, require {@code [a-z0-9_]+}
	 * length 1..24, not reserved.
	 *
	 * @return normalized name, or null if invalid / reserved
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
	 * Parse a JSON array string into slots. Null / blank / invalid → empty list.
	 * Skips bad entries; stops accepting new names after {@link #MAX_SLOTS}.
	 */
	public static ArrayList<ExtraTextSlot> parse(final String json) {
		ArrayList<ExtraTextSlot> out = new ArrayList<ExtraTextSlot>();
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
				if (out.size() >= MAX_SLOTS) {
					break;
				}
				JSONObject o = arr.optJSONObject(i);
				if (o == null) {
					continue;
				}
				ExtraTextSlot slot = ExtraTextSlot.fromJson(o);
				if (slot == null || slot.getName() == null || slot.getName().length() == 0) {
					continue;
				}
				if (seen.contains(slot.getName())) {
					continue;
				}
				seen.add(slot.getName());
				out.add(slot);
			}
		} catch (JSONException e) {
			return new ArrayList<ExtraTextSlot>();
		} catch (Exception e) {
			return new ArrayList<ExtraTextSlot>();
		}
		return out;
	}

	/** Serialize slots to a compact JSON array string (never null). */
	public static String toJson(final List<ExtraTextSlot> slots) {
		JSONArray arr = new JSONArray();
		if (slots != null) {
			int count = 0;
			HashSet<String> seen = new HashSet<String>();
			for (ExtraTextSlot slot : slots) {
				if (slot == null || count >= MAX_SLOTS) {
					continue;
				}
				String n = normalizeName(slot.getName());
				if (n == null || seen.contains(n)) {
					continue;
				}
				seen.add(n);
				try {
					ExtraTextSlot copy = slot.copy();
					copy.setName(n);
					arr.put(copy.toJson());
					count++;
				} catch (JSONException e) {
					// skip bad slot
				}
			}
		}
		return arr.toString();
	}

	/**
	 * Validate and clamp a mutable list in place (names, max 8, drop reserved/dupes).
	 *
	 * @return the same list instance
	 */
	public static ArrayList<ExtraTextSlot> validate(final ArrayList<ExtraTextSlot> slots) {
		if (slots == null) {
			return new ArrayList<ExtraTextSlot>();
		}
		ArrayList<ExtraTextSlot> cleaned = new ArrayList<ExtraTextSlot>();
		HashSet<String> seen = new HashSet<String>();
		for (ExtraTextSlot slot : slots) {
			if (slot == null || cleaned.size() >= MAX_SLOTS) {
				continue;
			}
			String n = normalizeName(slot.getName());
			if (n == null || seen.contains(n)) {
				continue;
			}
			seen.add(n);
			slot.setName(n);
			if (slot.getTitle() == null || slot.getTitle().length() == 0) {
				slot.setTitle(n);
			}
			cleaned.add(slot);
		}
		slots.clear();
		slots.addAll(cleaned);
		return slots;
	}
}
