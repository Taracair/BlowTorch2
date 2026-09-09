package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TimestampCommandParserTest {

	@Test
	public void emptyIsStatus() {
		assertEquals(TimestampCommandParser.ACTION_STATUS,
				TimestampCommandParser.parse("").action);
		assertEquals(TimestampCommandParser.ACTION_STATUS,
				TimestampCommandParser.parse(null).action);
	}

	@Test
	public void showHideToggle() {
		assertEquals(TimestampCommandParser.ACTION_SHOW,
				TimestampCommandParser.parse("on").action);
		assertEquals(TimestampCommandParser.ACTION_SHOW,
				TimestampCommandParser.parse("show").action);
		assertEquals(TimestampCommandParser.ACTION_HIDE,
				TimestampCommandParser.parse("off").action);
		assertEquals(TimestampCommandParser.ACTION_TOGGLE,
				TimestampCommandParser.parse("toggle").action);
	}

	@Test
	public void logOnOffToggle() {
		TimestampCommandParser.Result on = TimestampCommandParser.parse("log on");
		assertEquals(TimestampCommandParser.ACTION_LOG, on.action);
		assertEquals(Boolean.TRUE, on.logOn);
		TimestampCommandParser.Result off = TimestampCommandParser.parse("log hide");
		assertEquals(Boolean.FALSE, off.logOn);
		TimestampCommandParser.Result tog = TimestampCommandParser.parse("log toggle");
		assertNull(tog.logOn);
		assertTrue(TimestampCommandParser.parse("log").error.contains("Usage:"));
	}

	@Test
	public void hourTogglesByDefault() {
		TimestampCommandParser.Result r = TimestampCommandParser.parse("hour");
		assertEquals(TimestampCommandParser.ACTION_PART, r.action);
		assertEquals(TimestampFormat.HOUR, r.partBit);
		assertNull(r.partOn);
		assertEquals(TimestampFormat.MINUTE,
				TimestampCommandParser.parse("minute off").partBit);
		assertEquals(Boolean.FALSE, TimestampCommandParser.parse("minute off").partOn);
		assertEquals(Boolean.TRUE, TimestampCommandParser.parse("second on").partOn);
	}

	@Test
	public void junkIsUsage() {
		assertTrue(TimestampCommandParser.parse("banana").error.contains("Usage:"));
		assertTrue(TimestampCommandParser.parse("on now").error.contains("Usage:"));
	}
}
