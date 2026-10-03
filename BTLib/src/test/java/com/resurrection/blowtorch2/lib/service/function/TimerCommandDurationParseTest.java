package com.resurrection.blowtorch2.lib.service.function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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
	public void durationPatternMatchesRelativeUnitForms() {
		Matcher add = TimerCommand.DURATION_PATTERN.matcher(" duration heal 50s");
		assertTrue(add.matches());
		assertEquals("50s", add.group(2));
		Matcher sub = TimerCommand.DURATION_PATTERN.matcher(" duration heal -2m silent");
		assertTrue(sub.matches());
		assertEquals("-2m", sub.group(2));
		assertEquals("silent", sub.group(3));
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
	public void relativeFormWinsOverQuery() {
		Matcher set = TimerCommand.DURATION_PATTERN.matcher(" duration heal 15s");
		assertTrue(set.matches());
		assertEquals("15s", set.group(2));
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

	@Test
	public void parseBareSecondsIsAbsoluteSet() {
		TimerCommand.DurationValue v = TimerCommand.parseDurationValue("90");
		assertNotNull(v);
		assertFalse(v.relative);
		assertEquals(90, v.seconds);
	}

	@Test
	public void parseRelativeSecondsMinutesHours() {
		TimerCommand.DurationValue s = TimerCommand.parseDurationValue("50s");
		assertNotNull(s);
		assertTrue(s.relative);
		assertEquals(50, s.seconds);

		TimerCommand.DurationValue m = TimerCommand.parseDurationValue("2m");
		assertNotNull(m);
		assertTrue(m.relative);
		assertEquals(120, m.seconds);

		TimerCommand.DurationValue h = TimerCommand.parseDurationValue("1h");
		assertNotNull(h);
		assertTrue(h.relative);
		assertEquals(3600, h.seconds);
	}

	@Test
	public void parseRelativeSubtract() {
		TimerCommand.DurationValue s = TimerCommand.parseDurationValue("-50s");
		assertNotNull(s);
		assertTrue(s.relative);
		assertEquals(-50, s.seconds);

		TimerCommand.DurationValue m = TimerCommand.parseDurationValue("-2m");
		assertNotNull(m);
		assertTrue(m.relative);
		assertEquals(-120, m.seconds);
	}

	@Test
	public void parseRelativeIsCaseInsensitive() {
		TimerCommand.DurationValue v = TimerCommand.parseDurationValue("2M");
		assertNotNull(v);
		assertTrue(v.relative);
		assertEquals(120, v.seconds);
	}

	@Test
	public void parseRejectsBareNegativeAndJunk() {
		assertNull(TimerCommand.parseDurationValue("-50"));
		assertNull(TimerCommand.parseDurationValue("0"));
		assertNull(TimerCommand.parseDurationValue("0s"));
		assertNull(TimerCommand.parseDurationValue("abc"));
		assertNull(TimerCommand.parseDurationValue(""));
		assertNull(TimerCommand.parseDurationValue(null));
	}

	@Test
	public void workedExampleFiftySecondsAddsToRemaining() {
		// .timer duration … 50s on a timer that had 10s left → 60s left
		TimerCommand.DurationValue v = TimerCommand.parseDurationValue("50s");
		assertNotNull(v);
		assertTrue(v.relative);
		assertEquals(60, com.resurrection.blowtorch2.lib.timer.TimerDuration.adjustRemaining(
				10, v.seconds));
	}
}
