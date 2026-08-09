package com.resurrection.blowtorch2.lib.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The counting the multi-line trigger decision rests on. If these are wrong the
 * measurement is wrong, and a wrong measurement in a durable place is worse
 * than none.
 */
public class ChunkStatsTest {

	@Test
	public void countsChunksAndCompleteLines() {
		ChunkStats s = new ChunkStats();
		s.record("one\ntwo\nthree\n");
		assertEquals(1, s.chunks());
		assertEquals(3, s.lines());
		assertEquals(0, s.chunksEndingMidLine());
		assertEquals(3, s.longestRun());
	}

	@Test
	public void aTrailingFragmentIsNotACompleteLine() {
		// "three" has no newline yet: the rest of it is in the next chunk, and
		// no ^...$ pattern may rely on it. This is the whole point of the probe.
		ChunkStats s = new ChunkStats();
		s.record("one\ntwo\nthree");
		assertEquals(2, s.lines());
		assertEquals(1, s.chunksEndingMidLine());
	}

	@Test
	public void midLineEndingsAreCountedAcrossChunks() {
		ChunkStats s = new ChunkStats();
		s.record("a\nb");
		s.record("c\nd\n");
		s.record("e");
		assertEquals(3, s.chunks());
		assertEquals(1 + 2 + 0, s.lines());
		assertEquals(2, s.chunksEndingMidLine());
	}

	@Test
	public void longestRunIsTheBestChunkNotTheLast() {
		ChunkStats s = new ChunkStats();
		s.record("a\nb\nc\nd\n");
		s.record("e\n");
		assertEquals(4, s.longestRun());
	}

	@Test
	public void bucketsSplitWhereTheReportSaysTheyDo() {
		ChunkStats s = new ChunkStats();
		s.record("a\n");                     // 1  -> bucket 0
		s.record("a\nb\n");                  // 2  -> bucket 1
		s.record("a\nb\nc\n");               // 3  -> bucket 2
		s.record("a\nb\nc\nd\ne\nf\n");      // 6  -> bucket 3
		StringBuilder eleven = new StringBuilder();
		for (int i = 0; i < 11; i++) {
			eleven.append("x\n");
		}
		s.record(eleven.toString());         // 11 -> bucket 4
		assertEquals(1, s.bucket(0));
		assertEquals(1, s.bucket(1));
		assertEquals(1, s.bucket(2));
		assertEquals(1, s.bucket(3));
		assertEquals(1, s.bucket(4));
	}

	@Test
	public void aChunkWithNoNewlineAtAllCountsAsOneChunkAndNoLines() {
		ChunkStats s = new ChunkStats();
		s.record("a prompt with no newline > ");
		assertEquals(1, s.chunks());
		assertEquals(0, s.lines());
		assertEquals(1, s.chunksEndingMidLine());
		assertEquals(1, s.bucket(0));
	}

	@Test
	public void nullAndEmptyAreIgnoredRatherThanCounted() {
		ChunkStats s = new ChunkStats();
		s.record(null);
		s.record("");
		assertEquals(0, s.chunks());
	}

	@Test
	public void resetClearsEverything() {
		ChunkStats s = new ChunkStats();
		s.record("a\nb\nc");
		s.reset();
		assertEquals(0, s.chunks());
		assertEquals(0, s.lines());
		assertEquals(0, s.chunksEndingMidLine());
		assertEquals(0, s.longestRun());
		assertEquals(0, s.bucket(0));
	}

	@Test
	public void reportSaysSoWhenThereIsNothingToReport() {
		assertTrue(new ChunkStats().report().contains("Nothing recorded yet"));
	}

	@Test
	public void reportCarriesTheNumbersThatDecideTheQuestion() {
		ChunkStats s = new ChunkStats();
		s.record("a\nb\nc\n");
		s.record("d\ne");
		String r = s.report();
		assertTrue(r.contains("Chunks seen:        2"));
		assertTrue(r.contains("Complete lines:     4"));
		assertTrue(r.contains("Longest run:        3"));
		assertTrue(r.contains("1 of 2 (50%)"));
	}
}
