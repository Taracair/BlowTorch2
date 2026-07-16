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

public final class BlowTorchLogger {

	private static final String LOG_DIR = "logs";
	private static final String LOG_FILE = "blowtorch2.log";
	private static final String LOG_BACKUP = "blowtorch2.log.bak";
	private static final long MAX_BYTES = 2 * 1024 * 1024;

	private BlowTorchLogger() {
	}

	public static File getLogFile(Context context) {
		File dir = new File(context.getFilesDir(), LOG_DIR);
		return new File(dir, LOG_FILE);
	}

	public static void ensureLogFile(Context context) {
		File dir = new File(context.getFilesDir(), LOG_DIR);
		if (!dir.exists()) {
			dir.mkdirs();
		}
		File logFile = new File(dir, LOG_FILE);
		if (!logFile.exists()) {
			try {
				logFile.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
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
		logFile.renameTo(backup);
		try {
			logFile.createNewFile();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static synchronized void logError(Context context, String source, String message) {
		if (context == null || message == null) {
			return;
		}
		ensureLogFile(context);
		File logFile = getLogFile(context);
		if (logFile.length() > MAX_BYTES) {
			rotateLog(logFile.getParentFile(), logFile);
		}
		String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
		String plain = stripColors(message).trim();
		String line = stamp + " [" + source + "] " + plain + "\n";
		FileOutputStream out = null;
		try {
			out = new FileOutputStream(logFile, true);
			out.write(line.getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (out != null) {
				try {
					out.close();
				} catch (IOException e) {
					e.printStackTrace();
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
