package com.resurrection.blowtorch2.lib;

import android.app.Application;

import com.resurrection.blowtorch2.lib.util.BlowTorchLogger;

/**
 * Application object, present for one reason: it is the only thing the framework
 * creates in <em>every</em> process before anything else runs.
 *
 * <p>BlowTorch runs the UI in one process and the connection service in {@code
 * :stellar}, and the failures worth recording happen in both. Handing the logger an
 * application context from here means an error path anywhere can report itself without
 * having to find a context of its own — which was the practical reason most of them
 * settled for printStackTrace and vanished into the logcat ring buffer.
 */
public class BlowTorchApp extends Application {

	@Override
	public void onCreate() {
		super.onCreate();
		BlowTorchLogger.attach(this);
		recordUncaughtExceptions();
		enableStrictModeOnTestBuilds();
	}

	/**
	 * Write a crash to the error log before the process dies.
	 *
	 * <p>Until now nothing did. The only handler in the tree was a commented-out
	 * line in MainWindow naming a CrashReporter class that does not exist, so an
	 * uncaught exception took the app down leaving nothing behind but a logcat
	 * entry that the ring buffer eventually ate. The maintainer's error log
	 * looked quiet because crashes never reached it, not because there were none.
	 *
	 * <p>This records and then hands over to the handler that was already
	 * installed, so the app still dies exactly as it did. Swallowing the throw
	 * would leave the process alive in a state nobody designed for, which is a
	 * worse failure than the crash: a gate belongs where the bad value enters,
	 * not around the whole application.
	 *
	 * <p>Installed per process, since {@code :stellar} crashes independently of
	 * the UI and its failures are the ones nobody is looking at.
	 */
	private void recordUncaughtExceptions() {
		final Thread.UncaughtExceptionHandler previous =
				Thread.getDefaultUncaughtExceptionHandler();
		Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
			@Override
			public void uncaughtException(final Thread thread, final Throwable error) {
				try {
					BlowTorchLogger.logThrowable(
							"CRASH in " + getPackageName() + " on thread " + thread.getName(),
							error);
				} catch (Throwable loggingFailed) {
					// The process is going down either way; the original throwable
					// matters more than this one, so make sure it still gets out.
					android.util.Log.e("BlowTorch", "could not record crash", loggingFailed);
				}
				if (previous != null) {
					previous.uncaughtException(thread, error);
				}
			}
		});
	}

	/**
	 * Turn on StrictMode, on the test build only.
	 *
	 * <p>Every UI-thread problem in this project so far has been found the same
	 * slow way: someone notices a stutter, describes it, and we go looking with
	 * probes. StrictMode reports disk and network on the UI thread as it
	 * happens, with the stack that did it — the evidence arrives before the
	 * complaint.
	 *
	 * <p>Log, never crash. A penalty dialog or death would make the test build
	 * unusable for actually playing, and it is the build that gets played.
	 * Production is untouched: this is a development instrument, and it costs
	 * real time on every disk read.
	 */
	private void enableStrictModeOnTestBuilds() {
		if (!getPackageName().endsWith(".test")) {
			return;
		}
		try {
			android.os.StrictMode.setThreadPolicy(
					new android.os.StrictMode.ThreadPolicy.Builder()
							.detectDiskReads()
							.detectDiskWrites()
							.detectNetwork()
							.detectCustomSlowCalls()
							.penaltyLog()
							.build());
			android.os.StrictMode.setVmPolicy(
					new android.os.StrictMode.VmPolicy.Builder()
							.detectLeakedClosableObjects()
							.detectLeakedSqlLiteObjects()
							.penaltyLog()
							.build());
			android.util.Log.i("BlowTorch", "StrictMode on (test build): "
					+ "watch logcat for StrictMode policy violation");
		} catch (Exception e) {
			BlowTorchLogger.logMinor("BlowTorchApp.strictMode", e);
		}
	}
}
