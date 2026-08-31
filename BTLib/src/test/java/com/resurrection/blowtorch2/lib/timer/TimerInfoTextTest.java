package com.resurrection.blowtorch2.lib.timer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Pins the .timer info / dump wording — no Connection, no toast. */
public class TimerInfoTextTest {

	@Test
	public void runningRepeatShowsElapsedAndRemainingInMinutes() {
		String text = TimerInfoText.describe("heal", 90, 70, true, true);
		assertTrue(text, text.contains("Timer heal"));
		assertTrue(text, text.contains("State: running — repeats when it fires"));
		assertTrue(text, text.contains("Set for: 1m 30s"));
		assertTrue(text, text.contains("Elapsed: 20s"));
		assertTrue(text, text.contains("Remaining: 1m 10s"));
		assertFalse(text, text.contains(" (full)"));
	}

	@Test
	public void pausedOneShotDoesNotCallElapsedTheRemaining() {
		// Old .timer info for a paused timer printed (seconds - remaining) and
		// labelled it "remain" — 10s set, 7s left became "3 remain".
		String text = TimerInfoText.describe("tick", 10, 7, false, false);
		assertTrue(text, text.contains("State: paused — one-shot (stops after it fires)"));
		assertTrue(text, text.contains("Elapsed: 3s"));
		assertTrue(text, text.contains("Remaining: 7s"));
		assertFalse(text, text.contains("3 remain"));
	}

	@Test
	public void stoppedShowsFullDurationAndNoElapsedClock() {
		String text = TimerInfoText.describe("heal", 15, 15, false, true);
		assertTrue(text, text.contains("State: stopped — repeats when it fires"));
		assertTrue(text, text.contains("Elapsed: none (not running)"));
		assertTrue(text, text.contains("Remaining: 15s (full)"));
	}

	@Test
	public void leftoverZeroWhileNotRunningIsStoppedNotPaused() {
		String text = TimerInfoText.describe("heal", 30, 0, false, true);
		assertTrue(text, text.contains("State: stopped"));
		assertTrue(text, text.contains("Remaining: 30s (full)"));
	}

	@Test
	public void describeIsStableEnoughToAssertWholeBlock() {
		String expected = "Timer heal\n"
				+ "  State: running — repeats when it fires\n"
				+ "  Set for: 15s\n"
				+ "  Elapsed: 4s\n"
				+ "  Remaining: 11s";
		assertEquals(expected, TimerInfoText.describe("heal", 15, 11, true, true));
	}
}
