package com.resurrection.blowtorch2.lib.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.content.Context;

import com.resurrection.blowtorch2.lib.service.Colorizer;
import com.resurrection.blowtorch2.lib.ui.SDCardUtils;

public final class BlowTorchLogger {

	private static final String LOG_FILE = "blowtorch2.log";
	private static final String LOG_BACKUP = "blowtorch2.log.bak";
	private static final String GMCP_LOG_FILE = "gmcp.log";
	private static final String GMCP_LOG_BACKUP = "gmcp.log.bak";
	private static final long MAX_BYTES = 2 * 1024 * 1024;

	private BlowTorchLogger() {
	}

	/**
	 * Where errors get written.
	 *
	 * The shared /BlowTorch tree is preferred, but it belongs to whichever flavour
	 * created it: the test build cannot write into a directory the production build
	 * owns. resolveBlowTorchSubdir does not report that, so the writes below were
	 * failing with an IOException nobody saw and the log simply stopped growing.
	 * Fall back to the app's own files directory, which is always writable.
	 */
	public static File getLogDirectory(Context context) {
		File shared = SDCardUtils.resolveBlowTorchSubdir(context, SDCardUtils.SUBDIR_LOGS);
		if (shared != null && shared.isDirectory() && shared.canWrite()) {
			return shared;
		}
		File fallback = new File(context.getFilesDir(), "logs");
		if (!fallback.isDirectory()) {
			fallback.mkdirs();
		}
		return fallback;
	}

	public static File getLogFile(Context context) {
		return new File(getLogDirectory(context), LOG_FILE);
	}

	public static void ensureLogFile(Context context) {
		File dir = getLogDirectory(context);
		File logFile = new File(dir, LOG_FILE);
		if (!logFile.exists()) {
			try {
				logFile.createNewFile();
			} catch (IOException e) {
				// Straight to logcat, never through logThrowable: the file is what just
				// failed, and routing this back through here would recurse.
				android.util.Log.e(TAG, "Could not create the error log", e);
			}
			return;
		}
		if (logFile.length() > MAX_BYTES) {
			rotateLog(dir, logFile);
		}
	}

	private static void rotateLog(File dir, File logFile) {
		File backup = new File(dir, LOG_BACKUP);
		if (backup.exists()) {
			backup.delete();
		}
		// Both processes write this file, so the other one may have rotated it a
		// moment ago. renameTo and createNewFile both just report false in that case;
		// losing the race is fine, and throwing a fit about it would be worse than the
		// few lines at stake.
		logFile.renameTo(backup);
		try {
			logFile.createNewFile();
		} catch (IOException e) {
			android.util.Log.w(TAG, "Could not reopen the log after rotating", e);
		}
	}

	/** Application context, stashed so error paths do not each have to find one.
	 *
	 * <p>Set from {@link com.resurrection.blowtorch2.lib.BlowTorchApp}, which the
	 * framework instantiates once per process — so this is populated in the UI process
	 * and in :stellar alike. Null only before that runs, and then we still reach
	 * logcat. Never a plain Activity: this outlives every one of them. */
	private static volatile Context sAppContext = null;

	/** How long the same failure is kept out of the file after it has been recorded. */
	private static final long REPEAT_QUIET_MS = 10000;
	private static final java.util.HashMap<String, long[]> RECENT = new java.util.HashMap<String, long[]>();

	static final String TAG = "BlowTorch";

	/** Remember the application context for later error reporting.
	 *
	 * @param context Any context; its application context is what gets kept.
	 */
	public static void attach(final Context context) {
		if (context != null && sAppContext == null) {
			sAppContext = context.getApplicationContext();
		}
	}

