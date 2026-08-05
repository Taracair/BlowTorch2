/*
 * Copyright (C) Dan Block 2013
 */
package com.resurrection.blowtorch2.lib.service;

import java.util.HashMap;

import com.resurrection.blowtorch2.lib.alias.AliasData;
import com.resurrection.blowtorch2.lib.service.plugin.Plugin;

/**
 * Alias CRUD, enable/disable, and keyboard alias replacement for a Connection.
 *
 * <p>Every method that changes what an alias <em>says</em> also rebuilds the
 * trigger system, because a trigger pattern may name an alias -- the
 * {@code $alias&#123;name&#125;} form, resolved in
 * {@link Connection#buildTriggerSystem()}. Without that a trigger went on
 * matching the alias's old text until something else happened to rebuild, and
 * "I edited the alias and the trigger did not change" is the kind of bug that
 * gets blamed on the trigger.
 */
final class ConnectionAliases {

	private final Connection host;

	ConnectionAliases(final Connection host) {
		this.host = host;
	}

	/** Replace the whole alias map of the main settings plugin. */
	void setAliases(final HashMap<String, AliasData> map) {
		host.mSettings.getSettings().setAliases(map);
		host.mSettings.buildAliases();
		host.buildTriggerSystem();
	}

	/** Replace the whole alias map of a target plugin. */
	void setPluginAliases(final String plugin, final HashMap<String, AliasData> map) {
		Plugin p = host.mPluginMap.get(plugin);
		if (p != null) {
			p.getSettings().setAliases(map);
			p.getSettings().setDirty(true);
			p.buildAliases();
			host.buildTriggerSystem();
		}
	}

	/** Look up one alias in a target plugin, or null when the plugin is unknown. */
	AliasData getPluginAlias(final String plugin, final String key) {
		Plugin p = host.mPluginMap.get(plugin);
		if (p != null) {
			return p.getSettings().getAliases().get(key);
		}
		return null;
	}

	/** Look up one alias in the main settings plugin. */
	AliasData getAlias(final String key) {
		return host.mSettings.getSettings().getAliases().get(key);
	}

	/** Remove one alias from the main settings plugin. */
	void deleteAlias(final String key) {
		host.mSettings.getSettings().getAliases().remove(key);
		host.buildTriggerSystem();
	}

	/** Remove one alias from a target plugin. */
	void deletePluginAlias(final String plugin, final String key) {
		Plugin p = host.mPluginMap.get(plugin);
		if (p != null) {
			p.getSettings().getAliases().remove(key);
			host.buildTriggerSystem();
		}
	}

	/** The alias map of the main settings plugin. */
	HashMap<String, AliasData> getAliases() {
		return host.mSettings.getSettings().getAliases();
	}

	/** The alias map of a target plugin, or null when the plugin is unknown. */
	HashMap<String, AliasData> getPluginAliases(final String plugin) {
		Plugin p = host.mPluginMap.get(plugin);
		if (p != null) {
			return p.getSettings().getAliases();
		} else {
			return null;
		}
	}

	/** Enable or disable one alias in a target plugin. */
	void setPluginAliasEnabled(final String plugin, final boolean enabled, final String key) {
		Plugin p = host.mPluginMap.get(plugin);
		if (p != null) {
			AliasData data = p.getSettings().getAliases().get(key);
			if (data != null) {
				data.setEnabled(enabled);
				p.getSettings().setDirty(true);
				p.buildAliases();
			}
		}
	}

	/** Enable or disable one alias in the main settings plugin. */
	void setAliasEnabled(final boolean enabled, final String key) {
		AliasData data = host.mSettings.getSettings().getAliases().get(key);
		if (data != null) {
			data.setEnabled(enabled);
			host.mSettings.buildAliases();
		}
	}

	/** Helper for the keyboard command. Does an alias replacement in a special kind of way.
	 *
	 * Walks the enabled plugins and returns the first replacement that actually changed
	 * the bytes; falls back to the input untouched.
	 */
	byte[] doKeyboardAliasReplace(final byte[] bytes, final Boolean reprocess) {
		int count = host.mPlugins.size();
		for (int i = 0; i < count; i++) {
			Plugin p = host.mPlugins.get(i);
			if (p == null || !p.isEnabled()) {
				continue;
			}
			byte[] tmp = p.doAliasReplacement(bytes, reprocess);
			if (tmp.length != bytes.length) {
				return tmp;
			} else {
				boolean same = true;
				for (int j = 0; j < tmp.length; j++) {
					if (tmp[j] != bytes[j]) {
						same = false;
						j = tmp.length;
					}
				}
				if (!same) {
					return tmp;
				}
			}
		}

		return bytes;
	}
}
