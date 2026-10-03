package com.resurrection.blowtorch2.lib.service.sensor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

public class ShakeAxisTest {

	@Test public void dominantAxisBecomesALetter() {
		assertEquals("r", ShakeAxis.letter(10f, 1f, 0f));
		assertEquals("l", ShakeAxis.letter(-10f, 1f, 0f));
		assertEquals("u", ShakeAxis.letter(1f, 10f, 0f));
		assertEquals("d", ShakeAxis.letter(1f, -10f, 0f));
		assertEquals("shakeright", ShakeAxis.gestureId("r"));
		assertNull(ShakeAxis.letter(5f, 5f, 0f));
		assertNull(ShakeAxis.letter(1f, 1f, 20f));
	}

	@Test public void patternCompletesOnTheEnding() {
		ShakeSequence seq = new ShakeSequence();
		assertTrue(seq.push("l", 1000L, Arrays.asList("pat:lr")).isEmpty());
		assertEquals(Arrays.asList("pat:lr"),
				seq.push("r", 1400L, Arrays.asList("pat:lr")));
		assertTrue(seq.push("l", 2000L, Arrays.asList("pat:lr")).isEmpty());
	}

	@Test public void resetDropsAHalfFinishedPattern() {
		ShakeSequence seq = new ShakeSequence();
		seq.push("l", 1000L, Arrays.asList("pat:lr"));
		seq.reset();
		assertTrue(seq.push("r", 1200L, Arrays.asList("pat:lr")).isEmpty());
	}

	@Test public void patternExpiresAfterTheWindow() {
		ShakeSequence seq = new ShakeSequence();
		seq.push("l", 1000L, Arrays.asList("pat:lr"));
		assertTrue(seq.push("r", 1000L + ShakeSequence.WINDOW_MILLIS + 1L,
				Arrays.asList("pat:lr")).isEmpty());
	}

	@Test public void patternIdsAreGestures() {
		assertEquals("pat:lr", GestureCatalog.byId("pat:LR").getId());
		assertEquals("!pat:lr", GestureCatalog.fromPattern("!pat:lr", true).getPattern());
		assertNull(GestureCatalog.byId("pat:lx"));
		assertNull(GestureCatalog.byId("pat:l"));
		assertTrue(GestureCatalog.byId("pat:lrudlrud").getHelp().length() <= 120);
		assertTrue(GestureCatalog.isGesturePattern("!shakeleft", true));
	}
}
