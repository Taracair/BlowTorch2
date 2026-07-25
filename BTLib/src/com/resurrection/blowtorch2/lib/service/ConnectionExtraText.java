/*
 * Copyright (C) Dan Block 2013
 */
package com.resurrection.blowtorch2.lib.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import android.util.Log;

import com.resurrection.blowtorch2.lib.service.plugin.settings.BooleanOption;
import com.resurrection.blowtorch2.lib.service.plugin.settings.StringOption;
import com.resurrection.blowtorch2.lib.window.ExtraTextSlot;
import com.resurrection.blowtorch2.lib.window.ExtraTextSlotsStore;

/** Extra text window slots for a Connection: the configured list, the WindowTokens
 * backing them, and the GMCP routes they claim.
 *
 * Owns the slot list. Everything that reads it takes a copy, and everything that
 * changes it goes through here, so the JSON setting, the window tokens and the
 * Processor's GMCP routes cannot drift apart.
 */
final class ConnectionExtraText {

	private final Connection host;

	/** Configured slots, as parsed from the {@code extra_text_windows} setting. */
	private ArrayList<ExtraTextSlot> slots = new ArrayList<ExtraTextSlot>();

	ConnectionExtraText(final Connection host) {
		this.host = host;
	}

	/** Snapshot of configured slots (never null; may be empty). */
	ArrayList<ExtraTextSlot> getSlots() {
		if (slots == null) {
			slots = new ArrayList<ExtraTextSlot>();
		}
		ArrayList<ExtraTextSlot> copy = new ArrayList<ExtraTextSlot>(slots.size());
		for (ExtraTextSlot s : slots) {
			if (s != null) {
				copy.add(s.copy());
			}
		}
		return copy;
	}

	/** True when there is nothing configured, so routing can be skipped entirely. */
	boolean isEmpty() {
		return slots == null || slots.isEmpty();
	}

	/** Direct view of the slot list for the GMCP routing hot path.
	 *
	 * Deliberately not a copy: routeGmcpToExtraWindows runs per received GMCP
	 * message and only reads. Callers must not modify what they get back.
	 */
	List<ExtraTextSlot> peekSlots() {
		return slots;
	}

	/** Whether extra text overlays are enabled (default true). */
	boolean isEnabled() {
		if (host.mSettings == null || host.mSettings.getSettings() == null
				|| host.mSettings.getSettings().getOptions() == null) {
			return true;
		}
		try {
			Object o = host.mSettings.getSettings().getOptions()
					.findOptionByKey(ExtraTextSlotsStore.ENABLED_KEY);
			if (o instanceof BooleanOption) {
				Object val = ((BooleanOption) o).getValue();
				if (val instanceof Boolean) {
					return ((Boolean) val).booleanValue();
				}
			}
		} catch (Exception ignored) {
		}
		return true;
	}

	/**
	 * Ensure each configured slot has a {@link WindowToken} in the host's window
	 * list (buffer + default window options; no LayoutGroup). Reloads slots from
	 * the {@code extra_text_windows} setting first. Notifies UI when
	 * {@code notify} is true.
	 */
	void ensureSlots(final boolean notify) {
		HashSet<String> previousNames = new HashSet<String>();
		if (slots != null) {
			for (ExtraTextSlot s : slots) {
				if (s != null && s.getName() != null) {
					previousNames.add(s.getName());
				}
			}
		}
		reloadFromSettings();
		if (host.mWindows == null) {
			host.mWindows = new ArrayList<WindowToken>();
		}
		HashSet<String> nextNames = new HashSet<String>();
		for (ExtraTextSlot slot : slots) {
			if (slot == null || slot.getName() == null) {
				continue;
			}
			String name = slot.getName();
			nextNames.add(name);
			WindowToken existing = host.getWindowByName(name);
			if (existing == null) {
				WindowToken tok = new WindowToken(name, null, null, host.mDisplay);
				// Must stay false — bufferText holds bytes without painting (Window.addBytesImpl).
				tok.setBufferText(false);
				if (tok.getSettings() != null) {
					tok.getSettings().setOption("word_wrap", "true");
				}
				host.mWindows.add(tok);
			} else {
				existing.setBufferText(false);
				if (existing.getSettings() != null) {
					existing.getSettings().setOption("word_wrap", "true");
				}
			}
		}
		// Remove tokens for slots that disappeared from the JSON list.
		for (String old : previousNames) {
			if (old != null && !nextNames.contains(old) && host.mWindows != null) {
				for (int i = host.mWindows.size() - 1; i >= 0; i--) {
					WindowToken w = host.mWindows.get(i);
					if (w != null && old.equals(w.getName())) {
						host.mWindows.remove(i);
					}
				}
			}
		}
		if (notify) {
			host.requestExtraTextUi();
		}
	}

	/** Re-read the slot list from settings and refresh the GMCP routes. */
	private void reloadFromSettings() {
		String json = "[]";
		if (host.mSettings != null && host.mSettings.getSettings() != null
				&& host.mSettings.getSettings().getOptions() != null) {
			try {
				Object o = host.mSettings.getSettings().getOptions()
						.findOptionByKey(ExtraTextSlotsStore.SETTING_KEY);
				if (o instanceof StringOption) {
					Object val = ((StringOption) o).getValue();
					if (val != null) {
						json = val.toString();
					}
				}
			} catch (Exception e) {
				Log.w("BlowTorch", "reloadExtraTextSlotsFromSettings failed", e);
			}
		}
		slots = ExtraTextSlotsStore.parse(json);
		syncGmcpRoutes();
	}

