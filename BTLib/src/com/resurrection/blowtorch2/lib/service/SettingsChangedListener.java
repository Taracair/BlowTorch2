/*
 * Copyright (C) Dan Block 2013
 */
package com.resurrection.blowtorch2.lib.service;

/** Quick little interface to give the settings system a callback to call when a setting has changed. */
public interface SettingsChangedListener {
	/** The method to call when a setting has changed.
	 * 
	 * @param key The key of the setting that changed.
	 * @param value The new value of the setting.
	 */
	void updateSetting(String key, String value);
}
