/*
 * Copyright (C) Dan Block 2013
 */
package com.resurrection.blowtorch2.lib.service;

import java.util.HashMap;

import com.resurrection.blowtorch2.lib.service.plugin.Plugin;
import com.resurrection.blowtorch2.lib.trigger.TriggerData;

/** Trigger CRUD plus enable/disable/toggle (single, group, and all) for a Connection.
 *
 * Every mutation that changes which triggers are live ends with
 * {@link Connection#buildTriggerSystem()} so the compiled matcher stays in sync.
 */
final class ConnectionTriggers {

	private final Connection host;

	ConnectionTriggers(final Connection host) {
		this.host = host;
	}

	/** Triggers of the main connection settings. */
	HashMap<String, TriggerData> getTriggers() {
		return host.mSettings.getSettings().getTriggers();
	}

	/** Triggers of a given plugin, or null when the plugin is not loaded. */
	HashMap<String, TriggerData> getPluginTriggers(final String name) {
		Plugin p = host.mPluginMap.get(name);
		if (p != null) {
			return p.getSettings().getTriggers();
		} else {
			return null;
		}
	}

	/** Add a trigger to the main settings plugin. */
	void addTrigger(final TriggerData data) {
		host.mSettings.addTrigger(data);
	}

	/** Replace a trigger in the main settings plugin. */
	void updateTrigger(final TriggerData from, final TriggerData to) {
		host.mSettings.updateTrigger(from, to);
	}

	/** Replace a trigger in the target plugin. */
	void updatePluginTrigger(final String selectedPlugin, final TriggerData from,
			final TriggerData to) {
		Plugin p = host.mPluginMap.get(selectedPlugin);
		if (p != null) {
			p.updateTrigger(from, to);
		}
	}

	/** Add a trigger to the target plugin. */
	void newPluginTrigger(final String selectedPlugin, final TriggerData data) {
		Plugin p = host.mPluginMap.get(selectedPlugin);
		if (p != null) {
			p.addTrigger(data);
		}
	}

	/** One trigger from the target plugin, or null when plugin or trigger is missing. */
	TriggerData getPluginTrigger(final String selectedPlugin, final String pattern) {
		Plugin p = host.mPluginMap.get(selectedPlugin);
		if (p != null) {
			return p.getSettings().getTriggers().get(pattern);
		} else {
			return null;
		}
	}

	/** One trigger from the main settings plugin. */
	TriggerData getTrigger(final String pattern) {
		return host.mSettings.getSettings().getTriggers().get(pattern);
	}

	/** Enable or disable one trigger in the target plugin. */
	void setPluginTriggerEnabled(final String selectedPlugin, final boolean enabled,
			final String key) {
		Plugin p = host.mPluginMap.get(selectedPlugin);
		if (p != null) {
			TriggerData data = p.getSettings().getTriggers().get(key);
			if (data != null) {
				data.setEnabled(enabled);
				p.getSettings().setDirty(true);
				host.buildTriggerSystem();
			}
		}
	}

	/** Enable or disable one trigger in the main settings plugin. */
	void setTriggerEnabled(final boolean enabled, final String key) {
		TriggerData data = host.mSettings.getSettings().getTriggers().get(key);
		if (data != null) {
			data.setEnabled(enabled);
			host.buildTriggerSystem();
		}
	}

	/** Toggle one main-settings trigger; null when it does not exist. */
	Boolean toggleTriggerEnabled(final String key) {
		TriggerData data = host.mSettings.getSettings().getTriggers().get(key);
		if (data == null) {
			return null;
		}
		boolean next = !data.isEnabled();
		data.setEnabled(next);
		host.buildTriggerSystem();
		return Boolean.valueOf(next);
	}

	/** Set enabled state for every main-settings trigger in {@code group} (exact match). */
	int setTriggerGroupEnabled(final String group, final boolean enabled) {
		String g = group == null ? "" : group;
		int n = 0;
		for (TriggerData t : host.mSettings.getSettings().getTriggers().values()) {
			if (t.getGroup().equals(g)) {
				t.setEnabled(enabled);
				n++;
			}
		}
		if (n > 0) {
			host.buildTriggerSystem();
		}
		return n;
	}

	/** Toggle every main-settings trigger in {@code group} (exact match). */
	int toggleTriggerGroupEnabled(final String group) {
		String g = group == null ? "" : group;
		int n = 0;
		for (TriggerData t : host.mSettings.getSettings().getTriggers().values()) {
			if (t.getGroup().equals(g)) {
				t.setEnabled(!t.isEnabled());
				n++;
			}
		}
		if (n > 0) {
			host.buildTriggerSystem();
		}
		return n;
	}

