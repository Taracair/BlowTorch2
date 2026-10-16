package com.resurrection.blowtorch2.lib.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TimingWatchTest {

	@Test
	public void resumeKeepsTheEnterTime() {
		TimingWatch w = TimingWatch.arm(null, 0L, 0L, 0L, "resume", 1000L, 1100L);
		assertEquals("resume", w.reason);
		assertEquals(1000L, w.fromUp);
		assertEquals(1100L, w.armedUp);
	}

	@Test
	public void launcherTapBeatsALaterRebuild() {
		TimingWatch w = TimingWatch.arm(null, 500L, 0L, 0L, "windows", 0L, 2000L);
		assertEquals("launch", w.reason);
		assertEquals(500L, w.fromUp);
	}

	@Test
	public void switchIsTheStartWhenNothingEarlierIsOpen() {
		TimingWatch w = TimingWatch.arm(null, 0L, 800L, 0L, "windows", 0L, 900L);
		assertEquals("switch", w.reason);
		assertEquals(800L, w.fromUp);
	}

	@Test
	public void resumeThatThenSwitchesKeepsTheResume() {
		TimingWatch prev = new TimingWatch("resume", 1000L, 1100L);
		TimingWatch w = TimingWatch.arm(prev, 0L, 1200L, 0L, "windows", 0L, 1400L);
		assertEquals("resume+switch", w.reason);
		assertEquals(1000L, w.fromUp);
	}

	@Test
	public void resumeThenSwitchWithoutAnEarlierWatchKeepsTheResume() {
		TimingWatch w = TimingWatch.arm(null, 0L, 1200L, 1000L, "windows", 0L, 1400L);
		assertEquals("resume+switch", w.reason);
		assertEquals(1000L, w.fromUp);
	}

	@Test
	public void launchKeepsTheTapWhenAnOlderResumeIsStillFresh() {
		TimingWatch w = TimingWatch.arm(null, 5000L, 0L, 1000L, "windows", 0L, 6000L);
		assertEquals("launch", w.reason);
		assertEquals(5000L, w.fromUp);
	}

	@Test
	public void staleLaunchDoesNotStealALaterResume() {
		TimingWatch w = TimingWatch.arm(null, 500L, 0L, 0L, "resume", 40000L, 41000L);
		assertEquals("resume", w.reason);
		assertEquals(40000L, w.fromUp);
	}

	@Test
	public void stalePreviousWatchDoesNotStealALaterResume() {
		TimingWatch prev = new TimingWatch("switch", 1000L, 1100L);
		TimingWatch w = TimingWatch.arm(prev, 0L, 0L, 0L, "resume", 40000L, 41000L);
		assertEquals("resume", w.reason);
		assertEquals(40000L, w.fromUp);
	}

	@Test
	public void staleSwitchDoesNotStealALaterResume() {
		TimingWatch w = TimingWatch.arm(null, 0L, 1000L, 0L, "resume", 40000L, 41000L);
		assertEquals("resume", w.reason);
		assertEquals(40000L, w.fromUp);
	}
}
