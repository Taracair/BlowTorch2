package com.resurrection.blowtorch2.lib.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure-Java matching for session-log files. No Android types — unit tests run
 * this against lists of strings. Listing directories still lives on
 * {@link SessionLogger} because that needs a {@code Context}.
 *
 * <p>Filename pattern written by {@link SessionLogger}:
 * {@code {sanitizedDisplay}_{yyyy-MM-dd_HH-mm-ss}.txt}. Files are per world.
 */
public final class SessionLogSearch {

	public static final int DEFAULT_DAYS = 7;
	public static final int MAX_DAYS = 3650;
	public static final int MAX_HITS = 500;
	public static final int PREVIEW_CHARS = 120;

	/**
	 * Stamp after the world prefix. Greedy {@code (.+)_} would otherwise treat
	 * {@code foo_bar_2026-01-01_00-00-00.txt} as world {@code foo} when asking
	 * for {@code foo}.
	 */
	private static final Pattern FILE_NAME = Pattern.compile(
			"^(.+)_(\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2})\\.txt$");

	private static final long MS_PER_DAY = 24L * 60L * 60L * 1000L;

	private static final ExecutorService IO = Executors.newSingleThreadExecutor(new ThreadFactory() {
		@Override
		public Thread newThread(Runnable r) {
			Thread t = new Thread(r, "session-log-read");
			t.setDaemon(true);
			t.setPriority(Thread.MIN_PRIORITY);
			return t;
		}
	});

	private SessionLogSearch() {
	}

	/** Same rules {@link SessionLogger} uses when it creates a file. */
	public static String sanitizeProfile(String profile) {
		if (profile == null || profile.trim().isEmpty()) {
			return "session";
		}
		return profile.replaceAll("[^A-Za-z0-9._-]+", "_");
	}

	public static boolean isWorldLogFileName(String name, String display) {
		if (name == null) {
			return false;
		}
		Matcher m = FILE_NAME.matcher(name);
		if (!m.matches()) {
			return false;
		}
		return sanitizeProfile(display).equals(m.group(1));
	}

	/**
	 * {@code true} when {@code lastModified} is strictly before {@code now}
	 * minus {@code days}. {@code days = 0} therefore means “any file whose
	 * mtime is not in the future”, i.e. essentially every file on disk.
	 */
	public static boolean isOlderThanDays(long lastModifiedMs, long nowMs, int days) {
		int d = days < 0 ? 0 : days;
		long cutoff = nowMs - (long) d * MS_PER_DAY;
		return lastModifiedMs < cutoff;
	}

	public static int clampDays(int days) {
		if (days < 0) {
			return 0;
		}
		if (days > MAX_DAYS) {
			return MAX_DAYS;
		}
		return days;
	}

	public static void runIo(Runnable work) {
		if (work != null) {
			IO.execute(work);
		}
	}

	public static final class Hit {
		public final String fileName;
		public final String absolutePath;
		public final int lineIndex;
		public final String preview;

		public Hit(String fileName, String absolutePath, int lineIndex, String preview) {
			this.fileName = fileName == null ? "" : fileName;
			this.absolutePath = absolutePath == null ? "" : absolutePath;
			this.lineIndex = lineIndex;
			this.preview = preview == null ? "" : preview;
		}
	}

	public static String preview(String line) {
		if (line == null) {
			return "";
		}
		String s = line.replace('\t', ' ');
		if (s.length() <= PREVIEW_CHARS) {
			return s;
		}
		return s.substring(0, PREVIEW_CHARS);
	}

	public static boolean lineContains(String line, String query, boolean caseSensitive) {
		if (line == null || query == null || query.length() == 0) {
			return false;
		}
		if (caseSensitive) {
			return line.indexOf(query) >= 0;
		}
		return line.toLowerCase(Locale.US).indexOf(query.toLowerCase(Locale.US)) >= 0;
	}

	/**
	 * Match {@code query} over an in-memory list of lines. Used by unit tests
	 * and by anything that already has the text.
	 */
	public static List<Hit> searchLines(String fileLabel, List<String> lines,
			String query, boolean caseSensitive, int maxHits) {
		ArrayList<Hit> hits = new ArrayList<Hit>();
		if (lines == null || query == null || query.length() == 0 || maxHits <= 0) {
			return hits;
		}
		String label = fileLabel == null ? "" : fileLabel;
		for (int i = 0; i < lines.size() && hits.size() < maxHits; i++) {
			String line = lines.get(i);
			if (lineContains(line, query, caseSensitive)) {
				hits.add(new Hit(label, label, i, preview(line)));
			}
		}
		return hits;
	}

	/**
	 * Stream a file line by line. Does not load the whole file into one String.
	 *
	 * @return number of hits appended
	 */
	public static int searchFile(File file, String query, boolean caseSensitive,
			int maxHits, List<Hit> out) throws IOException {
		if (file == null || !file.isFile() || query == null || query.length() == 0
				|| maxHits <= 0 || out == null) {
			return 0;
		}
		int added = 0;
		BufferedReader reader = new BufferedReader(
				new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8),
				8192);
		try {
			String line;
			int index = 0;
			String name = file.getName();
			String path = file.getAbsolutePath();
			while (added < maxHits && (line = reader.readLine()) != null) {
				if (lineContains(line, query, caseSensitive)) {
					out.add(new Hit(name, path, index, preview(line)));
					added++;
				}
				index++;
			}
		} finally {
			reader.close();
		}
		return added;
	}
}
