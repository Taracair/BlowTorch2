package com.offsetnull.bt.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.content.Context;

import com.offsetnull.bt.service.Colorizer;

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
}