	/** Record a failure that means something is broken: file log and logcat.
	 *
	 * <p>For the paths where silence has actually cost us — binder calls, file IO,
	 * settings load and save, window and service lifecycle. Not for a failed parse of
	 * something a player typed; a log where routine and serious look alike is the
	 * problem this is meant to solve, arrived at from the other side.
	 *
	 * @param source Where it happened, for the log line.
	 * @param t The failure.
	 */
	public static void logThrowable(final String source, final Throwable t) {
		if (t == null) {
			return;
		}
		String detail = t.getClass().getSimpleName()
				+ (t.getMessage() != null ? (": " + t.getMessage()) : "");
		if (!shouldRecord(source, t)) {
			// Still visible while it is happening, just not filling the file.
			android.util.Log.w(TAG, "[" + source + "] (repeat) " + detail);
			return;
		}
		Context ctx = sAppContext;
		if (ctx == null) {
			android.util.Log.e(TAG, "[" + source + "] " + detail, t);
			return;
		}
		writeLine(ctx, source, stripColors(detail).trim());
		// The trace goes to logcat only. It is the useful half when someone is looking
		// live, and the half that would turn the file into a wall of frames.
		android.util.Log.e(TAG, "[" + source + "] " + detail, t);
	}

	/** Note a failure that is expected in normal use: logcat only, never the file.
	 *
	 * @param source Where it happened.
	 * @param t The failure.
	 */
	public static void logMinor(final String source, final Throwable t) {
		if (t == null) {
			return;
		}
		android.util.Log.w(TAG, "[" + source + "] " + t.getClass().getSimpleName()
				+ (t.getMessage() != null ? (": " + t.getMessage()) : ""));
	}

	/** False when this same failure was written recently.
	 *
	 * <p>A failure inside a draw or a read loop repeats at whatever rate that loop
	 * runs. Without this the file rotates in minutes and takes the history worth
	 * keeping with it.
	 */
	private static synchronized boolean shouldRecord(final String source, final Throwable t) {
		String key = source + "|" + t.getClass().getName() + "|" + t.getMessage();
		long now = System.currentTimeMillis();
		long[] seen = RECENT.get(key);
		if (seen != null && (now - seen[0]) < REPEAT_QUIET_MS) {
			seen[1]++;
			return false;
		}
		if (RECENT.size() > 200) {
			RECENT.clear();
		}
		RECENT.put(key, new long[] { now, 1 });
		return true;
	}

	public static synchronized void logError(Context context, String source, String message) {
		if (context == null || message == null) {
			return;
		}
		String plain = stripColors(message).trim();
		writeLine(context, source, plain);
		// Mirror to logcat regardless. A player can screenshot the game window, but a
		// screenshot is not something anyone can grep.
		android.util.Log.e(TAG, "[" + source + "] " + plain);
	}

	/** Where "Log GMCP?" writes. Separate file, see {@link #logGmcpTrace}. */
	public static File getGmcpLogFile(Context context) {
		return new File(getLogDirectory(context), GMCP_LOG_FILE);
	}

	/**
	 * Lines waiting to reach {@code gmcp.log}.
	 *
	 * <p>Bounded on purpose. A world that floods GMCP faster than a phone's
	 * external storage can absorb it must cost a dropped trace line, never
	 * unbounded memory in the service.
	 */
	private static final int GMCP_QUEUE_LIMIT = 4096;

	/** Lines folded into one file open. A connect burst is about ten. */
	private static final int GMCP_BATCH_MAX = 256;

	private static final java.util.concurrent.BlockingQueue<String> GMCP_QUEUE =
			new java.util.concurrent.LinkedBlockingQueue<String>(GMCP_QUEUE_LIMIT);

	private static Thread gmcpWriter;
	private static Context gmcpWriterContext;
	private static boolean gmcpQueueWasFull;

