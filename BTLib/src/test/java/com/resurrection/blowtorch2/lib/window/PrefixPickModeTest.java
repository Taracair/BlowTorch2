package com.resurrection.blowtorch2.lib.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PrefixPickModeTest {

	@Test
	public void emptyPrefixStillArms() {
		PrefixPickMode m = new PrefixPickMode();
		assertTrue(m.armOneshot(""));
		assertEquals(PrefixPickMode.Kind.ONESHOT, m.kind());
		assertNull(m.fire("", "helmet"));
		assertEquals(PrefixPickMode.Kind.ONESHOT, m.kind());
		assertEquals("fix helmet", m.fire("fix ", "helmet"));
	}

	@Test
	public void oneshotFiresOnceThenIdles() {
		PrefixPickMode m = new PrefixPickMode();
		assertTrue(m.armOneshot("fix "));
		assertEquals("fix helmet", m.fire("fix ", "helmet"));
		assertEquals(PrefixPickMode.Kind.IDLE, m.kind());
		assertNull(m.fire("fix ", "boots"));
	}

	@Test
	public void stickyKeepsFiring() {
		PrefixPickMode m = new PrefixPickMode();
		assertTrue(m.armSticky("fix "));
		assertEquals("fix helmet", m.fire("fix ", "helmet"));
		assertEquals("fix boots", m.fire("fix ", "boots"));
		assertEquals(PrefixPickMode.Kind.STICKY, m.kind());
	}

	@Test
	public void stickyReadsTheLivePrefix() {
		PrefixPickMode m = new PrefixPickMode();
		assertTrue(m.armSticky("fix "));
		assertEquals("get helmet", m.fire("get ", "helmet"));
	}

	@Test
	public void emptyPrefixWhileStickyRefusesButStaysArmed() {
		PrefixPickMode m = new PrefixPickMode();
		assertTrue(m.armSticky("fix "));
		assertNull(m.fire("", "helmet"));
		assertEquals(PrefixPickMode.Kind.STICKY, m.kind());
	}

	@Test
	public void idleFireRefuses() {
		assertNull(new PrefixPickMode().fire("fix ", "helmet"));
	}

	@Test
	public void toggleStickyTurnsOffTheSecondTime() {
		PrefixPickMode m = new PrefixPickMode();
		assertTrue(m.toggleSticky("fix "));
		assertTrue(m.isArmed());
		assertFalse(m.toggleSticky("fix "));
		assertFalse(m.isArmed());
	}

	@Test
	public void aMissedWordDoesNotConsumeOneshot() {
		PrefixPickMode m = new PrefixPickMode();
		assertTrue(m.armOneshot("fix "));
		assertNull(m.fire("fix ", null));
		assertEquals(PrefixPickMode.Kind.ONESHOT, m.kind());
		assertEquals("fix helmet", m.fire("fix ", "helmet"));
	}

	@Test
	public void emptyPrefixDoesNotConsumeOneshot() {
		PrefixPickMode m = new PrefixPickMode();
		assertTrue(m.armOneshot("fix "));
		assertNull(m.fire("", "helmet"));
		assertEquals(PrefixPickMode.Kind.ONESHOT, m.kind());
	}

	@Test
	public void toggleStickyEmptyPrefixTurnsOn() {
		PrefixPickMode m = new PrefixPickMode();
		assertTrue(m.toggleSticky(""));
		assertTrue(m.isArmed());
		assertFalse(m.toggleSticky(""));
		assertFalse(m.isArmed());
	}

	@Test
	public void aDotCommandIsNotAGamePrefix() {
		PrefixPickMode m = new PrefixPickMode();
		assertTrue(m.armOneshot(".pick"));
		assertNull(m.fire(".pick", "helmet"));
		assertEquals(PrefixPickMode.Kind.ONESHOT, m.kind());
	}
}
