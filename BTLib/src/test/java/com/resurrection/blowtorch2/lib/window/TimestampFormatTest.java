package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Calendar;

import org.junit.Test;

public class TimestampFormatTest {

	private static long at(final int year, final int month, final int day,
			final int hour, final int minute, final int second) {
		Calendar c = Calendar.getInstance();
		c.set(Calendar.MILLISECOND, 0);
		c.set(year, month, day, hour, minute, second);
		return c.getTimeInMillis();
	}

	@Test
	public void defaultIsHourAndMinute() {
		long t = at(2026, Calendar.AUGUST, 18, 14, 32, 7);
		assertEquals("14:32", TimestampFormat.lineLabel(t, TimestampFormat.DEFAULT));
		assertEquals("hour,minute", TimestampFormat.formatParts(TimestampFormat.DEFAULT));
		assertEquals(TimestampFormat.DEFAULT, TimestampFormat.parseParts("hour,minute"));
		assertEquals(TimestampFormat.DEFAULT, TimestampFormat.parseParts(""));
		assertEquals(TimestampFormat.DEFAULT, TimestampFormat.parseParts(null));
	}

	@Test
	public void secondsAppendAfterMinute() {
		long t = at(2026, Calendar.AUGUST, 18, 14, 32, 7);
		int flags = TimestampFormat.HOUR | TimestampFormat.MINUTE | TimestampFormat.SECOND;
		assertEquals("14:32:07", TimestampFormat.lineLabel(t, flags));
	}

	@Test
	public void monthAddsTheDay() {
		long t = at(2026, Calendar.AUGUST, 18, 14, 32, 0);
		int flags = TimestampFormat.MONTH | TimestampFormat.HOUR | TimestampFormat.MINUTE;
		assertEquals("18 Aug 14:32", TimestampFormat.lineLabel(t, flags));
	}

	@Test
	public void yearWithMonth() {
		long t = at(2024, Calendar.JANUARY, 5, 9, 8, 0);
		int flags = TimestampFormat.YEAR | TimestampFormat.MONTH
				| TimestampFormat.HOUR | TimestampFormat.MINUTE;
		assertEquals("5 Jan 2024 09:08", TimestampFormat.lineLabel(t, flags));
	}

	@Test
	public void clampEmptyBecomesDefault() {
		assertEquals(TimestampFormat.DEFAULT, TimestampFormat.clamp(0));
		assertEquals(TimestampFormat.DEFAULT, TimestampFormat.clamp(0x100));
	}

	@Test
	public void toggleRefusesToDropTheLastPart() {
		int onlyHour = TimestampFormat.HOUR;
		assertEquals(onlyHour, TimestampFormat.toggle(onlyHour, TimestampFormat.HOUR));
		int both = TimestampFormat.DEFAULT;
		int withoutHour = TimestampFormat.toggle(both, TimestampFormat.HOUR);
		assertEquals(TimestampFormat.MINUTE, withoutHour);
		assertEquals(both, TimestampFormat.toggle(withoutHour, TimestampFormat.HOUR));
	}

	@Test
	public void withPartOffRefusesEmpty() {
		assertEquals(TimestampFormat.HOUR,
				TimestampFormat.withPart(TimestampFormat.HOUR, TimestampFormat.HOUR, false));
	}

	@Test
	public void prefixEveryLineIncludingATrailingFragment() {
		long t = at(2026, Calendar.AUGUST, 18, 14, 32, 0);
		String out = TimestampFormat.prefixLog("aaa\nbbb\nccc", t, TimestampFormat.DEFAULT);
		assertEquals("[14:32] aaa\n[14:32] bbb\n[14:32] ccc", out);
		assertEquals("", TimestampFormat.prefixLog("", t, TimestampFormat.DEFAULT));
		assertEquals(null, TimestampFormat.prefixLog(null, t, TimestampFormat.DEFAULT));
	}

	@Test
	public void missingEpochYieldsNoLabel() {
		assertEquals("", TimestampFormat.lineLabel(0L, TimestampFormat.DEFAULT));
		assertEquals("", TimestampFormat.logPrefix(-1L, TimestampFormat.DEFAULT));
	}

	@Test
	public void parsePartsAcceptsSpacesAndShortNames() {
		assertEquals(TimestampFormat.HOUR | TimestampFormat.SECOND,
				TimestampFormat.parseParts("hour second"));
		assertEquals(TimestampFormat.MONTH | TimestampFormat.YEAR,
				TimestampFormat.parseParts("mon,yr"));
	}

	@Test
	public void bitForName() {
		assertEquals(TimestampFormat.HOUR, TimestampFormat.bitForName("hour"));
		assertEquals(TimestampFormat.MINUTE, TimestampFormat.bitForName("min"));
		assertEquals(0, TimestampFormat.bitForName("banana"));
		assertFalse(TimestampFormat.has(TimestampFormat.DEFAULT, TimestampFormat.SECOND));
		assertTrue(TimestampFormat.has(TimestampFormat.DEFAULT, TimestampFormat.MINUTE));
	}
}
