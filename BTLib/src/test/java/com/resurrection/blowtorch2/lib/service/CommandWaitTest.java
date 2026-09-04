package com.resurrection.blowtorch2.lib.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

/** Duration syntax for {@code .wait} / {@code #wait}, pinned before Connection. */
public class CommandWaitTest {

	@Test
	public void secondsUnit() {
		assertEquals(50000L, CommandWait.parseDurationMs("50s"));
	}

	@Test
	public void minutesThenSeconds() {
		assertEquals(310000L, CommandWait.parseDurationMs("5m10s"));
	}

	@Test
	public void hourMinuteSecond() {
		assertEquals(3910000L, CommandWait.parseDurationMs("1h5m10s"));
	}

	@Test
	public void orderDoesNotMatter() {
		assertEquals(CommandWait.parseDurationMs("5m5s"),
				CommandWait.parseDurationMs("5s5m"));
		assertEquals(CommandWait.parseDurationMs("1h5m10s"),
				CommandWait.parseDurationMs("10s1h5m"));
	}

	@Test
	public void spacesBetweenUnits() {
		assertEquals(310000L, CommandWait.parseDurationMs("5m 10s"));
	}

	@Test
	public void millisecondsAndFractionalSeconds() {
		assertEquals(500L, CommandWait.parseDurationMs("500ms"));
		assertEquals(1500L, CommandWait.parseDurationMs("1.5s"));
	}

	@Test
	public void bareNumberIsSeconds() {
		assertEquals(5000L, CommandWait.parseDurationMs("5"));
		assertEquals(1500L, CommandWait.parseDurationMs("1.5"));
	}

	@Test
	public void oneHourIsAllowed() {
		assertEquals(CommandWait.MAX_MS, CommandWait.parseDurationMs("1h"));
		assertSame(CommandWait.Kind.DELAY, CommandWait.parseArgument("1h").kind);
	}

	@Test
	public void overOneHourIsRefused() {
		assertSame(CommandWait.Kind.ERROR, CommandWait.parseArgument("1h1s").kind);
		assertSame(CommandWait.Kind.ERROR, CommandWait.parseArgument("2h").kind);
		assertSame(CommandWait.Kind.ERROR, CommandWait.parseArgument("1h5m10s").kind);
	}

	@Test
	public void zeroIsStop() {
		assertSame(CommandWait.Kind.STOP, CommandWait.parseArgument("0").kind);
		assertSame(CommandWait.Kind.STOP, CommandWait.parseArgument("0s").kind);
		assertSame(CommandWait.Kind.STOP, CommandWait.parseArgument("stop").kind);
	}

	@Test
	public void emptyArgumentIsUsage() {
		assertSame(CommandWait.Kind.ERROR, CommandWait.parseArgument("").kind);
		assertSame(CommandWait.Kind.ERROR, CommandWait.parseSegment(".wait").kind);
	}

	@Test
	public void leftoverTextIsError() {
		assertSame(CommandWait.Kind.ERROR, CommandWait.parseArgument("5x").kind);
		assertSame(CommandWait.Kind.ERROR, CommandWait.parseArgument("wait").kind);
	}

	@Test
	public void hashAndDotAreBothWaits() {
		CommandWait.Result hash = CommandWait.parseSegment("#wait 5s");
		CommandWait.Result dot = CommandWait.parseSegment(".wait 5s");
		assertSame(CommandWait.Kind.DELAY, hash.kind);
		assertSame(CommandWait.Kind.DELAY, dot.kind);
		assertEquals(5000L, hash.delayMs);
		assertEquals(5000L, dot.delayMs);
	}

	@Test
	public void hashRepeatIsNotAWait() {
		assertSame(CommandWait.Kind.NOT_WAIT,
				CommandWait.parseSegment("#5 north").kind);
		assertSame(CommandWait.Kind.NOT_WAIT,
				CommandWait.parseSegment("#help").kind);
		assertSame(CommandWait.Kind.NOT_WAIT,
				CommandWait.parseSegment("north").kind);
	}

	@Test
	public void caseInsensitivePrefix() {
		assertEquals(2000L, CommandWait.parseSegment("#WAIT 2S").delayMs);
		assertEquals(2000L, CommandWait.parseSegment(".Wait 2s").delayMs);
	}

	@Test
	public void formatRoundTripsCommonValues() {
		assertEquals("5s", CommandWait.format(5000L));
		assertEquals("5m10s", CommandWait.format(310000L));
		assertEquals("1h5m10s", CommandWait.format(3910000L));
		assertEquals("500ms", CommandWait.format(500L));
		assertEquals("1s500ms", CommandWait.format(1500L));
	}

	@Test
	public void delayResultHasNoMessage() {
		assertNull(CommandWait.parseSegment("#wait 5s").message);
	}
}
