package com.resurrection.blowtorch2.lib.util;

import android.os.RemoteException;

import com.resurrection.blowtorch2.lib.service.IConnectionBinder;

/**
 * Queue {@code saveSettings} off the UI thread. Measured 276–285 ms on that
 * thread (30 July 2026). Safe because edits already went through
 * {@code Connection.updateSetting}; callers that need the file on disk before
 * continuing still call {@link IConnectionBinder#saveSettings()} directly.
 */
public final class SettingsSaver {

	private SettingsSaver() {
	}

	/**
	 * A save is queued and has not begun yet, so another request would write the
	 * same state twice.
	 *
	 * <p>Cleared when the write <em>starts</em>, not when it finishes: an edit
	 * made while a write is in flight is not covered by that write and has to
	 * earn its own.
	 */
	private static final java.util.concurrent.atomic.AtomicBoolean QUEUED =
			new java.util.concurrent.atomic.AtomicBoolean(false);

	private static java.util.concurrent.ExecutorService sExecutor;

	private static synchronized java.util.concurrent.Executor executor() {
		if (sExecutor == null) {
			sExecutor = java.util.concurrent.Executors.newSingleThreadExecutor(
					new java.util.concurrent.ThreadFactory() {
						@Override
						public Thread newThread(Runnable r) {
							Thread t = new Thread(r, "bt-settings-save");
							t.setDaemon(true);
							return t;
						}
					});
		}
		return sExecutor;
	}

	/**
	 * Persist off the UI thread. A second request before the first starts is
	 * dropped. StrictMode put the write on the UI process main
	 * ({@code Stub.onTransact}) — in-process binder, not waiting on {@code :stellar}.
	 */
	public static void saveInBackground(final IConnectionBinder service) {
		if (service == null) {
			return;
		}
		if (!QUEUED.compareAndSet(false, true)) {
			return;
		}
		executor().execute(new Runnable() {
			@Override
			public void run() {
				QUEUED.set(false);
				try {
					service.saveSettings();
				} catch (RemoteException e) {
					// Worth the file: a settings save that vanished is the
					// failure this log exists for.
					BlowTorchLogger.logThrowable("SettingsSaver", e);
				} catch (RuntimeException e) {
					BlowTorchLogger.logThrowable("SettingsSaver", e);
				}
			}
		});
	}
}
