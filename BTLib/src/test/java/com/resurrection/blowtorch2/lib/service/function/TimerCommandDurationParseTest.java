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
}
