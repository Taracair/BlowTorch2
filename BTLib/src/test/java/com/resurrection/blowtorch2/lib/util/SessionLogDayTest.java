package com.resurrection.blowtorch2.lib.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Calendar;
import java.util.TimeZone;

import org.junit.Test;

public class SessionLogDayTest {

	private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
	private static final TimeZone NYC = TimeZone.getTimeZone("America/New_York");

	private static long utc(int year, int month, int day, int hour, int minute, int second) {
		Calendar c = Calendar.getInstance(UTC);
		c.clear();
		c.set(year, month, day, hour, minute, second);
		c.set(Calendar.MILLISECOND, 0);
		return c.getTimeInMillis();
	}

	@Test
	public void dayKeyAndFileNameAreCalendarDayNotSecondStamp() {
		long now = utc(2026, Calendar.SEPTEMBER, 6, 21, 24, 3);
		assertEquals("2026-09-06", SessionLogDay.dayKey(now, UTC));
		assertEquals("world-a_2026-09-06.txt",
				SessionLogDay.fileName("world-a", "2026-09-06"));
		assertEquals("session_2026-09-06.txt",
				SessionLogDay.fileName(null, "2026-09-06"));
	}

	@Test
	public void sameInstantCanBeDifferentLocalDays() {
		long t = utc(2026, Calendar.SEPTEMBER, 7, 0, 30, 0);
		assertEquals("2026-09-07", SessionLogDay.dayKey(t, UTC));
		assertEquals("2026-09-06", SessionLogDay.dayKey(t, NYC));
		assertTrue(SessionLogDay.sameLocalDay(t, t, NYC));
	}

	@Test
	public void sameLocalDayBreaksAtLocalMidnight() {
		long before = utc(2026, Calendar.SEPTEMBER, 6, 23, 59, 59);
		long after = utc(2026, Calendar.SEPTEMBER, 7, 0, 0, 0);
		assertTrue(SessionLogDay.sameLocalDay(before, before - 60_000L, UTC));
		assertFalse(SessionLogDay.sameLocalDay(before, after, UTC));
		assertTrue(SessionLogDay.needsRollover("2026-09-06", after, UTC));
		assertFalse(SessionLogDay.needsRollover("2026-09-06", before, UTC));
		assertFalse(SessionLogDay.needsRollover(null, after, UTC));
	}

	@Test
	public void dstSpringForwardStaysOneLocalDay() {
		Calendar c = Calendar.getInstance(NYC);
		c.clear();
		c.set(2026, Calendar.MARCH, 8, 1, 30, 0);
		c.set(Calendar.MILLISECOND, 0);
		long beforeSpring = c.getTimeInMillis();
		c.set(2026, Calendar.MARCH, 8, 3, 30, 0);
		long afterSpring = c.getTimeInMillis();
		assertEquals("2026-03-08", SessionLogDay.dayKey(beforeSpring, NYC));
		assertEquals("2026-03-08", SessionLogDay.dayKey(afterSpring, NYC));
		assertTrue(SessionLogDay.sameLocalDay(beforeSpring, afterSpring, NYC));
	}

	@Test
	public void parseNewAndLegacyNames() {
		SessionLogDay.ParsedName day = SessionLogDay.parseFileName(
				"world-a_2026-09-06.txt");
		assertNotNull(day);
		assertEquals("world-a", day.sanitizedWorld);
		assertEquals("2026-09-06", day.dayKey);
		assertNull(day.timeStamp);

		SessionLogDay.ParsedName legacy = SessionLogDay.parseFileName(
				"world-a_2026-09-06_21-24-03.txt");
		assertNotNull(legacy);
		assertEquals("world-a", legacy.sanitizedWorld);
		assertEquals("2026-09-06", legacy.dayKey);
		assertEquals("21-24-03", legacy.timeStamp);

		assertEquals("2026-09-06",
				SessionLogDay.dayKeyFromFileName("world-a_2026-09-06.txt"));
		assertNull(SessionLogDay.parseFileName("notes.txt"));
		assertNull(SessionLogDay.parseFileName("world-a_2026-09-06_p2.txt"));
	}

