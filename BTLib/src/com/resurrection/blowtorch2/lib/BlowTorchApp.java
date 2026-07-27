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
	}
}
