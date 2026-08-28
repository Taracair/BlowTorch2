package com.resurrection.blowtorch2.lib.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
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
	/** Cap for one Search press in the session-log dialog (not `.search logs`). */
	public static final long SEARCH_BYTE_BUDGET = 16L * 1024L * 1024L;
	/** Packed line indexes for in-file ‹ › after one scan. */
	public static final int MAX_LINE_HITS = 10000;
	/** Opens per list Search, on top of the byte budget. */
	public static final int MAX_FILES_PER_SEARCH = 100;

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
	 * Epoch millis of the {@code yyyy-MM-dd_HH-mm-ss} stamp in the filename,
	 * or null when the name is not a session log.
	 */
	public static Long fileNameStampMs(String name) {
		if (name == null) {
			return null;
		}
		Matcher m = FILE_NAME.matcher(name);
		if (!m.matches()) {
			return null;
		}
		SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss",
				Locale.US);
		fmt.setLenient(false);
		try {
			java.util.Date d = fmt.parse(m.group(2));
			return d == null ? null : Long.valueOf(d.getTime());
		} catch (ParseException e) {
			return null;
		}
	}

	/**
	 * {@code fromMs}/{@code untilMs} null means open on that side. A null
	 * stamp (unparseable name) is included so a renamed file is not dropped.
	 */
	public static boolean stampInRange(Long stampMs, Long fromMsInclusive,
			Long untilMsExclusive) {
		if (stampMs == null) {
			return true;
		}
		long stamp = stampMs.longValue();
		if (fromMsInclusive != null && stamp < fromMsInclusive.longValue()) {
			return false;
		}
		if (untilMsExclusive != null && stamp >= untilMsExclusive.longValue()) {
			return false;
		}
		return true;
	}

	/**
	 * {@code true} when the file is in the last {@code days} (including
	 * today). {@code days = 0} means every file on disk, not “older than”.
	 */
	public static boolean isWithinLastDays(long lastModifiedMs, long nowMs, int days) {
		if (lastModifiedMs > nowMs + 60L * 1000L) {
			return false;
		}
		int d = days < 0 ? 0 : days;
		if (d == 0) {
			return true;
		}
		long cutoff = nowMs - (long) d * MS_PER_DAY;
		return lastModifiedMs >= cutoff;
	}

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

	/** One loaded file that contains the query. List Search stays on the list. */
	public static final class FileMatch {
		public final File file;
		public final int firstLine;
		public final int matchCount;

		public FileMatch(File file, int firstLine, int matchCount) {
			this.file = file;
			this.firstLine = firstLine;
			this.matchCount = matchCount;
		}
	}

	public static final class FilesScan {
		public final ArrayList<FileMatch> matches = new ArrayList<FileMatch>();
		public int totalHits;
		public int filesOpened;
		public int filesLeft;
		public boolean stopped;
	}

	/** Packed 0-based line indexes for ‹ › after one stream. */
	public static final class LineHits {
		public final int[] lines;
		public final int size;
		public final boolean truncated;

		public LineHits(int[] lines, int size, boolean truncated) {
			this.lines = lines == null ? new int[0] : lines;
			this.size = size < 0 ? 0 : size;
			this.truncated = truncated;
		}

		public static LineHits empty() {
			return new LineHits(new int[0], 0, false);
		}
	}

	/** Cooperative cancel for the session-log IO thread. */
	public interface Cancel {
		boolean get();
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
		return containsIgnoreCase(line, query);
	}

	/**
	 * Case-insensitive {@code indexOf} without allocating a lowercased copy
	 * of each line. A 16 MB Search press used to pay two {@code toLowerCase}
	 * strings per line.
	 */
	private static boolean containsIgnoreCase(String hay, String needle) {
		int n = needle.length();
		int max = hay.length() - n;
		for (int i = 0; i <= max; i++) {
			if (hay.regionMatches(true, i, needle, 0, n)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Shared byte cap for one Search press. {@code used} counts UTF-16
	 * units plus a newline per line (close to file bytes on ASCII MUD logs).
	 */
	public static final class Budget {
		public final long limitBytes;
		public long used;
		public boolean stopped;

		public Budget(long limitBytes) {
			this.limitBytes = limitBytes < 0L ? 0L : limitBytes;
		}

		public static Budget ofDefault() {
			return new Budget(SEARCH_BYTE_BUDGET);
		}

		public void consume(long bytes) {
			if (bytes < 0L) {
				return;
			}
			used += bytes;
			if (used >= limitBytes) {
				stopped = true;
			}
		}
	}

	public static String budgetStopMessage(long limitBytes) {
		long mb = limitBytes / (1024L * 1024L);
		if (mb < 1L) {
			mb = 1L;
		}
		return "Stopped after " + mb
				+ " MB. Narrow dates or the name filter.";
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
		return searchFile(file, query, caseSensitive, 0, maxHits, null, out);
	}

	/**
	 * Stream from {@code startLine} (0-based; skip that many lines first).
	 * {@code budget} null means no cap. Stops when {@code budget.stopped} or
	 * {@code maxHits} is reached. Does not load the whole file as one String.
	 *
	 * @return number of hits appended
	 */
	public static int searchFile(File file, String query, boolean caseSensitive,
			int startLine, int maxHits, Budget budget, List<Hit> out)
			throws IOException {
		if (file == null || !file.isFile() || query == null || query.length() == 0
				|| maxHits <= 0 || out == null) {
			return 0;
		}
		if (budget != null && budget.stopped) {
			return 0;
		}
		int from = startLine < 0 ? 0 : startLine;
		int added = 0;
		BufferedReader reader = openReader(file);
		try {
			String line;
			int index = 0;
			String name = file.getName();
			String path = file.getAbsolutePath();
			while (added < maxHits && (line = reader.readLine()) != null) {
				if (index >= from) {
					if (budget != null) {
						budget.consume(line.length() + 1L);
					}
					if (lineContains(line, query, caseSensitive)) {
						out.add(new Hit(name, path, index, preview(line)));
						added++;
					}
				}
				index++;
				if (budget != null && budget.stopped) {
					break;
				}
			}
		} finally {
			reader.close();
		}
		return added;
	}

	/**
	 * First match at or after {@code fromLine}. If none and {@code wrap} and
	 * the scan finished inside the budget, first match from line 0.
	 */
	public static Hit findNextInFile(File file, String query, boolean caseSensitive,
			int fromLine, boolean wrap, Budget budget) throws IOException {
		ArrayList<Hit> out = new ArrayList<Hit>(1);
		int from = fromLine < 0 ? 0 : fromLine;
		searchFile(file, query, caseSensitive, from, 1, budget, out);
		if (out.isEmpty() && wrap && from > 0
				&& (budget == null || !budget.stopped)) {
			searchFile(file, query, caseSensitive, 0, 1, null, out);
		}
		return out.isEmpty() ? null : out.get(0);
	}

	/**
	 * Last match strictly before {@code beforeLine}. If none and {@code wrap}
	 * and the scan finished inside the budget, last match in the file.
	 */
	public static Hit findPreviousInFile(File file, String query,
			boolean caseSensitive, int beforeLine, boolean wrap, Budget budget)
			throws IOException {
		if (file == null || !file.isFile() || query == null || query.length() == 0) {
			return null;
		}
		if (budget != null && budget.stopped) {
			return null;
		}
		Hit lastBefore = null;
		Hit lastAny = null;
		BufferedReader reader = openReader(file);
		try {
			String line;
			int index = 0;
			String name = file.getName();
			String path = file.getAbsolutePath();
			while ((line = reader.readLine()) != null) {
				if (budget != null) {
					budget.consume(line.length() + 1L);
				}
				if (lineContains(line, query, caseSensitive)) {
					Hit hit = new Hit(name, path, index, preview(line));
					lastAny = hit;
					if (index < beforeLine) {
						lastBefore = hit;
					}
				}
				index++;
				if (budget != null && budget.stopped) {
					break;
				}
			}
		} finally {
			reader.close();
		}
		if (lastBefore != null) {
			return lastBefore;
		}
		boolean finished = budget == null || !budget.stopped;
		if (wrap && finished) {
			return lastAny;
		}
		return null;
	}

	/**
	 * Stream {@code files} newest-first. Stops at the byte budget, {@code
	 * maxFilesToOpen}, or {@code maxHits} matching lines. Does not open a file
	 * for the player — the dialog lists {@link FileMatch} rows.
	 */
	public static FilesScan searchFiles(List<File> files, String query,
			boolean caseSensitive, int maxMatchingFiles, int maxHits,
			int maxFilesToOpen, Budget budget, Cancel cancel) throws IOException {
		FilesScan result = new FilesScan();
		if (files == null || query == null || query.length() == 0) {
			return result;
		}
		int matchCap = maxMatchingFiles <= 0 ? MAX_HITS : maxMatchingFiles;
		int hitCap = maxHits <= 0 ? MAX_HITS : maxHits;
		int openCap = maxFilesToOpen <= 0 ? MAX_FILES_PER_SEARCH : maxFilesToOpen;
		for (int i = 0; i < files.size(); i++) {
			if (cancel != null && cancel.get()) {
				return result;
			}
			if (budget != null && budget.stopped) {
				result.stopped = true;
				result.filesLeft = files.size() - i;
				break;
			}
			if (result.filesOpened >= openCap || result.totalHits >= hitCap
					|| result.matches.size() >= matchCap) {
				result.stopped = true;
				result.filesLeft = files.size() - i;
				break;
			}
			File file = files.get(i);
			result.filesOpened++;
			FileMatch one;
			try {
				one = countFileMatches(file, query, caseSensitive,
						hitCap - result.totalHits, budget, cancel);
			} catch (IOException e) {
				continue;
			}
			if (cancel != null && cancel.get()) {
				return result;
			}
			if (one != null && one.matchCount > 0) {
				result.matches.add(one);
				result.totalHits += one.matchCount;
			}
			if (budget != null && budget.stopped) {
				result.stopped = true;
				result.filesLeft = files.size() - i - 1;
				break;
			}
		}
		return result;
	}

	private static FileMatch countFileMatches(File file, String query,
			boolean caseSensitive, int remainingHits, Budget budget, Cancel cancel)
			throws IOException {
		if (file == null || !file.isFile() || remainingHits <= 0) {
			return null;
		}
		int first = -1;
		int count = 0;
		BufferedReader reader = openReader(file);
		try {
			String line;
			int index = 0;
			int sinceCheck = 0;
			while ((line = reader.readLine()) != null) {
				if (budget != null) {
					budget.consume(line.length() + 1L);
				}
				if (lineContains(line, query, caseSensitive)) {
					if (first < 0) {
						first = index;
					}
					count++;
					if (count >= remainingHits) {
						break;
					}
				}
				index++;
				sinceCheck++;
				if (sinceCheck >= 256) {
					sinceCheck = 0;
					if (cancel != null && cancel.get()) {
						break;
					}
				}
				if (budget != null && budget.stopped) {
					break;
				}
			}
		} finally {
			reader.close();
		}
		if (count <= 0 || first < 0) {
			return null;
		}
		return new FileMatch(file, first, count);
	}

	/**
	 * One forward pass. {@code maxLines} caps the packed array (in-file ‹ ›).
	 */
	public static LineHits collectLineHits(File file, String query,
			boolean caseSensitive, int maxLines, Budget budget, Cancel cancel)
			throws IOException {
		if (file == null || !file.isFile() || query == null || query.length() == 0) {
			return LineHits.empty();
		}
		int cap = maxLines <= 0 ? MAX_LINE_HITS : maxLines;
		int[] buf = new int[cap];
		int size = 0;
		boolean truncated = false;
		BufferedReader reader = openReader(file);
		try {
			String line;
			int index = 0;
			int sinceCheck = 0;
			while ((line = reader.readLine()) != null) {
				if (budget != null) {
					budget.consume(line.length() + 1L);
				}
				if (lineContains(line, query, caseSensitive)) {
					if (size < cap) {
						buf[size] = index;
						size++;
					} else {
						truncated = true;
						break;
					}
				}
				index++;
				sinceCheck++;
				if (sinceCheck >= 256) {
					sinceCheck = 0;
					if (cancel != null && cancel.get()) {
						break;
					}
				}
				if (budget != null && budget.stopped) {
					truncated = true;
					break;
				}
			}
		} finally {
			reader.close();
		}
		if (size == 0) {
			return LineHits.empty();
		}
		if (size == buf.length) {
			return new LineHits(buf, size, truncated);
		}
		int[] exact = new int[size];
		System.arraycopy(buf, 0, exact, 0, size);
		return new LineHits(exact, size, truncated);
	}

	/**
	 * Index into {@code lines} of the first match at or after {@code fromLine}.
	 * {@code wrap} uses {@code 0} when none. {@code -1} if empty.
	 */
	public static int nextHitIndex(int[] lines, int size, int fromLine, boolean wrap) {
		if (lines == null || size <= 0) {
			return -1;
		}
		int n = size > lines.length ? lines.length : size;
		for (int i = 0; i < n; i++) {
			if (lines[i] >= fromLine) {
				return i;
			}
		}
		return wrap ? 0 : -1;
	}

	/**
	 * Index into {@code lines} of the last match strictly before {@code beforeLine}.
	 */
	public static int prevHitIndex(int[] lines, int size, int beforeLine, boolean wrap) {
		if (lines == null || size <= 0) {
			return -1;
		}
		int n = size > lines.length ? lines.length : size;
		for (int i = n - 1; i >= 0; i--) {
			if (lines[i] < beforeLine) {
				return i;
			}
		}
		return wrap ? n - 1 : -1;
	}

	private static BufferedReader openReader(File file) throws IOException {
		return new BufferedReader(
				new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8),
				8192);
	}
}