	/** Tell Processor which GMCP modules are claimed by extra-text panes
	 * (so they are suppressed from the main feed). */
	void syncGmcpRoutes() {
		if (host.mProcessor == null) {
			return;
		}
		ArrayList<String> patterns = new ArrayList<String>();
		if (slots != null) {
			for (int i = 0; i < slots.size(); i++) {
				ExtraTextSlot s = slots.get(i);
				if (s == null || s.getGmcpModules() == null) {
					continue;
				}
				for (int j = 0; j < s.getGmcpModules().size(); j++) {
					String p = s.getGmcpModules().get(j);
					if (p != null && p.trim().length() > 0 && !patterns.contains(p)) {
						patterns.add(p);
					}
				}
			}
		}
		host.mProcessor.setGmcpExtraRoutePatterns(patterns);
	}

	/** Write the slot list back to the setting and ask for a save. */
	private void persist() {
		if (host.mSettings == null || host.mSettings.getSettings() == null
				|| host.mSettings.getSettings().getOptions() == null) {
			return;
		}
		ExtraTextSlotsStore.validate(slots);
		String json = ExtraTextSlotsStore.toJson(slots);
		host.mSettings.getSettings().getOptions().setOption(ExtraTextSlotsStore.SETTING_KEY, json);
		host.requestSettingsSave();
	}

	/** Find a slot by name (normalized). Returns a copy, or null. */
	ExtraTextSlot find(final String name) {
		String n = ExtraTextSlotsStore.normalizeName(name);
		if (n == null) {
			if (name != null) {
				String lower = name.trim().toLowerCase(Locale.US);
				for (ExtraTextSlot s : slots) {
					if (s != null && lower.equals(s.getName())) {
						return s.copy();
					}
				}
			}
			return null;
		}
		for (ExtraTextSlot s : slots) {
			if (s != null && n.equals(s.getName())) {
				return s.copy();
			}
		}
		return null;
	}

	/**
	 * Insert or update a slot by name. Validates name / max count; persists JSON;
	 * ensures WindowToken; notifies UI.
	 *
	 * @return true if accepted
	 */
	boolean upsert(final ExtraTextSlot slot) {
		if (slot == null) {
			return false;
		}
		String n = ExtraTextSlotsStore.normalizeName(slot.getName());
		if (n == null) {
			return false;
		}
		slot.setName(n);
		if (slot.getTitle() == null || slot.getTitle().length() == 0) {
			slot.setTitle(n);
		}
		int existing = -1;
		for (int i = 0; i < slots.size(); i++) {
			ExtraTextSlot s = slots.get(i);
			if (s != null && n.equals(s.getName())) {
				existing = i;
				break;
			}
		}
		if (existing >= 0) {
			slots.set(existing, slot.copy());
		} else {
			if (slots.size() >= ExtraTextSlotsStore.MAX_SLOTS) {
				return false;
			}
			slots.add(slot.copy());
		}
		persist();
		ensureSlots(true);
		return true;
	}

	/**
	 * Remove a slot by name. Persists, drops matching WindowToken if present,
	 * notifies UI.
	 *
	 * @return true if a slot was removed
	 */
	boolean remove(final String name) {
		String n = ExtraTextSlotsStore.normalizeName(name);
		if (n == null && name != null) {
			n = name.trim().toLowerCase(Locale.US);
		}
		if (n == null || n.length() == 0) {
			return false;
		}
		boolean removed = false;
		for (int i = slots.size() - 1; i >= 0; i--) {
			ExtraTextSlot s = slots.get(i);
			if (s != null && n.equals(s.getName())) {
				slots.remove(i);
				removed = true;
			}
		}
		if (!removed) {
			return false;
		}
		persist();
		if (host.mWindows != null) {
			for (int i = host.mWindows.size() - 1; i >= 0; i--) {
				WindowToken w = host.mWindows.get(i);
				if (w != null && n.equals(w.getName())) {
					host.mWindows.remove(i);
				}
			}
		}
		// Keep in-memory list in sync without re-adding the removed token.
		host.requestExtraTextUi();
		return true;
	}

	/**
	 * Replace the slot list, write the JSON setting, ensure tokens, optionally
	 * save. Used by overlay geometry persist and Options / Lua.
	 */
	void replaceAll(final List<ExtraTextSlot> incoming, final boolean save) {
		ArrayList<ExtraTextSlot> next = new ArrayList<ExtraTextSlot>();
		if (incoming != null) {
			for (int i = 0; i < incoming.size(); i++) {
				ExtraTextSlot s = incoming.get(i);
				if (s != null) {
					next.add(s.copy());
				}
			}
		}
		ExtraTextSlotsStore.validate(next);
		slots = next;
		String json = ExtraTextSlotsStore.toJson(slots);
		if (host.mSettings != null && host.mSettings.getSettings() != null
				&& host.mSettings.getSettings().getOptions() != null) {
			host.mSettings.getSettings().getOptions().setOption(
					ExtraTextSlotsStore.SETTING_KEY, json);
		}
		ensureSlots(true);
		if (save) {
			host.requestSettingsSave();
		}
	}
}
