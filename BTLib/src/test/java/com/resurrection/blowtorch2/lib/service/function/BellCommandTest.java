package com.resurrection.blowtorch2.lib.service.function;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BellCommandTest {

	@Test
	public void emptyArgHonorsOptions() {
		assertArrayEquals(new String[0], BellCommand.parseDobellArgs(null));
		assertArrayEquals(new String[0], BellCommand.parseDobellArgs(""));
		assertArrayEquals(new String[0], BellCommand.parseDobellArgs("   "));
		assertNull(BellCommand.forceSpec(BellCommand.parseDobellArgs("")));
	}

	@Test
	public void vibrateDefaultsToShort() {
		assertArrayEquals(new String[] { "vibrate", "short" },
				BellCommand.parseDobellArgs("vibrate"));
		assertArrayEquals(new String[] { "vibrate", "short" },
				BellCommand.parseDobellArgs("  VIBRATE  "));
		assertEquals("vibrate:short",
				BellCommand.forceSpec(BellCommand.parseDobellArgs("vibrate")));
	}

	@Test
	public void vibrateShortLongStrong() {
		assertArrayEquals(new String[] { "vibrate", "short" },
				BellCommand.parseDobellArgs("vibrate short"));
		assertArrayEquals(new String[] { "vibrate", "long" },
				BellCommand.parseDobellArgs("vibrate long"));
		assertArrayEquals(new String[] { "vibrate", "long" },
				BellCommand.parseDobellArgs("VIBRATE LONG"));
		assertArrayEquals(new String[] { "vibrate", "strong" },
				BellCommand.parseDobellArgs("vibrate strong"));
		assertArrayEquals(new String[] { "vibrate", "burst" },
				BellCommand.parseDobellArgs("vibrate burst"));
		assertEquals("vibrate:long",
				BellCommand.forceSpec(BellCommand.parseDobellArgs("vibrate long")));
		assertEquals("vibrate:strong",
				BellCommand.forceSpec(BellCommand.parseDobellArgs("vibrate strong")));
		assertEquals("vibrate:burst",
				BellCommand.forceSpec(BellCommand.parseDobellArgs("vibrate burst")));
	}

	@Test
	public void alert() {
		assertArrayEquals(new String[] { "alert" },
				BellCommand.parseDobellArgs("alert"));
		assertEquals("alert",
				BellCommand.forceSpec(BellCommand.parseDobellArgs("alert")));
	}

	@Test
	public void garbageIsNull() {
		assertNull(BellCommand.parseDobellArgs("garbage"));
		assertNull(BellCommand.parseDobellArgs("vibrate foo"));
		assertNull(BellCommand.parseDobellArgs("vibrate long extra"));
		assertNull(BellCommand.parseDobellArgs("alert extra"));
		assertNull(BellCommand.parseDobellArgs("help"));
	}

	@Test
	public void vibratePatternsMapToDurations() {
		assertEquals(BellCommand.SHORT_MS, BellCommand.vibrateDurationMs("short"));
		assertEquals(BellCommand.LONG_MS, BellCommand.vibrateDurationMs("long"));
		assertEquals(BellCommand.STRONG_MS, BellCommand.vibrateDurationMs("strong"));
		assertEquals(300, BellCommand.SHORT_MS);
		assertEquals(800, BellCommand.LONG_MS);
		assertEquals(BellCommand.BURST_PULSE_MS, BellCommand.vibrateDurationMs("burst"));
		assertTrue(BellCommand.isBurst("burst"));
		assertFalse(BellCommand.isBurst("short"));
		assertEquals(BellCommand.STRONG_AMPLITUDE,
				BellCommand.vibrateAmplitude("short"));
		assertEquals(BellCommand.STRONG_AMPLITUDE,
				BellCommand.vibrateAmplitude("long"));
		assertEquals(BellCommand.STRONG_AMPLITUDE,
				BellCommand.vibrateAmplitude("strong"));
		assertEquals(-1, BellCommand.DEFAULT_AMPLITUDE);
		assertEquals(255, BellCommand.STRONG_AMPLITUDE);
	}
}
