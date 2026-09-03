package com.resurrection.blowtorch2.lib.service;

/**
 * Lines-per-chunk and mid-line endings. A multi-line trigger only works if
 * those lines usually arrive in one TCP chunk. Cheap ints; only when enabled.
 */
public final class ChunkStats {

	/** Buckets for lines-per-chunk: 1, 2, 3-5, 6-10, 11+. */
	private static final int BUCKETS = 5;

	private final int[] lineBuckets = new int[BUCKETS];
	private long chunks;
	private long lines;
	/**
	 * Chunks whose last character is not a newline. This is the number that
	 * decides the question: such a chunk ends mid-line, so the line it started
	 * is completed by the next chunk and no pattern can span that seam.
	 */
	private long chunksEndingMidLine;
	/** The most complete lines ever seen together in one chunk. */
	private int longestRun;

	/**
	 * Record one incoming chunk, exactly as the trigger path sees it.
	 *
	 * @param stripped the chunk with ANSI removed — the same string the
	 *        combined trigger pattern is matched against.
	 */
	public void record(final String stripped) {
		if (stripped == null || stripped.length() == 0) {
			return;
		}
		chunks++;
		int newlines = 0;
		for (int i = 0; i < stripped.length(); i++) {
			if (stripped.charAt(i) == '\n') {
				newlines++;
			}
		}
		boolean endsMidLine = stripped.charAt(stripped.length() - 1) != '\n';
		if (endsMidLine) {
			chunksEndingMidLine++;
		}
		// A "complete line" is one whose newline is inside this chunk. The
		// trailing fragment of a chunk that ends mid-line is not one: a pattern
		// anchored with ^...$ cannot rely on it.
		int complete = newlines;
		lines += complete;
		if (complete > longestRun) {
			longestRun = complete;
		}
		lineBuckets[bucketFor(complete)]++;
	}

	private static int bucketFor(final int completeLines) {
		if (completeLines <= 1) {
			return 0;
		}
		if (completeLines == 2) {
			return 1;
		}
		if (completeLines <= 5) {
			return 2;
		}
		if (completeLines <= 10) {
			return 3;
		}
		return 4;
	}

	public void reset() {
		for (int i = 0; i < BUCKETS; i++) {
			lineBuckets[i] = 0;
		}
		chunks = 0;
		lines = 0;
		chunksEndingMidLine = 0;
		longestRun = 0;
	}

	public long chunks() {
		return chunks;
	}

	public long lines() {
		return lines;
	}

	public long chunksEndingMidLine() {
		return chunksEndingMidLine;
	}

	public int longestRun() {
		return longestRun;
	}

	/** How many chunks fell in a bucket; index as in {@link #bucketFor}. */
	public int bucket(final int index) {
		return lineBuckets[index];
	}

	/**
	 * The reading, as text for the game window. Written to be understood
	 * without this class in front of you, because the person reading it is on a
	 * phone.
	 */
	public String report() {
		StringBuilder s = new StringBuilder();
		s.append("\nChunk probe — how the game's text arrives\n");
		if (chunks == 0) {
			s.append("Nothing recorded yet. Play for a while, then .probe report.\n");
			return s.toString();
		}
		s.append("Chunks seen:        ").append(chunks).append('\n');
		s.append("Complete lines:     ").append(lines).append('\n');
		s.append("Lines per chunk:    1: ").append(lineBuckets[0])
				.append("  2: ").append(lineBuckets[1])
				.append("  3-5: ").append(lineBuckets[2])
				.append("  6-10: ").append(lineBuckets[3])
				.append("  11+: ").append(lineBuckets[4]).append('\n');
		s.append("Longest run:        ").append(longestRun)
				.append(" lines in one chunk\n");
		long pct = chunks == 0 ? 0 : (chunksEndingMidLine * 100) / chunks;
		s.append("Ended mid-line:     ").append(chunksEndingMidLine)
				.append(" of ").append(chunks).append(" (").append(pct)
				.append("%)\n");
		s.append("\nWhat it means: a multi-line trigger can only match lines that\n");
		s.append("share one chunk. A high \"ended mid-line\" percentage, or a\n");
		s.append("longest run of 1-2, means multi-line patterns would miss often\n");
		s.append("and need a line buffer first.\n");
		return s.toString();
	}
}
