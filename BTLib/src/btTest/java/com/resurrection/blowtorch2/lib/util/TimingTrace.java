package com.resurrection.blowtorch2.lib.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import android.app.Application;
import android.content.Context;
import android.os.SystemClock;

import com.resurrection.blowtorch2.lib.BuildConfig;
import com.resurrection.blowtorch2.lib.settings.ConfigurationLoader;

/**
 * Test-build timing for return-from-background, {@code .switch}, and a
 * launcher open. This source set is the btTest flavor only. Production,
 * which F-Droid builds, compiles an empty class. Lines are queued; disk is
 * a daemon, same reason as the GMCP log (a blocking append on the caller
 * was 60–80 ms).
 *
 * <p>Each process writes its own file ({@code timing-ui.log},
 * {@code timing-svc.log}) next to {@code blowtorch2.log}. The two clocks
 * match: {@code up} is {@link SystemClock#uptimeMillis()}.
 */
public final class TimingTrace {

	public static final String EXTRA_LAUNCH_UP = "bt.timing.launchUp";
	public static final String EXTRA_LAUNCH_SRC = "bt.timing.launchSrc";
	public static final String LOG_TAG = "BlowTorchTiming";

	private static final int QUEUE_LIMIT = 1024;
	private static final int BATCH_MAX = 64;
	private static final long MAX_BYTES = 2L * 1024L * 1024L;
	private static final String MAIN_WINDOW = "mainDisplay";

	private static final AtomicInteger SEQ = new AtomicInteger();
	private static final BlockingQueue<String> QUEUE =
			new LinkedBlockingQueue<String>(QUEUE_LIMIT);
	private static final ThreadLocal<SimpleDateFormat> STAMP =
			new ThreadLocal<SimpleDateFormat>() {
				@Override
				protected SimpleDateFormat initialValue() {
					return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
				}
			};

	/** 0 unknown, 1 production (off), 2 test build (on). */
	private static volatile int state;
	private static volatile boolean enabled;
	private static volatile boolean wantsPaint;
	private static volatile boolean stopped;
	private static volatile boolean queueWasFull;
	private static volatile long leaveUp;
	private static volatile long launchUp;
	private static volatile long resumeEnterUp;
	/** True from onPause until the next onResume. paint() does not clear it. */
	private static volatile boolean away;
	private static volatile TimingWatch watch;
	private static volatile Context appContext;
	private static volatile String proc = "ui";
	private static Thread writer;

	private TimingTrace() {
	}

	public static boolean on() {
		return BuildConfig.TIMING_TRACE && enabled;
	}

	/** Call from each process once it has a Context. Idempotent. */
	public static void arm(final Context context) {
		if (context == null || state != 0) {
			return;
		}
		synchronized (TimingTrace.class) {
			if (state != 0) {
				return;
			}
			String pkg = context.getPackageName();
			if (!BuildConfig.TIMING_TRACE || pkg == null || !pkg.endsWith(".test")) {
				state = 1;
				return;
			}
			boolean test;
			try {
				test = ConfigurationLoader.isTestMode(context);
			} catch (RuntimeException e) {
				return;
			}
			if (!test) {
				state = 1;
				return;
			}
			Context app = context.getApplicationContext();
			appContext = app != null ? app : context;
			proc = processTag();
			state = 2;
			enabled = true;
			startWriter();
		}
	}

	public static void event(final String kind, final String fields) {
		if (!enabled) {
			return;
		}
		long up = SystemClock.uptimeMillis();
		int seq = SEQ.incrementAndGet();
		String wall = STAMP.get().format(new Date());
		String line = TimingLine.format(wall, up, seq, proc, kind, fields);
		if (!QUEUE.offer(line)) {
			if (!queueWasFull) {
				queueWasFull = true;
				android.util.Log.w(LOG_TAG, "timing queue full; dropping lines");
			}
		} else {
			queueWasFull = false;
		}
	}

	public static void markLeave() {
		if (!enabled) {
			return;
		}
		leaveUp = SystemClock.uptimeMillis();
		away = true;
		stopped = false;
		resumeEnterUp = 0L;
		watch = null;
		wantsPaint = false;
		event("resume", "phase=leave");
	}

	public static void noteStopped() {
		if (!enabled) {
			return;
		}
		stopped = true;
		// onStop, not onPause: a dialog pauses without leaving, and the tap
		// still belongs to the open. Home clears it so the return does not
		// start at that tap.
		launchUp = 0L;
		event("resume", "phase=stop sinceLeaveMs=" + sinceLeave(SystemClock.uptimeMillis()));
	}

	public static int stoppedFlag() {
		return stopped ? 1 : 0;
	}

	public static long sinceLeave(final long now) {
		long left = leaveUp;
		if (left <= 0L) {
			return -1L;
		}
		return now - left;
	}

	public static void noteResumeEnter(final long up) {
		if (!enabled || up <= 0L) {
			return;
		}
		resumeEnterUp = up;
		away = false;
	}

