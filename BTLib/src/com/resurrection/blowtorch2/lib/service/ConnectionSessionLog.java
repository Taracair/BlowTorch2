/*
 * Copyright (C) Dan Block 2013
 */
package com.resurrection.blowtorch2.lib.service;

import com.resurrection.blowtorch2.lib.util.SessionLogger;

/** Session file-log option wrappers for a Connection. */
final class ConnectionSessionLog {

	private final Connection host;

	ConnectionSessionLog(final Connection host) {
		this.host = host;
	}

	boolean isSessionLogEnabled() {
		try {
			Object opt = host.mSettings.getSettings().getOptions().findOptionByKey("session_log");
			if (opt instanceof com.resurrection.blowtorch2.lib.service.plugin.settings.BooleanOption) {
				Object val = ((com.resurrection.blowtorch2.lib.service.plugin.settings.BooleanOption) opt).getValue();
				return val instanceof Boolean && (Boolean) val;
			}
		} catch (Exception ignored) {
		}
		return SessionLogger.isEnabled(host.mService.getApplicationContext());
	}

	void applySessionLogDirectory() {
		try {
			Object opt = host.mSettings.getSettings().getOptions().findOptionByKey("session_log_directory");
			if (opt instanceof com.resurrection.blowtorch2.lib.service.plugin.settings.StringOption) {
				Object val = ((com.resurrection.blowtorch2.lib.service.plugin.settings.StringOption) opt).getValue();
				if (val instanceof String) {
					SessionLogger.setCustomDirectory(host.mService.getApplicationContext(), (String) val);
				}
			}
		} catch (Exception ignored) {
		}
	}

	void doSetSessionLog(final boolean enabled) {
		SessionLogger.setEnabled(host.mService.getApplicationContext(), enabled);
		if (enabled) {
			applySessionLogDirectory();
			SessionLogger.startSession(host.mService.getApplicationContext(), host.mDisplay);
			SessionLogger.appendMarker(host.mService.getApplicationContext(), host.mDisplay,
					"logging enabled → " + SessionLogger.getLogLocationLabel(
							host.mService.getApplicationContext()));
		}
	}

	void doSetSessionLogDirectory(final String path) {
		SessionLogger.setCustomDirectory(host.mService.getApplicationContext(),
				path != null ? path : "");
		if (SessionLogger.isEnabled(host.mService.getApplicationContext())) {
			SessionLogger.startSession(host.mService.getApplicationContext(), host.mDisplay);
		}
	}

	/** Apply session-log options when a connection becomes connected. */
	void onConnected() {
		SessionLogger.setEnabled(host.mService.getApplicationContext(), isSessionLogEnabled());
		applySessionLogDirectory();
		if (SessionLogger.isEnabled(host.mService.getApplicationContext())) {
			// Same profile: keep appending to the open file (reconnect). New file only
			// when there is no active session log for this profile.
			boolean continuing = SessionLogger.hasActiveSessionFor(host.mDisplay);
			SessionLogger.continueOrStartSession(host.mService.getApplicationContext(),
					host.mDisplay);
			String path = SessionLogger.getLogLocationLabel(host.mService.getApplicationContext());
			SessionLogger.appendMarker(host.mService.getApplicationContext(), host.mDisplay,
					(continuing ? "reconnected → " : "connected → ") + path);
		}
	}

	/** Flush buffered bytes to disk when the TCP session ends. */
	void onDisconnected() {
		SessionLogger.endSession(host.mService.getApplicationContext());
	}
}
