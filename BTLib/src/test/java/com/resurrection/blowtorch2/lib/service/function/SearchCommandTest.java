package com.resurrection.blowtorch2.lib.service.function;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import com.resurrection.blowtorch2.lib.util.SessionLogSearch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SearchCommandTest {

	@Test
	public void stripQuotesRemovesMatchingQuotes() {
		assertEquals("hello world", SearchCommand.stripQuotes("'hello world'"));
		assertEquals("hello world", SearchCommand.stripQuotes("\"hello world\""));
		assertEquals("plain", SearchCommand.stripQuotes("plain"));
		assertEquals("", SearchCommand.stripQuotes(null));
	}

	@Test
	public void argumentFromSlashCommandStripsPrefixKeepsQuotesForExecute() {
		assertEquals("dragon", SearchCommand.argumentFromSlashCommand("/search dragon"));
		assertEquals("'red dragon'", SearchCommand.argumentFromSlashCommand("/search 'red dragon'"));
		assertEquals("\"foo\"", SearchCommand.argumentFromSlashCommand("/SEARCH \"foo\""));
		assertEquals("", SearchCommand.argumentFromSlashCommand(null));
		assertEquals("red dragon", SearchCommand.parse(
				SearchCommand.argumentFromSlashCommand("/search 'red dragon'")).query);
		assertEquals(SearchCommand.Kind.WINDOW, SearchCommand.parse(
				SearchCommand.argumentFromSlashCommand("/search 'logs'")).kind);
	}

	@Test
	public void parseLogsBareOpensViewer() {
		assertEquals(SearchCommand.Kind.OPEN_LOGS, SearchCommand.parse("logs").kind);
		assertEquals(SearchCommand.Kind.OPEN_LOGS, SearchCommand.parse("LOGS").kind);
		assertEquals(SearchCommand.Kind.OPEN_LOGS, SearchCommand.parse("logs 7").kind);
		assertEquals(SearchCommand.Kind.OPEN_LOGS, SearchCommand.parse("logs 7 ''").kind);
	}

	@Test
	public void parseLogsDaysAndPhrase() {
		SearchCommand.Parsed p = SearchCommand.parse("logs 7 goblin");
		assertEquals(SearchCommand.Kind.LOGS, p.kind);
		assertEquals(7, p.days);
		assertEquals("goblin", p.query);
	}

	@Test
	public void parseLogsQuotedPhrase() {
		SearchCommand.Parsed p = SearchCommand.parse("logs 7 'multi word'");
		assertEquals(SearchCommand.Kind.LOGS, p.kind);
		assertEquals(7, p.days);
		assertEquals("multi word", p.query);

		SearchCommand.Parsed q = SearchCommand.parse("logs \"red dragon\"");
		assertEquals(SearchCommand.Kind.LOGS, q.kind);
		assertEquals(SearchCommand.DEFAULT_LOG_DAYS, q.days);
		assertEquals("red dragon", q.query);
	}

	@Test
	public void parseQuotedLogsIsWindowSearch() {
		SearchCommand.Parsed p = SearchCommand.parse("'logs'");
		assertEquals(SearchCommand.Kind.WINDOW, p.kind);
		assertEquals("logs", p.query);
	}

	@Test
	public void parseExistingFormsUnchanged() {
		assertEquals(SearchCommand.Kind.EMPTY, SearchCommand.parse("").kind);
		assertEquals(SearchCommand.Kind.EMPTY, SearchCommand.parse(null).kind);
		assertEquals(SearchCommand.Kind.NEXT, SearchCommand.parse("next").kind);
		assertEquals(SearchCommand.Kind.NEXT, SearchCommand.parse("n").kind);
		assertEquals(SearchCommand.Kind.PREV, SearchCommand.parse("prev").kind);
		assertEquals(SearchCommand.Kind.PREV, SearchCommand.parse("previous").kind);
		assertEquals(SearchCommand.Kind.CLOSE, SearchCommand.parse("close").kind);
		SearchCommand.Parsed w = SearchCommand.parse("goblin");
		assertEquals(SearchCommand.Kind.WINDOW, w.kind);
		assertEquals("goblin", w.query);
		assertEquals("multi word", SearchCommand.parse("'multi word'").query);
	}

	@Test
	public void parseDoesNotTreatTimeAsDays() {
		SearchCommand.Parsed p = SearchCommand.parse("logs 14:32");
		assertEquals(SearchCommand.Kind.LOGS, p.kind);
		assertEquals(SearchCommand.DEFAULT_LOG_DAYS, p.days);
		assertEquals("14:32", p.query);
	}

	@Test
	public void binderRoundTrip() {
		String encoded = SearchCommand.encodeLogsBinder(7, "goblin");
		SearchCommand.Parsed p = SearchCommand.decodeIncomingQuery(encoded);
		assertEquals(SearchCommand.Kind.LOGS, p.kind);
		assertEquals(7, p.days);
		assertEquals("goblin", p.query);
		assertEquals(SearchCommand.Kind.WINDOW,
				SearchCommand.decodeIncomingQuery("goblin").kind);
	}

	@Test
	public void matcherFindsLinesInAList() {
		List<String> lines = Arrays.asList(
				"You see a goblin.",
				"The room is empty.",
				"A goblin king arrives.");
		List<SessionLogSearch.Hit> hits = SessionLogSearch.searchLines(
				"world_2026-01-01_00-00-00.txt", lines, "goblin", false, 10);
		assertEquals(2, hits.size());
		assertEquals(0, hits.get(0).lineIndex);
		assertEquals(2, hits.get(1).lineIndex);
		assertTrue(hits.get(0).preview.contains("goblin"));
		assertEquals(0, SessionLogSearch.searchLines("f", lines, "dragon", false, 10).size());
		assertEquals(0, SessionLogSearch.searchLines("f", lines, "GOBLIN", true, 10).size());
		assertEquals(2, SessionLogSearch.searchLines("f", lines, "GOBLIN", false, 10).size());
	}

	@Test
	public void worldFileNameDoesNotStealPrefix() {
		assertTrue(SessionLogSearch.isWorldLogFileName(
				"foo_2026-08-20_14-03-11.txt", "foo"));
		assertFalse(SessionLogSearch.isWorldLogFileName(
				"foo_bar_2026-08-20_14-03-11.txt", "foo"));
		assertTrue(SessionLogSearch.isWorldLogFileName(
				"foo_bar_2026-08-20_14-03-11.txt", "foo_bar"));
		assertFalse(SessionLogSearch.isWorldLogFileName("notes.txt", "foo"));
		assertEquals("Aardwolf", SessionLogSearch.sanitizeProfile("Aardwolf"));
		assertEquals("a_b", SessionLogSearch.sanitizeProfile("a b"));
		Long stamp = SessionLogSearch.fileNameStampMs("foo_2026-08-20_14-03-11.txt");
		assertNotNull(stamp);
		assertTrue(SessionLogSearch.stampInRange(stamp, null, null));
		assertTrue(SessionLogSearch.stampInRange(stamp,
				Long.valueOf(stamp.longValue() - 1000L),
				Long.valueOf(stamp.longValue() + 1000L)));
		assertFalse(SessionLogSearch.stampInRange(stamp,
				Long.valueOf(stamp.longValue() + 1L), null));
		assertNull(SessionLogSearch.fileNameStampMs("notes.txt"));
	}

	@Test
	public void searchFileStreamsLinesWithoutAndroid() throws Exception {
		java.io.File dir = java.io.File.createTempFile("btlogs", "dir");
		dir.delete();
		dir.mkdirs();
		java.io.File f = new java.io.File(dir, "world_2026-01-01_00-00-00.txt");
		java.io.FileOutputStream out = new java.io.FileOutputStream(f);
		try {
			out.write("alpha\nA goblin.\nomega\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
		} finally {
			out.close();
		}
		java.util.ArrayList<SessionLogSearch.Hit> hits =
				new java.util.ArrayList<SessionLogSearch.Hit>();
		int n = SessionLogSearch.searchFile(f, "goblin", false, 10, hits);
		assertEquals(1, n);
		assertEquals(1, hits.get(0).lineIndex);
		assertEquals("world_2026-01-01_00-00-00.txt", hits.get(0).fileName);
		f.delete();
		dir.delete();
	}

	@Test
	public void olderThanDaysCutoff() {
		long now = 1_000_000_000_000L;
		long day = 24L * 60L * 60L * 1000L;
		assertTrue(SessionLogSearch.isOlderThanDays(now - 8 * day, now, 7));
		assertFalse(SessionLogSearch.isOlderThanDays(now - 3 * day, now, 7));
		assertTrue(SessionLogSearch.isOlderThanDays(now - 1, now, 0));
		assertFalse(SessionLogSearch.isOlderThanDays(now, now, 0));
	}

	@Test
	public void withinLastDaysIncludesTodayAndLiveFile() {
		long now = 1_000_000_000_000L;
		long day = 24L * 60L * 60L * 1000L;
		assertTrue(SessionLogSearch.isWithinLastDays(now, now, 7));
		assertTrue(SessionLogSearch.isWithinLastDays(now - 3 * day, now, 7));
		assertFalse(SessionLogSearch.isWithinLastDays(now - 8 * day, now, 7));
		assertTrue(SessionLogSearch.isWithinLastDays(now - 8 * day, now, 0));
		assertFalse(SessionLogSearch.isWithinLastDays(now + 2 * day, now, 7));
	}
}