	/**
	 * Append one GMCP packet to its own trace file, off whatever thread called.
	 *
	 * <p>Deliberately not the crash log. A busy world sends GMCP constantly, and
	 * putting that in {@code blowtorch2.log} rolled the error history away under
	 * it — the reason the trace was pulled out of there in the first place. The
	 * player still gets a file they can read and send, just not that one.
	 *
	 * <p><b>Measured, 30 July 2026.</b> This used to open, append and close the
	 * file on the calling thread, once per packet. The caller is
	 * {@code Processor.logGmcp}, which runs on the {@code :stellar} main thread —
	 * the same handler that carries text to the UI. StrictMode on the test build
	 * recorded <b>60–80 ms of blocking disk per packet</b> (pid 9054, tid 9054),
	 * 94 violations through this method in one eden-test session, and the connect
	 * burst was visibly frozen. Turning "Log GMCP?" on was therefore a way to make
	 * the client stutter, which is not what a diagnostic should cost.
	 *
	 * <p>So the caller now only stamps and enqueues. One low-priority daemon
	 * drains the queue and folds whatever is waiting into a single file open,
	 * which turns a connect burst of ten packets into one. The stamp is taken
	 * here, at the moment the packet arrived, not when the disk got round to it —
	 * a trace with rearranged times would be worse than no trace.
	 *
	 * @param context Application context; a null context is ignored.
	 * @param line Already-redacted packet text.
	 */
	public static void logGmcpTrace(final Context context, final String line) {
		if (context == null || line == null) {
			return;
		}
		String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
		startGmcpWriter(context.getApplicationContext());
		if (!GMCP_QUEUE.offer(stamp + " " + line)) {
			// Say it once. A queue that is full is already the busiest moment
			// there is, and a warning per dropped line would make it worse.
			if (!gmcpQueueWasFull) {
				gmcpQueueWasFull = true;
				android.util.Log.w(TAG, "GMCP log queue full; dropping trace lines");
			}
		} else {
			gmcpQueueWasFull = false;
		}
	}

	private static synchronized void startGmcpWriter(final Context app) {
		if (gmcpWriter != null) {
			return;
		}
		gmcpWriterContext = app;
		gmcpWriter = new Thread(new Runnable() {
			@Override
			public void run() {
				gmcpWriterLoop();
			}
		}, "gmcp-log");
		gmcpWriter.setDaemon(true);
		gmcpWriter.setPriority(Thread.MIN_PRIORITY);
		gmcpWriter.start();
	}

