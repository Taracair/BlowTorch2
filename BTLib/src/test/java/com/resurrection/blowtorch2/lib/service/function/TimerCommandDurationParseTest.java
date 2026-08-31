package com.resurrection.blowtorch2.lib.service.function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.regex.Matcher;

/** Guards the .timer duration line parser — no Connection needed. */
public class TimerCommandDurationParseTest {

	@Test
	public void durationLineParsesNameAndSeconds() {
		Matcher m = TimerCommand.DURATION_PATTERN.matcher(" duration pagertest 30 ");
		assertTrue(m.matches());
		assertEquals("pagertest", m.group(1));
		assertEquals("30", m.group(2));
		assertTrue(m.group(3) == null || m.group(3).isEmpty());
	}

	@Test
	public void durationIsCaseInsensitive() {
		Matcher m = TimerCommand.DURATION_PATTERN.matcher(" DURATION heal 15 silent");
		assertTrue(m.matches());
		assertEquals("heal", m.group(1));
		assertEquals("15", m.group(2));
		assertEquals("silent", m.group(3));
	}

	@Test
	public void playLineDoesNotMatchDurationPattern() {
		assertFalse(TimerCommand.DURATION_PATTERN.matcher(" play heal ").matches());
	}

	@Test
	public void durationRequiresNumericSeconds() {
		assertFalse(TimerCommand.DURATION_PATTERN.matcher(" duration heal abc").matches());
	}

	@Test
	public void durationWithoutSecondsIsAQueryNotASet() {
		assertFalse(TimerCommand.DURATION_PATTERN.matcher(" duration heal").matches());
		Matcher q = TimerCommand.DURATION_QUERY_PATTERN.matcher(" duration heal");
		assertTrue(q.matches());
		assertEquals("heal", q.group(1));
		assertTrue(q.group(2) == null || q.group(2).isEmpty());
	}

	@Test
	public void durationQueryRejectsANonWindowTail() {
		Matcher q = TimerCommand.DURATION_QUERY_PATTERN.matcher(" duration heal 15s");
		assertTrue(q.matches());
		assertEquals("15s", q.group(2));
		assertFalse(TimerCommand.isWindowToken(q.group(2)));
	}

	@Test
	public void durationQueryAcceptsWindowToken() {
		Matcher q = TimerCommand.DURATION_QUERY_PATTERN.matcher(" duration heal window");
		assertTrue(q.matches());
		assertEquals("heal", q.group(1));
		assertEquals("window", q.group(2));
		assertTrue(TimerCommand.isWindowToken(q.group(2)));
	}

	@Test
	public void durationSetStillWinsWhenSecondsArePresent() {
		Matcher set = TimerCommand.DURATION_PATTERN.matcher(" duration heal 30");
		assertTrue(set.matches());
		Matcher q = TimerCommand.DURATION_QUERY_PATTERN.matcher(" duration heal 30");
		// Query also matches that shape; execute() tries the set pattern first.
		assertTrue(q.matches());
		assertEquals("30", set.group(2));
	}

	@Test
	public void bareInfoDumpListMatch() {
		assertTrue(TimerCommand.BARE_DUMP_PATTERN.matcher(" info").matches());
		assertTrue(TimerCommand.BARE_DUMP_PATTERN.matcher(" dump").matches());
		assertTrue(TimerCommand.BARE_DUMP_PATTERN.matcher(" LIST").matches());
		assertFalse(TimerCommand.BARE_DUMP_PATTERN.matcher(" dump heal").matches());
		assertFalse(TimerCommand.BARE_DUMP_PATTERN.matcher(" play").matches());
	}

	@Test
	public void windowTokenIsOnlyTheWordWindow() {
		assertTrue(TimerCommand.isWindowToken("window"));
		assertTrue(TimerCommand.isWindowToken("WINDOW"));
		assertFalse(TimerCommand.isWindowToken("silent"));
		assertFalse(TimerCommand.isWindowToken(""));
		assertFalse(TimerCommand.isWindowToken(null));
	}
}
