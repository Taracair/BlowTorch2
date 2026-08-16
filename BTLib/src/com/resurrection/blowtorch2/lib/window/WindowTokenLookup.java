package com.resurrection.blowtorch2.lib.window;

import android.os.RemoteException;

import com.resurrection.blowtorch2.lib.service.WindowToken;

/**
 * Find a {@link WindowToken} by name without repeating a binder fetch when the
 * UI already has that token.
 *
 * <p>{@code IConnectionBinder.getWindowTokens()} parcels every window's
 * {@code TextTree} buffer. A 344 KB reply unparceled on the UI thread during
 * pause was a measured ANR (16 Aug 2026, Keep-in-background). Extra-text overlay
 * bind used to call that again just to pick one token by name, even after
 * {@code initiailizeWindows} had already stored the array in {@code mWindows}.
 */
public final class WindowTokenLookup {

	private WindowTokenLookup() {
	}

	/** Binder fetch used when the name is not in the local array. */
	public interface RemoteTokens {
		WindowToken[] fetch() throws RemoteException;
	}

	public static boolean hasTokens(final WindowToken[] windows) {
		return windows != null && windows.length > 0;
	}

	/**
	 * Scan an already-unparceled list. Does not touch the binder.
	 *
	 * @param windows tokens already in memory, or null
	 * @param name window name, or null
	 * @return the matching token, or null
	 */
	public static WindowToken findIn(final WindowToken[] windows, final String name) {
		if (name == null || windows == null) {
			return null;
		}
		for (int i = 0; i < windows.length; i++) {
			WindowToken t = windows[i];
			if (t != null && name.equals(t.getName())) {
				return t;
			}
		}
		return null;
	}

	/**
	 * Look in {@code local} first. Only hit {@code remote} when the name is
	 * not there.
	 *
	 * <p>Old behaviour always called {@code getWindowTokens()} even when
	 * {@code local} already held the token. That is the fetch this method
	 * skips. A miss still fetches: a slot created after the last window
	 * rebuild is not in {@code mWindows} yet.
	 *
	 * @param local tokens already in the activity, or null
	 * @param name window name, or null
	 * @param remote binder fetch; ignored on a local hit, unused when null
	 * @return the matching token, or null
	 * @throws RemoteException if the fallback fetch throws
	 */
	public static WindowToken find(final WindowToken[] local, final String name,
			final RemoteTokens remote) throws RemoteException {
		WindowToken hit = findIn(local, name);
		if (hit != null || name == null) {
			return hit;
		}
		if (remote == null) {
			return null;
		}
		return findIn(remote.fetch(), name);
	}
}
