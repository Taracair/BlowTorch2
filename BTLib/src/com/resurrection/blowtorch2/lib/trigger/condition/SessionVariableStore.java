package com.resurrection.blowtorch2.lib.trigger.condition;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-connection session string variables for trigger conditions / SetVariable.
 * Not persisted across reconnects.
 */
public class SessionVariableStore {

	private final HashMap<String, String> values = new HashMap<String, String>();

	public synchronized void set(String key, String value) {
		if (key == null || key.length() == 0) {
			return;
		}
		values.put(key, value != null ? value : "");
	}

	public synchronized void unset(String key) {
		if (key == null) {
			return;
		}
		values.remove(key);
	}

	public synchronized boolean exists(String key) {
		return key != null && values.containsKey(key);
	}

	/** @return value or {@code null} if unset */
	public synchronized String get(String key) {
		if (key == null) {
			return null;
		}
		return values.get(key);
	}

	public synchronized void clear() {
		values.clear();
	}

	/** Snapshot for debugging; not used by evaluation. */
	public synchronized Map<String, String> snapshot() {
		return new HashMap<String, String>(values);
	}
}