	@Test
	public void parseDoesNotStealShorterWorldPrefix() {
		SessionLogDay.ParsedName parsed = SessionLogDay.parseFileName(
				"foo_bar_2026-08-20.txt");
		assertNotNull(parsed);
		assertEquals("foo_bar", parsed.sanitizedWorld);
		SessionLogDay.ParsedName legacy = SessionLogDay.parseFileName(
				"foo_bar_2026-08-20_14-03-11.txt");
		assertNotNull(legacy);
		assertEquals("foo_bar", legacy.sanitizedWorld);
	}

	@Test
	public void fileNameStampMsDayFileIsStartOfLocalDay() {
		long start = utc(2026, Calendar.SEPTEMBER, 6, 0, 0, 0);
		assertEquals(Long.valueOf(start),
				SessionLogDay.fileNameStampMs("world-a_2026-09-06.txt", UTC));
		long stamped = utc(2026, Calendar.SEPTEMBER, 6, 21, 24, 3);
		assertEquals(Long.valueOf(stamped),
				SessionLogDay.fileNameStampMs("world-a_2026-09-06_21-24-03.txt", UTC));
		assertNull(SessionLogDay.fileNameStampMs("notes.txt", UTC));
		assertNull(SessionLogDay.fileNameStampMs("world-a_2026-13-40.txt", UTC));
	}

	@Test
	public void markersIncludeClockTime() {
		long now = utc(2026, Calendar.SEPTEMBER, 6, 21, 24, 3);
		assertEquals("\n--- client connected at 21:24:03 ---\n",
				SessionLogDay.connectedMarker(now, UTC));
		assertEquals("\n--- client disconnected at 21:24:03 ---\n",
				SessionLogDay.disconnectedMarker(now, UTC));
		assertEquals("\n--- logging enabled at 21:24:03 ---\n",
				SessionLogDay.loggingEnabledMarker(now, UTC));
		assertEquals("\n--- logging disabled at 21:24:03 ---\n",
				SessionLogDay.loggingDisabledMarker(now, UTC));
		assertEquals("\n--- local day ended at 21:24:03; continuing in the next day's file ---\n",
				SessionLogDay.midnightRolloverMarker(now, UTC));
		assertEquals("\n--- continued from the previous local day at 21:24:03 ---\n",
				SessionLogDay.midnightContinuationMarker(now, UTC));
		assertEquals("\n--- client connected at 21:24:03 → /tmp/world-a_2026-09-06.txt ---\n",
				SessionLogDay.locationMarker(now, UTC, "client connected",
						"/tmp/world-a_2026-09-06.txt"));
		assertEquals("\n--- 21:24:03 MCP sniff ---\n",
				SessionLogDay.timedMarker(now, UTC, "MCP sniff"));
	}

	@Test
	public void headerIsDayNotSecondStamp() {
		assertEquals("=== BlowTorch session log: world-a @ 2026-09-06 ===\n",
				SessionLogDay.headerLine("world-a", "2026-09-06"));
	}

	@Test
	public void newestFirstPutsLiveDayFileAboveSameDayLeftovers() {
		assertTrue(SessionLogDay.compareNamesNewestFirst(
				"world-a_2026-09-06.txt",
				"world-a_2026-09-06_23-45-01.txt") < 0);
		assertTrue(SessionLogDay.compareNamesNewestFirst(
				"world-a_2026-09-07.txt",
				"world-a_2026-09-06.txt") < 0);
		assertTrue(SessionLogDay.compareNamesNewestFirst(
				"world-a_2026-09-06_23-45-01.txt",
				"world-a_2026-09-06_08-00-00.txt") < 0);
		assertEquals(0, SessionLogDay.compareNamesNewestFirst(
				"world-a_2026-09-06.txt",
				"world-a_2026-09-06.txt"));
		assertTrue(SessionLogDay.compareNamesNewestFirst(
				"world-a_2026-09-06.txt", "notes.txt") < 0);
	}

	@Test
	public void markersUseInjectedTimezone() {
		long t = utc(2026, Calendar.SEPTEMBER, 7, 0, 30, 0);
		assertTrue(SessionLogDay.connectedMarker(t, NYC).contains("20:30:00"));
		assertTrue(SessionLogDay.connectedMarker(t, UTC).contains("00:30:00"));
	}
}