	private static void gmcpWriterLoop() {
		java.util.ArrayList<String> batch = new java.util.ArrayList<String>();
		while (true) {
			try {
				batch.clear();
				batch.add(GMCP_QUEUE.take());
				GMCP_QUEUE.drainTo(batch, GMCP_BATCH_MAX - 1);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
			writeGmcpBatch(batch);
		}
	}

	/** One rotation check and one file open for however many lines are waiting. */
	private static void writeGmcpBatch(final java.util.List<String> lines) {
		Context context = gmcpWriterContext;
		if (context == null || lines.isEmpty()) {
			return;
		}
		File dir = getLogDirectory(context);
		File file = new File(dir, GMCP_LOG_FILE);
		if (file.length() > MAX_BYTES) {
			File backup = new File(dir, GMCP_LOG_BACKUP);
			if (backup.exists()) {
				backup.delete();
			}
			file.renameTo(backup);
		}
		StringBuilder sb = new StringBuilder();
		for (String line : lines) {
			sb.append(line).append("\n");
		}
		FileOutputStream out = null;
		try {
			out = new FileOutputStream(file, true);
			out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			android.util.Log.w(TAG, "Could not write the GMCP log", e);
		} finally {
			if (out != null) {
				try {
					out.close();
				} catch (IOException e) {
					android.util.Log.w(TAG, "Could not close the GMCP log", e);
				}
			}
		}
	}

	/** Append one line to the on-device log. Callers decide what reaches logcat. */
	private static synchronized void writeLine(Context context, String source, String plain) {
		ensureLogFile(context);
		File logFile = getLogFile(context);
		if (logFile.length() > MAX_BYTES) {
			rotateLog(logFile.getParentFile(), logFile);
		}
		String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
		String line = stamp + " [" + source + "] " + plain + "\n";
		FileOutputStream out = null;
		try {
			out = new FileOutputStream(logFile, true);
			out.write(line.getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			android.util.Log.e(TAG, "Could not write error log: " + line.trim(), e);
		} finally {
			if (out != null) {
				try {
					out.close();
				} catch (IOException e) {
					android.util.Log.w(TAG, "Could not close the error log", e);
				}
			}
		}
	}

	public static String stripColors(String message) {
		if (message == null) {
			return "";
		}
		String plain = message;
		plain = plain.replace(Colorizer.getRedColor(), "");
		plain = plain.replace(Colorizer.getWhiteColor(), "");
		plain = plain.replace("\n", " ");
		return plain;
	}

	/** Map common Lua/plugin errors to short player-facing text. */
	public static String humanizeError(String message) {
		if (message == null || message.trim().isEmpty()) {
			return "Unknown error.";
		}
		String plain = stripColors(message);
		String lower = plain.toLowerCase(Locale.US);
		if (lower.contains("module 'marshal' not found")
				|| lower.contains("module \"marshal\" not found")
				|| (lower.contains("marshal") && lower.contains("not found"))) {
			return "A plugin needs the marshal library, which is missing or not installed yet.\n"
					+ "Try: reconnect once (libraries sync on start), or reinstall the test APK.\n\n"
					+ "Technical detail:\n" + plain;
		}
		if (lower.contains("module '") && lower.contains("not found")) {
			return "A Lua module required by a plugin could not be loaded.\n"
					+ "Usually this means the plugin expects a library that is not packaged or not synced yet.\n\n"
					+ "Technical detail:\n" + plain;
		}
		if (lower.contains("illegal group reference")
				|| (lower.contains("illegalargumentexception")
						&& lower.contains("group"))) {
			return "A trigger/alias Ack or replace string contained a \"$\" that Java treated as a regex group reference.\n"
					+ "BlowTorch now quotes those automatically; if you still see this, simplify the Ack text.\n\n"
					+ "Technical detail:\n" + plain;
		}
		if (lower.contains("attempt to call") && lower.contains("nil")) {
			return "A plugin script called a missing function (nil).\n"
					+ "The plugin may be outdated or misconfigured for this profile.\n\n"
					+ "Technical detail:\n" + plain;
		}
		if (lower.contains("plugin") && (lower.contains("failed") || lower.contains("error") || lower.contains("parse"))) {
			return "Plugin problem while loading or saving settings.\n"
					+ "Check Plugins list for the broken entry, or restore a backup.\n\n"
					+ "Technical detail:\n" + plain;
		}
		return plain;
	}

	/** Read the last {@code maxBytes} of the error log (UTF-8, best-effort). */
	public static String readLogTail(Context context, int maxBytes) {
		ensureLogFile(context);
		File logFile = getLogFile(context);
		if (!logFile.exists() || logFile.length() == 0) {
			return "(Log is empty.)";
		}
		int limit = Math.max(1024, maxBytes);
		RandomAccessFile raf = null;
		try {
			raf = new RandomAccessFile(logFile, "r");
			long length = raf.length();
			long start = Math.max(0, length - limit);
			raf.seek(start);
			byte[] buf = new byte[(int) (length - start)];
			raf.readFully(buf);
			String text = new String(buf, StandardCharsets.UTF_8);
			if (start > 0) {
				int nl = text.indexOf('\n');
				if (nl >= 0 && nl + 1 < text.length()) {
					text = text.substring(nl + 1);
				}
				return "… (earlier lines truncated) …\n" + text;
			}
			return text;
		} catch (IOException e) {
			return "Could not read log: " + e.getMessage();
		} finally {
			if (raf != null) {
				try {
					raf.close();
				} catch (IOException ignored) {
				}
			}
		}
	}

	public static String readEntireLog(Context context) {
		ensureLogFile(context);
		File logFile = getLogFile(context);
		if (!logFile.exists() || logFile.length() == 0) {
			return "(Log is empty.)";
		}
		FileInputStream in = null;
		try {
			in = new FileInputStream(logFile);
			byte[] buf = new byte[(int) Math.min(logFile.length(), MAX_BYTES)];
			int n = in.read(buf);
			if (n <= 0) {
				return "(Log is empty.)";
			}
			return new String(buf, 0, n, StandardCharsets.UTF_8);
		} catch (IOException e) {
			return "Could not read log: " + e.getMessage();
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException ignored) {
				}
			}
		}
	}
}