	/** Enable or disable every trigger in the main settings plugin. */
	int setAllTriggersEnabled(final boolean enabled) {
		int n = 0;
		for (TriggerData t : host.mSettings.getSettings().getTriggers().values()) {
			t.setEnabled(enabled);
			n++;
		}
		host.buildTriggerSystem();
		return n;
	}

	/** Toggle one trigger in the target plugin; null when plugin or trigger is missing. */
	Boolean togglePluginTriggerEnabled(final String selectedPlugin, final String key) {
		Plugin p = host.mPluginMap.get(selectedPlugin);
		if (p == null) {
			return null;
		}
		TriggerData data = p.getSettings().getTriggers().get(key);
		if (data == null) {
			return null;
		}
		boolean next = !data.isEnabled();
		data.setEnabled(next);
		p.getSettings().setDirty(true);
		host.buildTriggerSystem();
		return Boolean.valueOf(next);
	}

	/** Set enabled state for every trigger in {@code selectedPlugin} matching {@code group}. */
	int setPluginTriggerGroupEnabled(final String selectedPlugin,
			final String group, final boolean enabled) {
		Plugin p = host.mPluginMap.get(selectedPlugin);
		if (p == null) {
			return 0;
		}
		String g = group == null ? "" : group;
		int n = 0;
		for (TriggerData t : p.getSettings().getTriggers().values()) {
			if (t.getGroup().equals(g)) {
				t.setEnabled(enabled);
				n++;
			}
		}
		if (n > 0) {
			p.getSettings().setDirty(true);
			host.buildTriggerSystem();
		}
		return n;
	}

	/** Toggle every trigger in {@code selectedPlugin} matching {@code group}. */
	int togglePluginTriggerGroupEnabled(final String selectedPlugin,
			final String group) {
		Plugin p = host.mPluginMap.get(selectedPlugin);
		if (p == null) {
			return 0;
		}
		String g = group == null ? "" : group;
		int n = 0;
		for (TriggerData t : p.getSettings().getTriggers().values()) {
			if (t.getGroup().equals(g)) {
				t.setEnabled(!t.isEnabled());
				n++;
			}
		}
		if (n > 0) {
			p.getSettings().setDirty(true);
			host.buildTriggerSystem();
		}
		return n;
	}

	/** Enable or disable every trigger in the target plugin. */
	int setAllPluginTriggersEnabled(final String selectedPlugin,
			final boolean enabled) {
		Plugin p = host.mPluginMap.get(selectedPlugin);
		if (p == null) {
			return 0;
		}
		int n = 0;
		for (TriggerData t : p.getSettings().getTriggers().values()) {
			t.setEnabled(enabled);
			n++;
		}
		if (n > 0) {
			p.getSettings().setDirty(true);
			host.buildTriggerSystem();
		}
		return n;
	}

	/** Set group state across main settings and every loaded plugin. */
	int setTriggerGroupEnabledEverywhere(final String group,
			final boolean enabled) {
		int n = setTriggerGroupEnabled(group, enabled);
		for (Plugin p : host.mPlugins) {
			if (p == null || p == host.mSettings) {
				continue;
			}
			n += setPluginTriggerGroupEnabled(p.getName(), group, enabled);
		}
		return n;
	}

	/** Toggle a group across main settings and every loaded plugin. */
	int toggleTriggerGroupEnabledEverywhere(final String group) {
		int n = toggleTriggerGroupEnabled(group);
		for (Plugin p : host.mPlugins) {
			if (p == null || p == host.mSettings) {
				continue;
			}
			n += togglePluginTriggerGroupEnabled(p.getName(), group);
		}
		return n;
	}

	/** Remove a trigger from the target plugin. */
	void deletePluginTrigger(final String selectedPlugin, final String which) {
		Plugin p = host.mPluginMap.get(selectedPlugin);
		if (p != null) {
			p.getSettings().getTriggers().remove(which);
			p.getSettings().setDirty(true);
			p.sortTriggers();
		}
		host.buildTriggerSystem();
	}

	/** Remove a trigger from the main settings plugin. */
	void deleteTrigger(final String which) {
		host.mSettings.getSettings().getTriggers().remove(which);
		host.mSettings.sortTriggers();
		host.buildTriggerSystem();
	}
}