	public static void noteLaunch(final long up, final String src) {
		if (!enabled || up <= 0L) {
			return;
		}
		launchUp = up;
		event("launch", "phase=noted src=" + TimingLine.token(src) + " tapUp=" + up);
	}

	/**
	 * Arm the next main-window frame. UI thread only. Call this after the
	 * rebuild that frame should show, not when the work is merely queued:
	 * a frame callback already on the looper would otherwise close the wait
	 * on the old world and wipe the tap.
	 *
	 * <p>A {@code switch} reason passes that switch's post time as
	 * {@code fromUp}. Each switch message carries its own time.
	 */
	public static void expectPaint(final String reason, final long fromUp) {
		if (!enabled) {
			return;
		}
		// A rebuild that finishes while paused must not arm. The next return
		// would otherwise keep that start for the whole time away.
		// resumeEnterUp is cleared by the frame, so it is not this signal.
		if (away) {
			event("paint", "phase=skip reason=paused arm=" + TimingLine.token(reason));
			return;
		}
		long now = SystemClock.uptimeMillis();
		long switchUp = "switch".equals(reason) ? fromUp : 0L;
		watch = TimingWatch.arm(watch, launchUp, switchUp, resumeEnterUp,
				reason, fromUp, now);
		wantsPaint = true;
	}

	/**
	 * Uptime at which this frame should be timed, or 0 when it should not.
	 * One volatile read per frame. The clock is read only when a wait is open.
	 */
	public static long paintStart(final String windowName) {
		if (!BuildConfig.TIMING_TRACE || !wantsPaint || !MAIN_WINDOW.equals(windowName)) {
			return 0L;
		}
		return SystemClock.uptimeMillis();
	}

	public static void paint(final long drawStart, final int hw, final int dbg,
			final int tiles, final int bake, final int typeset) {
		TimingWatch w = watch;
		watch = null;
		wantsPaint = false;
		launchUp = 0L;
		resumeEnterUp = 0L;
		if (!enabled || w == null || drawStart <= 0L) {
			return;
		}
		long now = SystemClock.uptimeMillis();
		long leave = leaveUp;
		long sinceLeave = leave <= 0L ? -1L : drawStart - leave;
		event("paint", "reason=" + w.reason
				+ " gapMs=" + (drawStart - w.fromUp)
				+ " drawMs=" + (now - drawStart)
				+ " armedMs=" + (drawStart - w.armedUp)
				+ " sinceLeaveMs=" + sinceLeave
				+ " hw=" + hw
				+ " dbg=" + dbg
				+ " tiles=" + tiles
				+ " bake=" + bake
				+ " typeset=" + typeset);
	}

	private static String processTag() {
		String name = Application.getProcessName();
		if (name != null && name.indexOf(":stellar") >= 0) {
			return "svc";
		}
		return "ui";
	}

	private static void startWriter() {
		if (writer != null) {
			return;
		}
		writer = new Thread(new Runnable() {
			@Override
			public void run() {
				writerLoop();
			}
		}, "timing-log");
		writer.setDaemon(true);
		writer.setPriority(Thread.MIN_PRIORITY);
		writer.start();
	}

	private static void writerLoop() {
		Context context = appContext;
		File file = null;
		if (context != null) {
			File dir = BlowTorchLogger.getLogDirectory(context);
			file = new File(dir, "svc".equals(proc) ? "timing-svc.log" : "timing-ui.log");
			android.util.Log.i(LOG_TAG, file.getAbsolutePath());
			long up = SystemClock.uptimeMillis();
			String wall = STAMP.get().format(new Date());
			writeBatch(file, java.util.Collections.singletonList(
					TimingLine.format(wall, up, 0, proc, "file",
							"path=" + TimingLine.token(file.getAbsolutePath()))));
		}
		ArrayList<String> batch = new ArrayList<String>();
		while (true) {
			try {
				batch.clear();
				batch.add(QUEUE.take());
				QUEUE.drainTo(batch, BATCH_MAX - 1);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
			if (file == null && appContext != null) {
				File dir = BlowTorchLogger.getLogDirectory(appContext);
				file = new File(dir, "svc".equals(proc) ? "timing-svc.log" : "timing-ui.log");
			}
			if (file != null) {
				writeBatch(file, batch);
			}
		}
	}

	private static void writeBatch(final File file, final java.util.List<String> lines) {
		if (file == null || lines == null || lines.isEmpty()) {
			return;
		}
		File parent = file.getParentFile();
		if (parent != null && !parent.isDirectory()) {
			parent.mkdirs();
		}
		if (file.length() > MAX_BYTES) {
			File backup = new File(parent, file.getName() + ".bak");
			if (backup.exists()) {
				backup.delete();
			}
			file.renameTo(backup);
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < lines.size(); i++) {
			sb.append(lines.get(i)).append('\n');
		}
		FileOutputStream out = null;
		try {
			out = new FileOutputStream(file, true);
			out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			android.util.Log.w(LOG_TAG, "could not write the timing log", e);
		} finally {
			if (out != null) {
				try {
					out.close();
				} catch (IOException e) {
					android.util.Log.w(LOG_TAG, "could not close the timing log", e);
				}
			}
		}
	}
}
