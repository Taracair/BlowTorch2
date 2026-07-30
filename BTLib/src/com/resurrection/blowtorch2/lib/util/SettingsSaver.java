package com.resurrection.blowtorch2.lib.util;

import android.os.RemoteException;

import com.resurrection.blowtorch2.lib.service.IConnectionBinder;

/**
 * Ask the service to persist settings without making the caller wait.
 *
 * <p><b>Measured, 30 July 2026.</b> Closing the Options dialog called
 * {@code service.saveSettings()} straight from the UI thread. That is a
 * synchronous binder transaction, and on the far side it writes the settings
 * file and then exports a copy to shared storage, both with an fsync since
 * {@code 957a8822} made the save crash-safe. StrictMode measured the round trip
 * at <b>276-285 ms</b>, landing 1-3 ms before {@code Choreographer: Skipped 33
 * frames} and {@code Skipped 35 frames} -- which is the stutter on the options
 * dialog's own dismiss animation, reported from the device.
 *
 * <p>The fsync is not the thing to remove: settings silently reverting to
 * factory defaults is a bug this app has already shipped once. What is wrong is
 * the UI thread waiting for it.
 *
 * <p><b>Why this is safe, which is not obvious:</b> the dialog does not hold the
 * edits. Every change is pushed to the service as it is made, through
 * {@code Connection.updateSetting}; {@code saveSettings} only asks the service
 * to write down state it already has. So moving the call off the UI thread
 * cannot lose an edit that was still sitting in a widget -- there are none.
 *
 * <p>Binder ordering is unchanged: the transaction is still synchronous, still
 * one at a time, just not on the thread drawing frames. Callers that genuinely
 * need the file on disk before they continue must keep calling
 * {@link IConnectionBinder#saveSettings()} directly.
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
	 * Persist settings on a background thread.
	 *
	 * <p>Saves are serialized, and a second request arriving before the first
	 * has started is dropped. That window is small -- the Options dialog only
	 * saves when the last screen closes, so it does not benefit -- but it is
	 * what keeps a caller that fires repeatedly from queueing a write per call.
	 *
	 * <p><b>Where this runs, which is not what the package layout suggests:</b>
	 * StrictMode put the settings write on 23944/23944, the UI process's own
	 * main thread, with {@code Stub.onTransact} in the stack. The transaction is
	 * serviced in-process, so the disk was really happening on the UI thread
	 * rather than the UI thread merely waiting on {@code :stellar}. Moving the
	 * call therefore moves the work, and it does not land on the service's main
	 * thread, which is the one carrying game text.
	 *
	 * @param service The connection binder; null is ignored.
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
