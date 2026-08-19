/*
 * Copyright (C) Dan Block 2013
 */
package com.resurrection.blowtorch2.lib.service;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.util.Log;

/** Auto-reconnect and persistent-connection handling for a Connection.
 *
 * Owns the reconnect state outright: how many attempts have been spent, whether
 * auto-reconnect or persistent mode is on, and the network callback used while
 * waiting for connectivity to come back. Connection reaches this state through
 * the methods here rather than touching fields, so the retry budget and the
 * pending network wait can only be changed in one place.
 */
final class ConnectionReconnect {

	/** Reconnect delay while persistent mode is on and the last drop was clean. */
	private static final int PERSISTENT_SHORT_RECONNECT_MILLIS = 8000;

	/** Reconnect delay while persistent mode is on after a network error. */
	private static final int PERSISTENT_ERROR_RECONNECT_MILLIS = 15000;

	/** Give up waiting for a usable network after this long and retry blindly. */
	private static final int PERSISTENT_NETWORK_WAIT_CAP_MILLIS = 180000;

	/** Attempt limit when the setting carries no value. */
	private static final int DEFAULT_RECONNECT_LIMIT = 5;

	private final Connection host;

	/** Configured attempt limit; null until the setting is read. */
	private Integer limit;

	/** Attempts spent since the last successful connect. */
	private int attempt = 0;

	/** Auto-reconnect setting; null until the setting is read. */
	private Boolean autoReconnect;

	/** Persistent-connection setting. */
	private Boolean persistent = Boolean.FALSE;

	/** Registered while waiting for a usable network, null otherwise. */
	private ConnectivityManager.NetworkCallback networkCallback;

	/** Cap timer for the wait above, null when nothing is pending. */
	private Runnable networkWaitTimeout;

	ConnectionReconnect(final Connection host) {
		this.host = host;
	}

	/** Setting handler: cap on reconnect attempts. */
	void setLimit(final Integer value) {
		limit = value;
	}

	/** Setting handler: auto-reconnect on or off. */
	void setAutoReconnect(final Boolean value) {
		autoReconnect = value;
	}

	/** Setting handler: persistent connection on or off.
	 *
	 * Switching it off drops any pending network wait, otherwise a reconnect
	 * scheduled under the old setting would still fire.
	 */
	void setPersistent(final Boolean value) {
		persistent = value != null ? value : Boolean.FALSE;
		if (!isPersistent()) {
			clearNetworkWait();
		}
	}

	/** True when persistent mode is on. */
	boolean isPersistent() {
		return Boolean.TRUE.equals(persistent);
	}

	/** True when a dropped connection should be retried at all.
	 *
	 * Persistent connection only changes <em>how</em> a retry waits (network
	 * callback, longer delay, peer-close as a flap). It does not retry on its
	 * own: Auto Reconnect off means off, including overnight idle drops.
	 */
	boolean wantsReconnect() {
		return Boolean.TRUE.equals(autoReconnect);
	}

	/** Peer closed the socket. Retry only when auto-reconnect is on and
	 * persistent mode asked to treat that close as a flap. */
	boolean reconnectOnPeerClose() {
		return wantsReconnect() && isPersistent();
	}

	/** Called on a successful connect: the retry budget starts over. */
	void onConnected() {
		clearNetworkWait();
		attempt = 0;
	}

	/** Called when the user asks for a session that must not retry. */
	void disableForSession() {
		autoReconnect = Boolean.FALSE;
		attempt = 0;
	}

	/** Configured attempt limit. Persistent mode does not widen this: the
	 * Options number is what the player typed. */
	private int effectiveLimit() {
		return limit != null ? limit.intValue() : DEFAULT_RECONNECT_LIMIT;
	}

	/** Whether a retry is wanted and the budget still allows one.
	 *
	 * Separate from {@link #consumeAttempt} for the error path, which has to tear
	 * the net threads down between the check and the scheduling: tearing down
	 * clears any pending network wait, so it must not run after one is armed.
	 */
	boolean canAttempt() {
		return wantsReconnect() && attempt < effectiveLimit();
	}

	/** Spend one attempt from the budget and schedule the retry.
	 *
	 * @param normalDelayMs delay to use when persistent mode is off.
	 * @return remaining attempts after this one, or -1 when the caller should
	 *         stop retrying and let the disconnect stand.
	 */
	int consumeAttempt(final long normalDelayMs) {
		if (!wantsReconnect()) {
			return -1;
		}
		int max = effectiveLimit();
		if (attempt >= max) {
			return -1;
		}
		attempt++;
		schedule(normalDelayMs);
		return max - attempt;
	}

	/** Wording for the "attempting reconnect" notice, which differs per mode. */
	String describeNextAttempt(final String offDelayText) {
		if (isPersistent()) {
			return " (waiting for network if needed).";
		}
		return offDelayText;
	}

	/** Trailing note appended to the disconnect warning in persistent mode. */
	String persistentNote() {
		return isPersistent() ? " Persistent connection is on." : "";
	}

	/** Post the reconnect message, waiting for a network first when needed. */
	private void schedule(final long normalDelayMs) {
		if (host == null || host.mHandler == null) {
			return;
		}
		long delay = normalDelayMs;
		if (isPersistent()) {
			if (normalDelayMs <= Connection.THREE_THOUSAND_MILLIS) {
				delay = PERSISTENT_SHORT_RECONNECT_MILLIS;
			} else {
				delay = PERSISTENT_ERROR_RECONNECT_MILLIS;
			}
			if (!isNetworkUsable()) {
				waitForNetworkThenReconnect(delay);
				return;
			}
		}
		host.mHandler.sendEmptyMessageDelayed(Connection.MESSAGE_RECONNECT, delay);
	}

	/** Whether the device currently has a network that claims internet access. */
	private boolean isNetworkUsable() {
		try {
			ConnectivityManager cm = (ConnectivityManager)
					host.mService.getSystemService(Context.CONNECTIVITY_SERVICE);
			if (cm == null) {
				return true;
			}
			Network active = cm.getActiveNetwork();
			if (active == null) {
				return false;
			}
			NetworkCapabilities caps = cm.getNetworkCapabilities(active);
			return caps != null
					&& caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
		} catch (Exception e) {
			// Assume usable: a failed check should not stop a reconnect attempt.
			return true;
		}
	}

	/** Hold the reconnect until connectivity returns, or until the cap expires. */
	private void waitForNetworkThenReconnect(final long afterAvailableDelayMs) {
		if (host.mHandler == null || host.mService == null) {
			return;
		}
		clearNetworkWait();
		String message = "\n" + Colorizer.getRedColor()
				+ "No usable network right now. Will reconnect when connectivity returns…"
				+ Colorizer.getWhiteColor() + "\n";
		host.mHandler.sendMessage(
				host.mHandler.obtainMessage(Connection.MESSAGE_PROCESSORWARNING, message));
		try {
			final ConnectivityManager cm = (ConnectivityManager)
					host.mService.getSystemService(Context.CONNECTIVITY_SERVICE);
			if (cm == null) {
				host.mHandler.sendEmptyMessageDelayed(Connection.MESSAGE_RECONNECT,
						afterAvailableDelayMs);
				return;
			}
			networkCallback = new ConnectivityManager.NetworkCallback() {
				@Override
				public void onAvailable(Network network) {
					clearNetworkWait();
					if (host.mHandler != null) {
						host.mHandler.sendEmptyMessageDelayed(Connection.MESSAGE_RECONNECT,
								afterAvailableDelayMs);
					}
				}
			};
			NetworkRequest request = new NetworkRequest.Builder()
					.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
					.build();
			cm.registerNetworkCallback(request, networkCallback);
			networkWaitTimeout = new Runnable() {
				@Override
				public void run() {
					if (networkCallback != null) {
						clearNetworkWait();
						if (host.mHandler != null) {
							host.mHandler.sendEmptyMessage(Connection.MESSAGE_RECONNECT);
						}
					}
				}
			};
			host.mHandler.postDelayed(networkWaitTimeout, PERSISTENT_NETWORK_WAIT_CAP_MILLIS);
		} catch (Exception e) {
			Log.w("BlowTorch", "Persistent network wait failed; reconnecting blindly", e);
			host.mHandler.sendEmptyMessageDelayed(Connection.MESSAGE_RECONNECT,
					afterAvailableDelayMs);
		}
	}

	/** Drop any pending network wait and unregister the callback. */
	void clearNetworkWait() {
		if (host == null) {
			return;
		}
		if (host.mHandler != null && networkWaitTimeout != null) {
			host.mHandler.removeCallbacks(networkWaitTimeout);
			networkWaitTimeout = null;
		}
		if (networkCallback != null && host.mService != null) {
			try {
				ConnectivityManager cm = (ConnectivityManager)
						host.mService.getSystemService(Context.CONNECTIVITY_SERVICE);
				if (cm != null) {
					cm.unregisterNetworkCallback(networkCallback);
				}
			} catch (Exception ignored) {
			}
			networkCallback = null;
		}
	}
}
